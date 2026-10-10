package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rocraft.Rocraft;
import com.rocraft.rbx.Dds;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Roblox's ForceField on a character: a translucent blue bubble (forcefield_glow_main: dark centre, brighter rim)
 * facing the camera, two sparkly rings (forcefield_vortex_main) lying flat around the waist and hips and spinning
 * opposite ways, and white sparkles drifting up (GearEffects). Textures come from the user's Roblox install.
 */
final class ForceFieldFx {
	private static Identifier glow, vortex;
	private static boolean tried;

	/** Rings, in the character's Roblox-stud space (HumanoidRootPart at the origin, y up). */
	static void rings(PoseStack ps, SubmitNodeCollector out) {
		if (!textures() || vortex == null) return;
		float t = (System.currentTimeMillis() % 100000) / 1000f;
		// a swirl of faint concentric rings around the waist, ~6 studs across, each turning at its own speed
		ring(ps, out, -0.35f, 3.3f, t * 1.6f);
		ring(ps, out, -0.55f, 2.8f, -t * 2.1f + 1);
		ring(ps, out, -0.75f, 2.3f, t * 2.7f + 2);
	}

	private static void ring(PoseStack ps, SubmitNodeCollector out, float y, float r, float angle) {
		ps.pushPose();
		ps.translate(0, y, 0);
		ps.mulPose(new org.joml.Quaternionf().rotateY(angle));
		out.submitCustomGeometry(ps, RenderTypes.text(vortex), (pose, vc) -> {
			quad(vc, pose, -r, 0, -r, -r, 0, r, r, 0, r, r, 0, -r, 0xFFFFF2F8);
		});
		ps.popPose();
	}

	/** The bubble: a camera-facing disc 8 studs across, centred on the character (centre in camera space). */
	static void bubble(PoseStack ps, SubmitNodeCollector out, Vector3f centre) {
		if (!textures() || glow == null) return;
		float r = 8f / 2 * AvatarLayer.S; // 8 studs across, centred on the torso (measured from Roblox)
		ps.pushPose();
		ps.last().pose().identity().translate(centre).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
		ps.last().normal().identity();
		out.submitCustomGeometry(ps, RenderTypes.text(glow), (pose, vc) -> quad(vc, pose, -r, -r, 0, -r, r, 0, r, r, 0, r, -r, 0, 0xFFFFFFFF));
		ps.popPose();
	}

	/** Both faces, so culling never hides it. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
		float[][] p = {{x0, y0, z0, 0, 0}, {x1, y1, z1, 0, 1}, {x2, y2, z2, 1, 1}, {x3, y3, z3, 1, 0}};
		for (int i = 0; i < 4; i++) vc.addVertex(pose, p[i][0], p[i][1], p[i][2]).setColor(argb).setUv(p[i][3], p[i][4]).setLight(0xF000F0);
		for (int i = 3; i >= 0; i--) vc.addVertex(pose, p[i][0], p[i][1], p[i][2]).setColor(argb).setUv(p[i][3], p[i][4]).setLight(0xF000F0);
	}

	/**
	 * Roblox draws these additively; Minecraft's unlit text shader blends normally, so each texel becomes its own hue
	 * at full strength with the brightness moved into alpha (same look over the scene, without an additive pipeline).
	 */
	private static boolean textures() {
		if (tried) return glow != null || vortex != null;
		tried = true;
		glow = bubbleTexture();
		vortex = load("forcefield_vortex_main", 3.5f);
		return glow != null || vortex != null;
	}

	/**
	 * Measured from Roblox: the bubble is an even light-blue tint (+0, +40, +64 over the scene) with a soft edge
	 * over its outer ~6% and no bright rim. As a normal blend: (127, 196, 255) at 60%.
	 */
	private static Identifier bubbleTexture() {
		int n = 256;
		var ni = new NativeImage(n, n, false);
		for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
			double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1, d = Math.sqrt(dx * dx + dy * dy);
			double edge = Math.clamp((1 - d) / 0.06, 0, 1);
			int a = (int) Math.round(153 * edge * edge * (3 - 2 * edge));
			ni.setPixel(x, y, a << 24 | 127 << 16 | 196 << 8 | 255);
		}
		var id = Rocraft.id("forcefield/bubble");
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft forcefield bubble", ni));
		return id;
	}

	private static Identifier load(String name, float gain) {
		try {
			var f = RobloxAssets.file("textures/particles/" + name + ".dds");
			if (f == null) return null;
			var d = Dds.read(Files.readAllBytes(f));
			var ni = new NativeImage(d.width, d.height, false);
			for (int i = 0; i < d.argb.length; i++) {
				int c = d.argb[i], a = c >>> 24, r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
				int m = Math.max(r, Math.max(g, b));
				// glow: colour lives in RGB (alpha ~0); vortex: white with the shape in alpha
				int alpha = name.contains("glow") ? Math.min(255, Math.round(m / 142f * 255 * gain)) : Math.min(255, Math.round(a * gain));
				int rr = m == 0 ? 255 : r * 255 / m, gg = m == 0 ? 255 : g * 255 / m, bb = m == 0 ? 255 : b * 255 / m;
				if (!name.contains("glow")) { rr = gg = bb = 255; }
				else { // lighter, like Roblox's additive blue over the scene; nothing outside the bubble's edge
					rr = 70; gg = 150; bb = 255; // Roblox's ForceField blue
					double dx = (i % d.width + 0.5) / d.width * 2 - 1, dy = (i / d.width + 0.5) / d.height * 2 - 1;
					alpha = dx * dx + dy * dy > 0.94 * 0.94 ? 0 : Math.max(alpha, 45); // a see-through blue fill, brighter towards the rim
				}
				ni.setPixel(i % d.width, i / d.width, alpha << 24 | rr << 16 | gg << 8 | bb);
			}
			var id = Rocraft.id("forcefield/" + name);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft " + name, ni));
			return id;
		} catch (Exception e) {
			Rocraft.LOGGER.warn("ForceField texture {} unavailable: {}", name, e.toString());
			return null;
		}
	}
}
