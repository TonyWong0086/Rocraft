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
	record Atlas(Identifier id, int texW, int texH, int lineH, int[] u, int[] v, int[] w) {}
	private static final Map<String, Atlas> CACHE = new HashMap<>();
	private static final Map<String, Optional<Font>> BASE = new HashMap<>();

	private static Font base(boolean bold) {
		if (!RocraftConfig.INSTANCE.robloxFont) return null;
		String n = bold ? "SourceSansPro-Bold.ttf" : "SourceSansPro-Regular.ttf";
		return BASE.computeIfAbsent(n, k -> {
			var p = RobloxAssets.file("fonts/" + k);
			try { return Optional.ofNullable(p == null ? null : Font.createFont(Font.TRUETYPE_FONT, p.toFile())); }
			catch (Exception e) { Rocraft.LOGGER.warn("font {} failed: {}", k, e.toString()); return Optional.empty(); }
		}).orElse(null);
	}

	private static Atlas atlas(int px, boolean bold) {
		return CACHE.computeIfAbsent(px + (bold ? "b" : "r"), k -> {
			Font f = base(bold).deriveFont((float) px);
			var probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
			probe.setFont(f);
			var fm = probe.getFontMetrics();
			probe.dispose();
			int lineH = fm.getAscent() + fm.getDescent(), W = 512, x = 0, y = 0;
			int[] u = new int[128], v = new int[128], w = new int[128];
			for (int c = 32; c < 127; c++) {
				int cw = fm.charWidth(c) + 2;
				if (x + cw > W) { x = 0; y += lineH + 2; }
				u[c] = x; v[c] = y; w[c] = fm.charWidth(c); x += cw;
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
			Identifier id = Rocraft.id("rbxfont/" + k);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft font " + k, ni));
			return new Atlas(id, W, H, lineH, u, v, w);
		});
	}

	private static int ch(char c) { return c >= 32 && c < 127 ? c : '?'; }

	/** Width in the current pose's units (the HUD draws in real pixels). */
	static int width(String s, int px, boolean bold) {
		if (base(bold) == null) return Math.round(Minecraft.getInstance().font.width(s) * px / 9f);
		Atlas a = atlas(px, bold);
		int n = 0;
		for (char c : s.toCharArray()) n += a.w[ch(c)];
		return n;
	}

	static void draw(GuiGraphicsExtractor g, String s, int x, int y, int px, boolean bold, int argb) {
		if (base(bold) == null) { // no Roblox install or font off: vanilla font scaled to the same height
			var m = g.pose();
			m.pushMatrix();
			m.translate(x, y);
			m.scale(px / 9f, px / 9f);
			g.text(Minecraft.getInstance().font, s, 0, 0, argb, false);
			m.popMatrix();
			return;
		}
		Atlas a = atlas(px, bold);
		for (char c0 : s.toCharArray()) {
			int c = ch(c0);
			g.blit(RenderPipelines.GUI_TEXTURED, a.id, x, y, a.u[c], a.v[c], a.w[c], a.lineH, a.texW, a.texH, argb);
			x += a.w[c];
		}
	}
}
