package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rocraft.Rocraft;
import com.rocraft.rbx.Dds;
import com.rocraft.tools.RobloxPart;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * A Roblox Part as Roblox draws it: one stud of Roblox's studs.dds per stud on the top surface, inlets on the bottom,
 * smooth sides, all tinted by the BrickColor (the texture is normalised so its flat plastic is white).
 */
final class PartRenderer extends EntityRenderer<RobloxPart, PartRenderer.State> {
	static final class State extends EntityRenderState { int color; float sx, sy, sz; final Quaternionf rot = new Quaternionf(); }
	static final float STUD = 0.28f;
	private static Identifier studs, inlets;
	private static boolean tried;

	PartRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(RobloxPart e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.color = e.color();
		var size = e.size();
		s.sx = size.x(); s.sy = size.y(); s.sz = size.z();
		e.tumble.rotO.slerp(e.tumble.rot, partial, s.rot);
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		textures();
		float hx = s.sx * STUD / 2, hz = s.sz * STUD / 2, h = s.sy * STUD;
		int nx = Math.max(1, Math.round(s.sx)), nz = Math.max(1, Math.round(s.sz)), light = s.lightCoords, c = s.color | 0xFF000000;
		Identifier white = BombRenderer.BALL.texture();
		ps.pushPose(); // tumbling about its centre after a blast
		ps.translate(0, h / 2, 0);
		ps.mulPose(s.rot);
		ps.translate(0, -h / 2, 0);
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(studs != null ? studs : white), (pose, vc) -> {
			for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) { // one stud per cell
				float x0 = -hx + 2 * hx * i / nx, x1 = -hx + 2 * hx * (i + 1) / nx, z0 = -hz + 2 * hz * k / nz, z1 = -hz + 2 * hz * (k + 1) / nz;
				quad(vc, pose, c, light, 0, 1, 0, x0, h, z0, 0, 0, x0, h, z1, 0, 1, x1, h, z1, 1, 1, x1, h, z0, 1, 0);
			}
		});
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(inlets != null ? inlets : white), (pose, vc) -> {
			for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) {
				float x0 = -hx + 2 * hx * i / nx, x1 = -hx + 2 * hx * (i + 1) / nx, z0 = -hz + 2 * hz * k / nz, z1 = -hz + 2 * hz * (k + 1) / nz;
				quad(vc, pose, c, light, 0, -1, 0, x0, 0, z0, 0, 0, x1, 0, z0, 1, 0, x1, 0, z1, 1, 1, x0, 0, z1, 0, 1);
			}
		});
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(white), (pose, vc) -> { // Smooth sides
			quad(vc, pose, c, light, 0, 0, -1, -hx, 0, -hz, 0, 0, -hx, h, -hz, 0, 0, hx, h, -hz, 0, 0, hx, 0, -hz, 0, 0);
			quad(vc, pose, c, light, 0, 0, 1, hx, 0, hz, 0, 0, hx, h, hz, 0, 0, -hx, h, hz, 0, 0, -hx, 0, hz, 0, 0);
			quad(vc, pose, c, light, -1, 0, 0, -hx, 0, hz, 0, 0, -hx, h, hz, 0, 0, -hx, h, -hz, 0, 0, -hx, 0, -hz, 0, 0);
			quad(vc, pose, c, light, 1, 0, 0, hx, 0, -hz, 0, 0, hx, h, -hz, 0, 0, hx, h, hz, 0, 0, hx, 0, hz, 0, 0);
		});
		ps.popPose();
		super.submit(s, ps, out, cam);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int argb, int light, float nx, float ny, float nz, float... v) {
		for (int i = 0; i < 4; i++)
			vc.addVertex(pose, v[i * 5], v[i * 5 + 1], v[i * 5 + 2]).setColor(argb).setUv(v[i * 5 + 3], v[i * 5 + 4])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** Single-stud tiles cut from the install's PlatformContent/pc/textures/studs.dds (64 px per stud). */
	private static void textures() {
		if (tried) return;
		tried = true;
		try {
			var f = RobloxAssets.file("../PlatformContent/pc/textures/studs.dds");
			if (f == null) return;
			var d = Dds.read(Files.readAllBytes(f));
			studs = tile(d, 0, "studs");
			inlets = tile(d, 1024, "inlets");
		} catch (Exception e) { Rocraft.LOGGER.warn("Roblox studs texture unavailable: {}", e.toString()); }
	}

	private static Identifier tile(Dds d, int top, String name) {
		int base = d.argb[(top + 2) * d.width + 2] & 255; // flat plastic between the studs
		var ni = new NativeImage(64, 64, false);
		for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
			int c = d.argb[(top + y) * d.width + x];
			int r = Math.min(255, (c >> 16 & 255) * 255 / Math.max(1, base)), g = Math.min(255, (c >> 8 & 255) * 255 / Math.max(1, base)), b = Math.min(255, (c & 255) * 255 / Math.max(1, base));
			ni.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
		}
		var id = Rocraft.id("part/" + name);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft " + name, ni));
		return id;
	}
}
