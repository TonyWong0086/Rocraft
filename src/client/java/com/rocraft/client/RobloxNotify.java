package com.rocraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;

/**
 * 2018 Roblox notifications (StarterGui:SetCore("SendNotification")): dark rounded cards in the bottom-right corner,
 * newest at the bottom, sliding in from the right, Duration 5 s; optional icon on the left, bold title, text under it.
 * Drawn by Hud2018 in screen pixels.
 */
public final class RobloxNotify {
	static final int W = 260, GAP = 8, SLIDE_MS = 250, DURATION_MS = 5000;
	private record Note(String title, String text, ItemStack icon, long start) {}
	private static final List<Note> NOTES = new ArrayList<>();

	public static void show(String title, String text, ItemStack icon) {
		long now = System.currentTimeMillis();
		// the same message again while it's up (Minecraft unlocks recipes one by one) just keeps it showing
		NOTES.removeIf(n -> n.title.equals(title) && n.text.equals(text));
		NOTES.add(new Note(title, text, icon == null || icon.isEmpty() ? null : icon.copy(), now));
		if (NOTES.size() > 5) NOTES.removeFirst();
	}

	static void draw(GuiGraphicsExtractor g, int W0, int H0) {
		long now = System.currentTimeMillis();
		NOTES.removeIf(n -> now - n.start > DURATION_MS + SLIDE_MS);
		int bottom = H0 - 16;
		var m = g.pose();
		for (int i = NOTES.size() - 1; i >= 0; i--) {
			Note n = NOTES.get(i);
			int tx0 = n.icon != null ? 64 : 12, tw = W - 10 - tx0;
			var lines = n.text.isEmpty() ? List.<String>of() : wrap(n.text, tw, 16);
			int nl = Math.min(2, lines.size());
			// snug card: the same 10 px margin above the title and below the last line, no empty band at the bottom
			int h = Math.max(n.icon != null ? 60 : 0, 10 + 22 + nl * 17 + 10), y = bottom - h;
			bottom = y - GAP;
			long age = now - n.start;
			float in = Math.min(1, age / (float) SLIDE_MS), out = Math.max(0, (age - DURATION_MS) / (float) SLIDE_MS);
			float t = in - out; // 0 = off screen to the right, 1 = in place (Quad ease)
			t = t * (2 - t);
			int x = W0 - Math.round((W + 16) * t); // slides in from the right edge
			card(g, x, y, h);
			if (n.icon != null) {
				m.pushMatrix();
				m.translate(x + 10, y + (h - 44) / 2f);
				m.scale(44 / 16f, 44 / 16f);
				g.item(n.icon, 0, 0);
				m.popMatrix();
			}
			int tx = x + tx0, ty = y + (h - (22 + nl * 17)) / 2;
			RbxFont.draw(g, fit(n.title, tw, 20, true), tx, ty, 20, true, 0xFFFFFFFF);
			for (int l = 0; l < nl; l++) RbxFont.draw(g, lines.get(l), tx, ty + 22 + l * 17, 16, false, 0xFFC8C8C8);
		}
	}

	/** RoundedRect8px.png 9-sliced, Color3(31,31,31) at transparency 0.2. */
	private static void card(GuiGraphicsExtractor g, int x, int y, int H) {
		var t = RobloxAssets.tex("textures/ui/RoundedRect8px.png");
		int c = 0xCC1F1F1F;
		if (t == null) { g.fill(x, y, x + W, y + H, c); return; }
		int k = 8;
		int[] xs = {x, x + k, x + W - k}, ws = {k, W - 2 * k, k}, us = {0, k, 32 - k}, uw = {k, 32 - 2 * k, k};
		int[] ys = {y, y + k, y + H - k}, hs = {k, H - 2 * k, k}, vs = {0, k, 32 - k}, vh = {k, 32 - 2 * k, k};
		for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
			g.blit(RenderPipelines.GUI_TEXTURED, t, xs[i], ys[j], us[i], vs[j], ws[i], hs[j], uw[i], vh[j], 32, 32, c);
	}

	private static List<String> wrap(String s, int w, int px) {
		List<String> out = new ArrayList<>();
		String cur = "";
		for (String word : s.split(" ")) {
			String next = cur.isEmpty() ? word : cur + " " + word;
			if (!cur.isEmpty() && RbxFont.width(next, px, false) > w) { out.add(cur); cur = word; }
			else cur = next;
		}
		out.add(cur);
		return out;
	}

	private static String fit(String s, int w, int px, boolean bold) {
		if (RbxFont.width(s, px, bold) <= w) return s;
		while (s.length() > 1 && RbxFont.width(s + "...", px, bold) > w) s = s.substring(0, s.length() - 1);
		return s + "...";
	}
}
