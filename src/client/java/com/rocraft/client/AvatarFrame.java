package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import com.rocraft.rbx.R6;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The avatar picture in Settings > Avatar: the loaded R6 character (meshes, clothing, accessories, idle pose) drawn
 * by a small software rasterizer into a texture, re-drawn whenever a different profile finishes loading.
 */
final class AvatarFrame {
	static final int W = 240, H = 320;
	private static RobloxProfile drawn;
	private static volatile BufferedImage ready;
	private static volatile boolean busy;
	private static Identifier tex;
	private static DynamicTexture dyn;

	/** Texture of the profile's picture, or null while it is still being drawn. Render thread. */
	static Identifier texture(RobloxProfile p) {
		if (p != drawn && !busy && Rig.body() != null) {
			drawn = p;
			busy = true;
			Thread.startVirtualThread(() -> {
				try { ready = render(p); }
				catch (Exception e) { Rocraft.LOGGER.warn("avatar picture failed: {}", e.toString()); }
				busy = false;
			});
		}
		var img = ready;
		if (img != null) {
			ready = null;
			var ni = new NativeImage(W, H, true);
			for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) ni.setPixel(x, y, img.getRGB(x, y));
			if (dyn != null) dyn.close();
			dyn = new DynamicTexture(() -> "rocraft avatar frame", ni);
			tex = Rocraft.id("avatar_frame");
			Minecraft.getInstance().getTextureManager().register(tex, dyn);
		}
		return busy && img == null && tex == null ? null : tex;
	}

	static boolean busy() { return busy; }

	private record Item(MeshDraw d, BufferedImage tex, int part) {}

	static BufferedImage render(RobloxProfile p) {
		var idle = Animator.anim(Animator.IDLE);
		Matrix4f[] pose = R6.solve(n -> idle == null ? null : idle.sample(n, 0));
		List<Item> items = new ArrayList<>();
		var body = Rig.body();
		BufferedImage skin = AvatarSkin.paint(p);
		for (int i = 0; i < 6; i++) items.add(i == 0 && p.head != null ? new Item(p.head, p.head.image, 0) : new Item(p.bodyParts[i] != null ? p.bodyParts[i] : body[i], skin, i));
		for (var a : p.accessories) items.add(new Item(a.draw(), a.draw().image, a.part()));

		var img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		float[] zb = new float[W * H];
		Arrays.fill(zb, Float.MAX_VALUE);
		float sc = H / 7.8f, yaw = (float) Math.toRadians(-25), cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
		var v = new Vector3f();
		for (Item it : items) {
			var d = it.d();
			var m = pose[it.part()];
			float[][] P = new float[3][3], N = new float[3][3];
			for (int t = 0; t < d.count; t += 3) {
				for (int k = 0; k < 3; k++) {
					int i = t + k;
					m.transformPosition(v.set(d.pos[i * 3], d.pos[i * 3 + 1], d.pos[i * 3 + 2]));
					float rx = v.x * cy - v.z * sy, rz = v.x * sy + v.z * cy; // camera on the character's front (-Z)
					P[k][0] = W / 2f - rx * sc; P[k][1] = H / 2f - (v.y - 0.6f) * sc; P[k][2] = rz;
					m.transformDirection(v.set(d.nrm[i * 3], d.nrm[i * 3 + 1], d.nrm[i * 3 + 2]));
					N[k][0] = v.x * cy - v.z * sy; N[k][1] = v.y; N[k][2] = v.x * sy + v.z * cy;
				}
				raster(img, zb, P, N, d, t, it.tex());
			}
		}
		return img;
	}

	private static void raster(BufferedImage img, float[] zb, float[][] P, float[][] N, MeshDraw d, int t, BufferedImage tex) {
		int x0 = (int) Math.max(0, Math.min(P[0][0], Math.min(P[1][0], P[2][0]))), x1 = (int) Math.min(W - 1, Math.max(P[0][0], Math.max(P[1][0], P[2][0])));
		int y0 = (int) Math.max(0, Math.min(P[0][1], Math.min(P[1][1], P[2][1]))), y1 = (int) Math.min(H - 1, Math.max(P[0][1], Math.max(P[1][1], P[2][1])));
		float den = (P[1][1] - P[2][1]) * (P[0][0] - P[2][0]) + (P[2][0] - P[1][0]) * (P[0][1] - P[2][1]);
		if (Math.abs(den) < 1e-6) return;
		for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) {
			float l0 = ((P[1][1] - P[2][1]) * (px - P[2][0]) + (P[2][0] - P[1][0]) * (py - P[2][1])) / den;
			float l1 = ((P[2][1] - P[0][1]) * (px - P[2][0]) + (P[0][0] - P[2][0]) * (py - P[2][1])) / den, l2 = 1 - l0 - l1;
			if (l0 < 0 || l1 < 0 || l2 < 0) continue;
			float z = l0 * P[0][2] + l1 * P[1][2] + l2 * P[2][2];
			if (z >= zb[py * W + px]) continue;
			int c = -1;
			if (tex != null) {
				float u = l0 * d.uv[t * 2] + l1 * d.uv[t * 2 + 2] + l2 * d.uv[t * 2 + 4], vv = l0 * d.uv[t * 2 + 1] + l1 * d.uv[t * 2 + 3] + l2 * d.uv[t * 2 + 5];
				c = tex.getRGB(Math.floorMod((int) (u * tex.getWidth()), tex.getWidth()), Math.floorMod((int) (vv * tex.getHeight()), tex.getHeight()));
				if ((c >>> 24) < 128) continue;
			}
			// key light from the camera side and a little above, like Roblox's avatar thumbnails
			float nx = l0 * N[0][0] + l1 * N[1][0] + l2 * N[2][0], ny = l0 * N[0][1] + l1 * N[1][1] + l2 * N[2][1], nz = l0 * N[0][2] + l1 * N[1][2] + l2 * N[2][2];
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
			float shade = 0.6f + 0.4f * Math.max(0, (-nz * 0.8f + ny * 0.5f + nx * 0.3f) / len);
			int r = (int) Math.min(255, (c >> 16 & 255) * shade), g = (int) Math.min(255, (c >> 8 & 255) * shade), b = (int) Math.min(255, (c & 255) * shade);
			zb[py * W + px] = z;
			img.setRGB(px, py, 0xFF000000 | r << 16 | g << 8 | b);
		}
	}
}
