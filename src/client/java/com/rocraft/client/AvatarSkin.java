package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Texture for the R6 model in R6Model: body colors, then Pants, then Shirt, folded exactly like Roblox
 * folds a 585x559 clothing template, plus the classic face. Layout must match R6Model's texOffs.
 */
final class AvatarSkin {
	static final int K = 8; // texture px per model unit (512x512 for the 64x64 unit layout)
	private static final Map<RobloxProfile, Identifier> BUILT = new IdentityHashMap<>();

	static Identifier textureId(RobloxProfile p) { return BUILT.computeIfAbsent(p, AvatarSkin::build); }

	/** Skin for a profile (cape/elytra from base kept). Render thread. */
	static PlayerSkin skin(RobloxProfile p, PlayerSkin base) {
		Identifier id = BUILT.computeIfAbsent(p, AvatarSkin::build);
		var tex = new ClientAsset.ResourceTexture(id, id);
		return new PlayerSkin(tex, base == null ? null : base.cape(), base == null ? null : base.elytra(), PlayerModelType.WIDE, true);
	}

	// Template face rects {x, y, w, h}: order right, front, left, back, top, bottom (MC box-unfold names)
	static final int[][] T_TORSO = {{165, 74, 64, 128}, {231, 74, 128, 128}, {361, 74, 64, 128}, {427, 74, 128, 128}, {231, 8, 128, 64}, {231, 204, 128, 64}};
	static final int[][] T_RIGHT = {{19, 355, 64, 128}, {217, 355, 64, 128}, {151, 355, 64, 128}, {85, 355, 64, 128}, {217, 289, 64, 64}, {217, 485, 64, 64}};
	static final int[][] T_LEFT = {{506, 355, 64, 128}, {308, 355, 64, 128}, {374, 355, 64, 128}, {440, 355, 64, 128}, {308, 289, 64, 64}, {308, 485, 64, 64}};

	private static Identifier build(RobloxProfile p) {
		var img = paint(p);
		var ni = new NativeImage(img.getWidth(), img.getHeight(), true);
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
		Identifier id = Rocraft.id("avatar/" + (p.guest ? "guest" : Long.toString(p.userId)) + "_" + System.identityHashCode(p));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft avatar " + p.name, ni));
		return id;
	}

	/** Pure AWT, no game needed (checked offline against a real template). */
	static BufferedImage paint(RobloxProfile p) {
		int[] c = p.colors; // head, torso, leftArm, rightArm, leftLeg, rightLeg
		var img = new BufferedImage(64 * K, 64 * K, BufferedImage.TYPE_INT_ARGB);
		var g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		int[][] boxes = R6Model.BOXES; // {u, v, w, h, d}: head, torso, rightArm, leftArm, rightLeg, leftLeg
		int[] col = {c[0], c[1], c[3], c[2], c[5], c[4]};
		for (int i = 0; i < 6; i++) fill(g, boxes[i], col[i]);
		if (p.pants != null) {
			fold(g, p.pants, T_TORSO, boxes[1]);
			fold(g, p.pants, T_RIGHT, boxes[4]);
			fold(g, p.pants, T_LEFT, boxes[5]);
		}
		if (p.shirt != null) {
			fold(g, p.shirt, T_TORSO, boxes[1]);
			fold(g, p.shirt, T_RIGHT, boxes[2]);
			fold(g, p.shirt, T_LEFT, boxes[3]);
		}
		var face = RobloxAssets.file("textures/face.png");
		if (p.face != null || face != null) try {
			int[] h = boxes[0];
			g.drawImage(p.face != null ? p.face : ImageIO.read(face.toFile()), (h[0] + h[4]) * K, (h[1] + h[4]) * K, h[2] * K, h[3] * K, null);
		} catch (Exception e) { Rocraft.LOGGER.warn("face.png failed: {}", e.toString()); }
		g.dispose();
		return img;
	}

	/** Whole unfolded box area in one color. */
	private static void fill(Graphics2D g, int[] b, int rgb) {
		g.setColor(new java.awt.Color(rgb));
		g.fillRect(b[0] * K, b[1] * K, (2 * b[4] + 2 * b[2]) * K, (b[4] + b[3]) * K);
	}

	/** Paint template faces onto a box's unfolded faces (MC box UV layout). */
	private static void fold(Graphics2D g, BufferedImage t, int[][] r, int[] b) {
		int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
		int[][] dst = {{u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}, {u + d, v, w, d}, {u + d + w, v, w, d}};
		for (int i = 0; i < 6; i++) {
			int[] s = r[i], o = dst[i];
			g.drawImage(t, o[0] * K, o[1] * K, (o[0] + o[2]) * K, (o[1] + o[3]) * K, s[0], s[1], s[0] + s[2], s[1] + s[3], null);
		}
	}
}
