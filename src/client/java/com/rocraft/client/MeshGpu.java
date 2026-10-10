package com.rocraft.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rocraft.Rocraft;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Roblox meshes drawn from GPU-resident vertex buffers: each MeshDraw is uploaded once in part space (position, UV,
 * normal) and every frame only its pose, normal matrix, colour, light and overlay go up, as one small uniform block
 * per draw. Looks the same as the entity-cutout path (core/entity fragment shader, PER_FACE_LIGHTING, lightmap,
 * overlay, fog); drawn in the solid phase of Minecraft's feature renderer, which FeatureDispatcherMixin adds this to.
 */
final class MeshGpu implements FeatureRenderer<MeshGpu.Submit> {
	static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("Rocraft mesh");
	/** Set once the feature renderer is registered (else MeshDraw falls back to writing vertices each frame). */
	static volatile boolean ready;

	static final VertexFormat FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT).addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("Normal", GpuFormat.RGBA8_SNORM).build();
	static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
		.withLocation(Rocraft.id("pipeline/mesh_gpu")).withVertexShader(Rocraft.id("core/mesh")).withFragmentShader("core/entity")
		.withShaderDefine("ALPHA_CUTOUT", 0.1f).withShaderDefine("PER_FACE_LIGHTING")
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
		.withBindGroupLayout(BindGroupLayout.builder().withUniform("RocraftMesh", UniformType.UNIFORM_BUFFER).build())
		.withVertexBinding(0, FORMAT).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withDepthStencilState(DepthStencilState.DEFAULT)
		.withCull(false).build();
	private static final int UBO_SIZE = new com.mojang.blaze3d.buffers.Std140SizeCalculator().putMat4f().putMat4f().putVec4().putIVec4().get();
	private static DynamicUniformStorage<Uniform> uniforms;
	/** Buffers of meshes that were garbage collected, closed on the render thread. */
	private static final ConcurrentLinkedQueue<GpuBuffer> DEAD = new ConcurrentLinkedQueue<>();
	private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();

	/** One mesh draw: camera-space pose and normal matrix, as PoseStack had them. */
	record Submit(Matrix4f pose, Matrix3f normal, MeshDraw mesh, RenderType type, int argb, int light) implements SubmitNode {
		@Override public FeatureRendererType<Submit> featureType() { return TYPE; }
	}

	private record Uniform(Matrix4f pose, Matrix4f normal, int argb, int light) implements DynamicUniformStorage.DynamicUniform {
		@Override public void write(ByteBuffer b) {
			int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
			Std140Builder.intoBuffer(b).putMat4f(pose).putMat4f(normal)
				.putVec4((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f)
				.putIVec4(light & 0xFFFF, light >>> 16, overlay & 0xFFFF, overlay >>> 16);
		}
	}

	private record Draw(PreparedRenderType type, GpuBuffer vertices, int count, GpuBufferSlice uniform) {}

	private final List<List<Draw>> groups = new ArrayList<>();

	@Override
	public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
		if (uniforms == null) uniforms = new DynamicUniformStorage<>("Rocraft mesh UBO", UBO_SIZE, 256);
		var types = new IdentityHashMap<RenderType, PreparedRenderType>();
		var u = new Uniform[submits.size()];
		for (int i = 0; i < u.length; i++) {
			var s = submits.get(i);
			u[i] = new Uniform(s.pose, new Matrix4f(s.normal), s.argb, s.light);
		}
		GpuBufferSlice[] slices = uniforms.writeUniforms(u);
		var draws = new ArrayList<Draw>(u.length);
		for (int i = 0; i < u.length; i++) {
			var s = submits.get(i);
			draws.add(new Draw(types.computeIfAbsent(s.type, RenderType::prepare), buffer(s.mesh), s.mesh.count, slices[i]));
		}
		groups.add(draws);
	}

	@Override
	public void executeGroup(FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
		List<Draw> draws = groups.get(groupIndex);
		if (draws.isEmpty()) return;
		// as PreparedRenderType.drawFromBuffer, but one pass for every mesh
		var first = draws.get(0).type;
		var target = first.outputTarget().getRenderTarget();
		var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		var depth = !target.useDepth ? null : RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Rocraft meshes", color, Optional.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			var sc = first.scissorState();
			if (sc.enabled()) pass.enableScissor(sc.x(), sc.y(), sc.width(), sc.height());
			RenderSystem.bindDefaultUniforms(pass);
			PreparedRenderType bound = null;
			for (var d : draws) {
				if (d.type != bound) {
					bound = d.type;
					pass.setUniform("DynamicTransforms", bound.dynamicTransforms());
					for (var t : bound.textures()) pass.bindTexture(t.name(), t.textureView(), t.sampler());
				}
				pass.setUniform("RocraftMesh", d.uniform);
				pass.setVertexBuffer(0, d.vertices.slice());
				pass.draw(d.count, 1, 0, 0);
			}
		}
	}

	@Override
	public void finishExecute(FeatureFrameContext context) { groups.clear(); }

	/** End of frame (DynamicUniforms.reset): next frame's uniforms go in the ring's next buffer; dropped meshes freed. */
	static void endFrame() {
		if (uniforms != null) uniforms.endFrame();
		for (GpuBuffer b; (b = DEAD.poll()) != null; ) b.close();
	}

	/** The mesh's vertex buffer, uploaded on first use (render thread) and freed once the mesh is garbage collected. */
	private static GpuBuffer buffer(MeshDraw m) {
		if (m.gpu != null) return m.gpu;
		int n = m.count;
		ByteBuffer data = MemoryUtil.memAlloc(n * FORMAT.getVertexSize());
		try {
			for (int i = 0; i < n; i++) {
				data.putFloat(m.pos[i * 3]).putFloat(m.pos[i * 3 + 1]).putFloat(m.pos[i * 3 + 2]);
				data.putFloat(m.uv[i * 2]).putFloat(m.uv[i * 2 + 1]);
				data.put(snorm(m.nrm[i * 3])).put(snorm(m.nrm[i * 3 + 1])).put(snorm(m.nrm[i * 3 + 2])).put((byte) 0);
			}
			data.flip();
			GpuBuffer b = RenderSystem.getDevice().createBuffer(() -> "Rocraft mesh", GpuBuffer.USAGE_VERTEX, data);
			CLEANER.register(m, () -> DEAD.add(b));
			return m.gpu = b;
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static byte snorm(float c) { return (byte) (int) (Math.clamp(c, -1f, 1f) * 127); } // as BufferBuilder
}
