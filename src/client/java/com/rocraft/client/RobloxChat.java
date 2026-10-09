package com.rocraft.client;

import com.rocraft.client.mixin.ChatComponentAccessor;
import com.rocraft.client.mixin.ChatScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * The 2018 Roblox chat window (Lua chat defaults): top-left under the top bar, 30% x 25% of the screen,
 * "[Name]: message" in SourceSansBold 18 with a light text stroke, names coloured by Roblox's name-colour hash,
 * text fading 30 s after it arrives, and the chat bar while typing. Drawn in screen pixels by Hud2018.
 */
final class RobloxChat {
	static final int TEXT = 18, BAR_H = 44, PAD = 8;
	static final Pattern PLAYER = Pattern.compile("^<([^>]{1,40})> (.*)$", Pattern.DOTALL);
	/** NAME_COLORS from the chat's ExtraDataInitializer. */
	static final int[] NAME_COLORS = {0xFD2943, 0x01A2FF, 0x02B857, 0x6B327C, 0xDA8541, 0xF5CD30, 0xE8BAC8, 0xD7C59A};

	/** GetNameValue: char bytes, negated for every other pair counted from the end. */
	static int nameColor(String name) {
		int value = 0, n = name.length();
		for (int i = 1; i <= n; i++) {
			int c = name.charAt(i - 1), rev = n - i + 1;
			if (n % 2 == 1) rev--;
			if (rev % 4 >= 2) c = -c;
			value += c;
		}
		return NAME_COLORS[Math.floorMod(value, NAME_COLORS.length)];
	}

	static void draw(GuiGraphicsExtractor g, Minecraft mc, int W, int H, int top) {
		boolean open = mc.gui.screen() instanceof ChatScreen;
		int x0 = 0, y0 = top + 2, w = Math.max(380, W * 30 / 100), h = Math.max(200, H * 25 / 100);
		int bottom = y0 + h - (open ? BAR_H : 0);
		if (open) g.fill(x0, y0, x0 + w, y0 + h, 0x66000000); // BackgroundTransparency 0.6

		// wrap messages into lines, newest at the bottom
		var hud = mc.gui.hud;
		int now = hud.getGuiTicks();
		var all = ((ChatComponentAccessor) hud.getChat()).rocraft$allMessages(); // newest first
		int y = bottom - PAD;
		for (var msg : all) {
			int age = now - msg.addedTime();
			float alpha = open ? 1 : age < 600 ? 1 : Math.max(0, 1 - (age - 600) / 20f); // ChatWindowTextFadeOutTime 30
			if (alpha <= 0) { if (!open) break; else continue; }
			var lines = layout(msg.content().getString(), w - 2 * PAD);
			for (int i = lines.size() - 1; i >= 0; i--) {
				y -= TEXT + 2;
				if (y < y0 + 4) return;
				var l = lines.get(i);
				int a = (int) (alpha * 255) << 24;
				int x = x0 + PAD;
				if (l.name != null) { stroke(g, l.name, x, y, a | l.nameColor); x += RbxFont.width(l.name, TEXT, true); }
				stroke(g, l.text, x, y, a | 0xFFFFFF);
			}
			if (open) continue;
		}
		if (open) bar(g, mc, x0, y0 + h - BAR_H, w);
	}

	/** Chat bar: light box with the typed text and caret, or the classic hint. */
	private static void bar(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
		g.fill(x + 6, y + 6, x + w - 6, y + BAR_H - 6, 0x99FFFFFF); // ChatBarBoxColor white, transparency 0.4
		var input = ((ChatScreenAccessor) mc.gui.screen()).rocraft$input();
		String text = input.getValue();
		int tx = x + 14, ty = y + (BAR_H - 24) / 2;
		if (text.isEmpty()) RbxFont.draw(g, "To chat click here or press \"/\" key", tx, ty, TEXT, false, 0xFF6E6E6E);
		else RbxFont.draw(g, text, tx, ty, TEXT, false, 0xFF000000);
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = tx + RbxFont.width(text.substring(0, Math.min(text.length(), input.getCursorPosition())), TEXT, false);
			g.fill(cx, ty + 2, cx + 1, ty + 22, 0xFF000000);
		}
	}

	private static void stroke(GuiGraphicsExtractor g, String s, int x, int y, int argb) {
		int shade = ((argb >>> 24) / 4) << 24; // TextStrokeTransparency 0.75
		RbxFont.draw(g, s, x + 1, y + 1, TEXT, true, shade);
		RbxFont.draw(g, s, x, y, TEXT, true, argb);
	}

	record Line(String name, int nameColor, String text) {}

	/** "<Name> hi" -> "[Name]: hi" with the name coloured; word-wrapped to width. */
	static List<Line> layout(String raw, int width) {
		String name = null, text = raw;
		int color = 0xFFFFFF;
		var m = PLAYER.matcher(raw);
		if (m.matches()) { name = "[" + m.group(1) + "]: "; color = nameColor(m.group(1)); text = m.group(2); }
		List<Line> out = new ArrayList<>();
		int nameW = name == null ? 0 : RbxFont.width(name, TEXT, true);
		StringBuilder cur = new StringBuilder();
		int avail = width - nameW;
		for (String word : text.split(" ")) {
			String next = cur.isEmpty() ? word : cur + " " + word;
			if (!cur.isEmpty() && RbxFont.width(next, TEXT, true) > avail) {
				out.add(new Line(out.isEmpty() ? name : null, color, cur.toString()));
				cur = new StringBuilder(word);
				avail = width;
			} else cur = new StringBuilder(next);
		}
		out.add(new Line(out.isEmpty() ? name : null, color, cur.toString()));
		return out;
	}
}
