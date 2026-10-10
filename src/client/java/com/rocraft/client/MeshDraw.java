package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import com.rocraft.rbx.RbxMesh;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A Roblox mesh ready to draw: triangles expanded per corner, in Roblox studs (part space), plus its texture. */
final class MeshDraw {
	final float[] pos, nrm, uv;
	final int count; // corners (3 per triangle)
	/** The mesh's coarser Roblox LODs (same texture), drawn further away. */
	MeshDraw[] lods = {};
	BufferedImage image; // source pixels (kept for the Settings avatar picture)
	private Identifier tex;
	private static int seq;

	private MeshDraw(int corners, BufferedImage image) {
		count = corners;
		pos = new float[corners * 3];
		nrm = new float[corners * 3];
		uv = new float[corners * 2];
		this.image = image;
	}

	/** Mesh with its own UVs, vertices moved by m (Roblox CFrame * Offset * Scale). */
	static MeshDraw of(RbxMesh mesh, Matrix4f m, BufferedImage image) {
		var d = of(mesh, mesh.tris, m, image);
		d.lods = java.util.Arrays.stream(mesh.lods).map(t -> of(mesh, t, m, image)).toArray(MeshDraw[]::new);
		return d;
	}

	private static MeshDraw of(RbxMesh mesh, int[] tris, Matrix4f m, BufferedImage image) {
		var d = new MeshDraw(tris.length, image);
		var v = new Vector3f();
		for (int c = 0; c < tris.length; c++) {
			int i = tris[c];
			m.transformPosition(v.set(mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2]));
			d.pos[c * 3] = v.x; d.pos[c * 3 + 1] = v.y; d.pos[c * 3 + 2] = v.z;
			m.transformDirection(v.set(mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2])).normalize();
			d.nrm[c * 3] = v.x; d.nrm[c * 3 + 1] = v.y; d.nrm[c * 3 + 2] = v.z;
			d.uv[c * 2] = mesh.uv[i * 2];
			d.uv[c * 2 + 1] = mesh.uv[i * 2 + 1];
		}
		return d;
	}

	/**
	 * Body-part mesh textured like a Roblox R6 limb: each triangle takes the clothing face it points at
	 * (box[] = {u, v, w, h, d} of the folded avatar texture, 64x64 units, same unfold as AvatarSkin).
	 */
	static MeshDraw planar(RbxMesh mesh, int[] box) {
		var d = new MeshDraw(mesh.tris.length, null);
		float[] b = mesh.bounds();
		float hx = (b[3] - b[0]) / 2, hy = (b[4] - b[1]) / 2, hz = (b[5] - b[2]) / 2;
		int u = box[0], v = box[1], w = box[2], h = box[3], dp = box[4];
		for (int t = 0; t < mesh.tris.length; t += 3) {
			int a = mesh.tris[t], bb = mesh.tris[t + 1], c = mesh.tris[t + 2];
			// triangle normal (Roblox space) picks the face
			float ex = mesh.pos[bb * 3] - mesh.pos[a * 3], ey = mesh.pos[bb * 3 + 1] - mesh.pos[a * 3 + 1], ez = mesh.pos[bb * 3 + 2] - mesh.pos[a * 3 + 2];
			float fx = mesh.pos[c * 3] - mesh.pos[a * 3], fy = mesh.pos[c * 3 + 1] - mesh.pos[a * 3 + 1], fz = mesh.pos[c * 3 + 2] - mesh.pos[a * 3 + 2];
			float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
			float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
			// sign check against the vertex normal so winding order doesn't matter
			float vn = mesh.nrm[a * 3] * nx + mesh.nrm[a * 3 + 1] * ny + mesh.nrm[a * 3 + 2] * nz;
			if (vn < 0) { nx = -nx; ny = -ny; nz = -nz; }
			for (int k = 0; k < 3; k++) {
				int i = mesh.tris[t + k], o = t + k;
				float x = mesh.pos[i * 3], y = mesh.pos[i * 3 + 1], z = mesh.pos[i * 3 + 2];
				float s, tt;
				int[] r; // face rect {x, y, w, h} in units
				if (ax >= ay && ax >= az) {
					if (nx > 0) { r = new int[]{u, v + dp, dp, h}; s = (hz - z) / (2 * hz); }           // character's right
					else { r = new int[]{u + dp + w, v + dp, dp, h}; s = (z + hz) / (2 * hz); }        // left
					tt = (hy - y) / (2 * hy);
				} else if (az >= ay) {
					if (nz < 0) { r = new int[]{u + dp, v + dp, w, h}; s = (hx - x) / (2 * hx); }       // front (-Z)
					else { r = new int[]{u + 2 * dp + w, v + dp, w, h}; s = (x + hx) / (2 * hx); }     // back
					tt = (hy - y) / (2 * hy);
				} else {
					s = (hx - x) / (2 * hx);
					if (ny > 0) { r = new int[]{u + dp, v, w, dp}; tt = (hz - z) / (2 * hz); }          // top
					else { r = new int[]{u + dp + w, v, w, dp}; tt = (z + hz) / (2 * hz); }            // bottom
				}
				s = Math.clamp(s, 0.002f, 0.998f);
				tt = Math.clamp(tt, 0.002f, 0.998f);
				d.pos[o * 3] = x; d.pos[o * 3 + 1] = y; d.pos[o * 3 + 2] = z;
				d.nrm[o * 3] = mesh.nrm[i * 3]; d.nrm[o * 3 + 1] = mesh.nrm[i * 3 + 1]; d.nrm[o * 3 + 2] = mesh.nrm[i * 3 + 2];
				d.uv[o * 2] = (r[0] + s * r[2]) / 64f;
				d.uv[o * 2 + 1] = (r[1] + tt * r[3]) / 64f;
			}
		}
		return d;
	}

	/** A Roblox Ball part (Shape = Ball) of the given diameter in studs; white, tinted when drawn. */
	static MeshDraw ball(float size) {
		int seg = 16, ring = 12;
		var d = new MeshDraw(seg * ring * 6, null);
		int c = 0;
		for (int r = 0; r < ring; r++) for (int s = 0; s < seg; s++) {
			float[][] q = {pt(r, s, ring, seg), pt(r + 1, s, ring, seg), pt(r + 1, s + 1, ring, seg), pt(r, s + 1, ring, seg)};
			for (int k : new int[]{0, 1, 2, 0, 2, 3}) {
				for (int j = 0; j < 3; j++) { d.pos[c * 3 + j] = q[k][j] * size / 2; d.nrm[c * 3 + j] = q[k][j]; }
				d.uv[c * 2] = 0.5f;
				d.uv[c * 2 + 1] = 0.5f;
				c++;
			}
		}
		return d;
	}

	private static float[] pt(int r, int s, int ring, int seg) {
		double th = Math.PI * r / ring, ph = 2 * Math.PI * s / seg;
		return new float[]{(float) (Math.sin(th) * Math.cos(ph)), (float) Math.cos(th), (float) (Math.sin(th) * Math.sin(ph))};
	}

	/** Copy of src with vertices moved by m and a new texture. */
	static MeshDraw transformed(MeshDraw src, Matrix4f m, BufferedImage image) {
		var d = new MeshDraw(src.count, image);
		var v = new Vector3f();
		for (int c = 0; c < src.count; c++) {
			m.transformPosition(v.set(src.pos[c * 3], src.pos[c * 3 + 1], src.pos[c * 3 + 2]));
			d.pos[c * 3] = v.x; d.pos[c * 3 + 1] = v.y; d.pos[c * 3 + 2] = v.z;
			m.transformDirection(v.set(src.nrm[c * 3], src.nrm[c * 3 + 1], src.nrm[c * 3 + 2])).normalize();
			d.nrm[c * 3] = v.x; d.nrm[c * 3 + 1] = v.y; d.nrm[c * 3 + 2] = v.z;
		}
		System.arraycopy(src.uv, 0, d.uv, 0, src.uv.length);
		d.lods = java.util.Arrays.stream(src.lods).map(l -> transformed(l, m, null)).toArray(MeshDraw[]::new);
		return d;
	}

	/** entityCutout, but drawn as triangles: Roblox meshes are triangles, so no fourth (repeated) corner per face. */
	private static final com.mojang.blaze3d.pipeline.RenderPipeline TRIANGLES = com.mojang.blaze3d.pipeline.RenderPipeline
		.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_SNIPPET).withLocation(Rocraft.id("pipeline/mesh_cutout"))
		.withShaderDefine("ALPHA_CUTOUT", 0.1f).withShaderDefine("PER_FACE_LIGHTING")
		.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1).withCull(false)
		.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES).build();
	private static final java.util.Map<Identifier, net.minecraft.client.renderer.rendertype.RenderType> TYPES = new java.util.HashMap<>();

	static net.minecraft.client.renderer.rendertype.RenderType type(Identifier tex) {
		return TYPES.computeIfAbsent(tex, t -> net.minecraft.client.renderer.rendertype.RenderType.create("rocraft_mesh",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(TRIANGLES).withTexture("Sampler0", t).useLightmap().useOverlay()
				.affectsCrumbling().setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
	}

	/** Submit as entity geometry. */
	static void submit(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, int light, MeshDraw d, Identifier tex) {
		submit(ps, out, light, d, tex, -1);
	}

	static void submit(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, int light, MeshDraw d, Identifier tex, int argb) {
		var at = ps.last().pose();
		// distance from the camera in the mesh's own units (studs), so a scaled-up GUI preview stays detailed
		MeshDraw m = d.lod((at.m30() * at.m30() + at.m31() * at.m31() + at.m32() * at.m32()) / (at.m00() * at.m00() + at.m01() * at.m01() + at.m02() * at.m02()));
		out.submitCustomGeometry(ps, type(tex), (pose, vc) -> {
			float[] p = m.pos, n = m.nrm, uv = m.uv;
			Matrix4f mp = pose.pose();
			org.joml.Matrix3f mn = pose.normal();
			int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
			var v = new Vector3f();
			var w = new Vector3f();
			for (int i = 0; i < m.count; i++) {
				mp.transformPosition(v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]));
				mn.transform(w.set(n[i * 3], n[i * 3 + 1], n[i * 3 + 2])).normalize();
				// one call per vertex: BufferBuilder writes the whole vertex at once
				vc.addVertex(v.x, v.y, v.z, argb, uv[i * 2], uv[i * 2 + 1], overlay, light, w.x, w.y, w.z);
			}
		});
	}

	/** This mesh or a coarser Roblox LOD for a squared distance in studs: LOD 1 past 24 studs (9 blocks), the coarsest past 48. */
	MeshDraw lod(float d2) {
		if (lods.length == 0 || d2 < 24 * 24) return this;
		return d2 < 48 * 48 ? lods[0] : lods[lods.length - 1];
	}

	/** Own texture, uploaded on first use (render thread). White if the asset had none. */
	Identifier texture() {
		if (tex == null) {
			BufferedImage img = image != null ? image : new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) {{ setRGB(0, 0, -1); }};
			var ni = new NativeImage(img.getWidth(), img.getHeight(), true);
			for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
			tex = Rocraft.id("mesh/" + (seq++));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "rocraft mesh", ni));
		}
		return tex;
	}
}
