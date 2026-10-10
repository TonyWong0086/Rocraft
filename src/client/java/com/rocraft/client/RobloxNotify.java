package com.rocraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * 2018 Roblox notifications (StarterGui:SetCore("SendNotification"), NotificationScript2): 200 x 64 square-cornered
 * see-through black boxes at the bottom right, newest at the bottom, sliding in from the right (0.35 s, Sine), shown
 * 5 s; optional 48 px icon on the left, bold 18 px white title, 14 px light grey text under it. Drawn by Hud2018 in
 * screen pixels. Look measured from a 2018 screenshot.
 */
public final class RobloxNotify {
	static final int W = 200, H = 64, GAP = 4, MARGIN = 4, SLIDE_MS = 350, DURATION_MS = 5000;
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
		int y = H0 - 16;
		var m = g.pose();
		for (int i = NOTES.size() - 1; i >= 0; i--, y -= GAP) {
			Note n = NOTES.get(i);
			y -= H;
			long age = now - n.start;
			float in = Math.min(1, age / (float) SLIDE_MS), out = Math.max(0, (age - DURATION_MS) / (float) SLIDE_MS);
			float t = in - out; // 0 = off screen to the right, 1 = in place
			t = (float) (1 - Math.cos(Math.PI * t)) / 2; // Sine InOut
			int x = W0 - Math.round((W + MARGIN) * t);
			g.fill(x, y, x + W, y + H, 0x4D000000);
			if (n.icon != null) {
				m.pushMatrix();
				m.translate(x + 8, y + 8);
				m.scale(3, 3); // 16 px item -> 48 px image
				g.item(n.icon, 0, 0);
				m.popMatrix();
			}
			int tx = x + (n.icon != null ? 64 : 8), tw = x + W - 8 - tx;
			int tp = 18; // a long title shrinks to fit (down to the text size) before it's cut short
			while (tp > 14 && RbxFont.width(n.title, tp, true) > tw) tp--;
			RbxFont.draw(g, fit(n.title, tw, tp, true), tx, y + 12 + (18 - tp) / 2, tp, true, 0xFFFFFFFF);
			var lines = n.text.isEmpty() ? List.<String>of() : wrap(n.text, tw, 14);
			for (int l = 0; l < Math.min(2, lines.size()); l++) RbxFont.draw(g, lines.get(l), tx, y + 34 + l * 14, 14, false, 0xFFE6E6E6);
		}
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
