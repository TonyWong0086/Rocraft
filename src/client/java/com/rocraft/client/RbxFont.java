package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import com.rocraft.RocraftConfig;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** SourceSansPro (the 2018 Roblox UI font) from the user's Roblox install, baked per pixel size into a glyph atlas. */
final class RbxFont {
	/** w: whole-pixel glyph cell width; adv: the font's exact (fractional) advance, for world text laid out like Roblox's. */
	record Atlas(Identifier id, int texW, int texH, int lineH, int[] u, int[] v, int[] w, float[] adv) {}
	private static final Map<String, Atlas> CACHE = new HashMap<>();
	private static final Map<String, Optional<Font>> BASE = new HashMap<>();

	/** Roblox's Legacy font (humanoid name displays): Arial. */
	static final String LEGACY = "arial";

	private static Font base(boolean bold) { return base(bold ? "SourceSansPro-Bold.ttf" : "SourceSansPro-Regular.ttf"); }

	private static Font base(String n) {
		if (!RocraftConfig.INSTANCE.robloxFont) return null;
		if (n.equals(LEGACY)) n = "C:/Windows/Fonts/arial.ttf";
		return BASE.computeIfAbsent(n, k -> {
			var p = k.contains("/") ? (java.nio.file.Files.exists(java.nio.file.Path.of(k)) ? java.nio.file.Path.of(k) : RobloxAssets.file("fonts/SourceSansPro-Regular.ttf"))
				: RobloxAssets.file("fonts/" + k);
			try { return Optional.ofNullable(p == null ? null : Font.createFont(Font.TRUETYPE_FONT, p.toFile())); }
			catch (Exception e) { Rocraft.LOGGER.warn("font {} failed: {}", k, e.toString()); return Optional.empty(); }
		}).orElse(null);
	}

	private static Atlas atlas(float px, boolean bold) { return atlas(px, bold ? "b" : "r"); }

	private static Atlas atlas(float px, String font) {
		return CACHE.computeIfAbsent(px + font, k -> {
			Font f = (font.equals("b") ? base(true) : font.equals("r") ? base(false) : base(font)).deriveFont(px);
			var probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
			probe.setFont(f);
			var fm = probe.getFontMetrics();
			probe.dispose();
			int lineH = fm.getAscent() + fm.getDescent(), W = 512, x = 0, y = 0;
			int[] u = new int[128], v = new int[128], w = new int[128];
			float[] adv = new float[128];
			var frc = new java.awt.font.FontRenderContext(null, true, true);
			for (int c = 32; c < 127; c++) {
				int cw = fm.charWidth(c) + 2;
				if (x + cw > W) { x = 0; y += lineH + 2; }
				u[c] = x; v[c] = y; w[c] = fm.charWidth(c); x += cw;
				adv[c] = (float) f.getStringBounds(String.valueOf((char) c), frc).getWidth();
			}
			int H = y + lineH + 2;
			var img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
			var g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setFont(f);
			g.setColor(Color.WHITE);
			for (int c = 32; c < 127; c++) g.drawString(String.valueOf((char) c), u[c], v[c] + fm.getAscent());
			g.dispose();
			var ni = new NativeImage(W, H, false);
			for (int py = 0; py < H; py++) for (int pxl = 0; pxl < W; pxl++) ni.setPixel(pxl, py, img.getRGB(pxl, py));
			Identifier id = Rocraft.id("rbxfont/" + k.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft font " + k, ni));
			return new Atlas(id, W, H, lineH, u, v, w, adv);
		});
	}

	private static int ch(char c) { return c >= 32 && c < 127 ? c : '?'; }

	/** Width in the current pose's units (the HUD draws in real pixels). */
	static int width(String s, int px, boolean bold) { return width(s, px, bold ? "b" : "r"); }

	/** Width of s in a font ("b", "r" or LEGACY). */
	static int width(String s, int px, String font) {
		if (font(font) == null) return Math.round(Minecraft.getInstance().font.width(s) * px / 9f);
		Atlas a = atlas(px, font);
		int n = 0;
		for (char c : s.toCharArray()) n += a.w[ch(c)];
		return n;
	}

	static void draw(GuiGraphicsExtractor g, String s, int x, int y, int px, boolean bold, int argb) { draw(g, s, x, y, px, bold ? "b" : "r", argb); }

	private static Font font(String font) { return font.equals("b") ? base(true) : font.equals("r") ? base(false) : base(font); }

	/** HUD text in a font ("b", "r" or LEGACY). */
	static void draw(GuiGraphicsExtractor g, String s, int x, int y, int px, String font, int argb) {
		if (font(font) == null) { // no Roblox install or font off: vanilla font scaled to the same height
			var m = g.pose();
			m.pushMatrix();
			m.translate(x, y);
			m.scale(px / 9f, px / 9f);
			g.text(Minecraft.getInstance().font, s, 0, 0, argb, false);
			m.popMatrix();
			return;
		}
		Atlas a = atlas(px, font);
		for (char c0 : s.toCharArray()) {
			int c = ch(c0);
			g.blit(RenderPipelines.GUI_TEXTURED, a.id, x, y, a.u[c], a.v[c], a.w[c], a.lineH, a.texW, a.texH, argb);
			x += a.w[c];
		}
	}

	/**
	 * Text in the world (name above a head): glyph quads in the current pose, 1 unit = 1 font pixel, y down,
	 * unlit. Falls back to Minecraft's font when the Roblox font is off.
	 */
	static void world(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, String s, float x, float y, int px, boolean bold, int argb) {
		world(ps, out, s, x, y, px, bold ? "b" : "r", argb, 0);
	}

	/** Height of a line of text (the glyph boxes world() draws), in its pixels. */
	static int lineHeight(float px, String font) {
		if ((font.equals("b") ? base(true) : font.equals("r") ? base(false) : base(font)) == null) return Math.round(px);
		return atlas(px, font).lineH();
	}

	/** Width of s in a world font ("b", "r" or LEGACY). */
	static int worldWidth(String s, float px, String font) {
		if ((font.equals("b") ? base(true) : font.equals("r") ? base(false) : base(font)) == null) return Math.round(Minecraft.getInstance().font.width(s) * px / 9f);
		Atlas a = atlas(px, font);
		float n = 0;
		for (char c : s.toCharArray()) n += a.adv[ch(c)];
		return Math.round(n);
	}

	/** outline: ARGB of a 1-pixel stroke all round (Roblox TextStroke), 0 for none. */
	static void world(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, String s, float x, float y, float px, String font, int argb, int outline) {
		if ((font.equals("b") ? base(true) : font.equals("r") ? base(false) : base(font)) == null) {
			ps.pushPose();
			ps.translate(x, y, 0);
			ps.scale(px / 9f, px / 9f, 1);
			out.submitText(ps, 0, 0, net.minecraft.network.chat.Component.literal(s).getVisualOrderText(), false,
				net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0xF000F0, argb, 0, 0);
			ps.popPose();
			return;
		}
		Atlas a = atlas(px, font);
		// Minecraft's unlit text shader (no shading, so white stays white); outline first, then the text, in one batch
		out.submitCustomGeometry(ps, net.minecraft.client.renderer.rendertype.RenderTypes.text(a.id), (pose, vc) -> {
			if (outline != 0) for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}})
				glyphs(vc, pose, a, s, x + o[0], y + o[1], outline);
			if (argb >>> 24 != 0) glyphs(vc, pose, a, s, x, y, argb);
		});
	}

	private static void glyphs(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Atlas a, String s, float x, float y, int argb) {
		float pen = x;
		for (char c0 : s.toCharArray()) {
			int c = ch(c0);
			float cx = Math.round(pen); // each glyph on whole pixels (its cell was rasterised there), pen kept fractional
			float u0 = a.u[c] / (float) a.texW, u1 = (a.u[c] + a.w[c]) / (float) a.texW, v0 = a.v[c] / (float) a.texH, v1 = (a.v[c] + a.lineH) / (float) a.texH;
			float[][] q = {{cx, y, u0, v0}, {cx, y + a.lineH, u0, v1}, {cx + a.w[c], y + a.lineH, u1, v1}, {cx + a.w[c], y, u1, v0}};
			for (float[] k : q) vc.addVertex(pose, k[0], k[1], 0).setColor(argb).setUv(k[2], k[3]).setLight(0xF000F0);
			pen += a.adv[c];
		}
	}
}
