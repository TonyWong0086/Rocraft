package com.rocraft.client;

import com.rocraft.RocraftConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rocraft settings drawn as the 2018 Roblox settings menu (SettingsHub): dark shield, tab bar with the menu icons and
 * blue selection underline, rows of "label  < value >", rounded menu buttons. Laid out in real screen pixels with
 * Roblox's own textures and SourceSans, like the HUD.
 */
public final class SettingsScreen extends Screen {
	static final String UI = "textures/ui/Settings/";
	static final String[] TABS = {"Avatar", "Gameplay", "Graphics"};
	static final String[] ICONS = {"MenuBarIcons/PlayersTabIcon.png", "MenuBarIcons/GameSettingsTab.png", "MenuBarIcons/CaptureTab.png"};
	static final int ROW = 52, TAB_Y = 34, TAB_H = 64;
	static final String CREDENTIALS_URL = "https://create.roblox.com/dashboard/credentials";
	static final String[] SETUP = {
		"1. Open create.roblox.com/dashboard/credentials (button below) and click Create API Key.",
		"2. Add API systems: legacy-assets -> legacy-assets:manage, and users -> users.advanced:read.",
		"3. Under Accepted IP Addresses add 0.0.0.0/0 (or your own IP), save, and copy the key.",
		"4. Paste it into Open Cloud Key above, type your username, and press Load Avatar.",
		"5. Restart Minecraft once so the gear, animations and sounds download."};

	private record Hit(int x, int y, int w, int h, Runnable action) {}
	private final List<Hit> hits = new ArrayList<>();
	private final Screen parent;
	private int tab;
	private EditBox user, key;
	private int mx, my; // mouse, screen px

	public SettingsScreen(Screen parent, int tab) {
		super(Component.literal("Rocraft Settings"));
		this.parent = parent;
		this.tab = tab;
	}

	private int s() { return minecraft.getWindow().getGuiScale(); }
	private int W() { return width * s(); }
	private int H() { return height * s(); }
	private int hubW() { return Math.min(860, W() - 40); }
	private int hubX() { return (W() - hubW()) / 2; }
	private int rowsTop() { return TAB_Y + TAB_H + 24; }
	private int avatarRowsW() { return hubW() * 62 / 100; }

	@Override protected void init() {
		var c = RocraftConfig.INSTANCE;
		if (tab == 0) {
			int fx = hubX() + avatarRowsW() - 300;
			user = field(fx, rowsTop(), 280, "Roblox username (blank = Guest)", c.username, 20);
			key = field(fx, rowsTop() + ROW, 280, "Open Cloud API key (optional)", c.apiKey, 4096);
			key.addFormatter((t, i) -> FormattedCharSequence.forward("*".repeat(t.length()), Style.EMPTY));
		}
	}

	private EditBox field(int px, int py, int pw, String hint, String value, int max) {
		int s = s();
		var e = addRenderableWidget(new EditBox(font, (px + 12) / s, (py + ROW / 2) / s - 4, (pw - 24) / s, 12, Component.literal(hint)));
		e.setBordered(false);
		e.setMaxLength(max);
		e.setHint(Component.literal(hint));
		e.setValue(value);
		e.setTextColor(0xFFFFFFFF);
		return e;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
		super.extractBackground(g, mouseX, mouseY, dt);
		int s = s();
		mx = mouseX * s;
		my = mouseY * s;
		hits.clear();
		var m = g.pose();
		m.pushMatrix();
		m.scale(1f / s, 1f / s);
		int W = W(), H = H(), hx = hubX(), hw = hubW();
		g.fill(0, 0, W, H, 0xB4141414); // shield

		// tab bar: icon + name, blue underline on the selected tab
		int tw = hw / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			int x = hx + i * tw, t = i;
			boolean lit = i == tab || in(x, TAB_Y, tw, TAB_H);
			int lw = RbxFont.width(TABS[i], 24, true), ix = x + (tw - (40 + lw)) / 2;
			tex(g, UI + ICONS[i], ix, TAB_Y + (TAB_H - 32) / 2, 32, 32, lit ? 0xFFFFFFFF : 0xFFA0A0A0);
			RbxFont.draw(g, TABS[i], ix + 40, TAB_Y + (TAB_H - 30) / 2, 24, true, lit ? 0xFFFFFFFF : 0xFFA0A0A0);
			if (i == tab) tex(g, UI + "MenuBarAssets/MenuSelection.png", x, TAB_Y + TAB_H - 6, tw, 6, 0xFFFFFFFF);
			hits.add(new Hit(x, TAB_Y, tw, TAB_H, () -> { tab = t; rebuildWidgets(); }));
		}
		g.fill(hx, TAB_Y + TAB_H, hx + hw, TAB_Y + TAB_H + 1, 0x40FFFFFF);

		var c = RocraftConfig.INSTANCE;
		int y = rowsTop();
		switch (tab) {
			case 0 -> {
				int rw = avatarRowsW();
				label(g, hx, y, rw, "Username");
				box(g, hx + rw - 300, y, 280);
				y += ROW;
				label(g, hx, y, rw, "Open Cloud Key");
				box(g, hx + rw - 300, y, 280);
				y += ROW;
				label(g, hx, y, rw, "Avatar");
				button(g, hx + rw - 300, y + 6, 136, ROW - 12, "Load Avatar", true, () -> {
					c.username = user.getValue().trim();
					c.apiKey = key.getValue().trim();
					c.save();
					RocraftClient.reloadProfile();
				});
				button(g, hx + rw - 156, y + 6, 136, ROW - 12, "Play as Guest", false, () -> {
					c.username = "";
					user.setValue("");
					c.save();
					RocraftClient.reloadProfile();
				});
				y += ROW;
				RbxFont.draw(g, "Your key stays in config/rocraft.json on this PC.", hx + 20, y + 14, 18, false, 0xFF8C8C8C);
				y += 44;
				// setup help: Roblox's assets aren't shipped, they come from the install and the user's own key
				boolean installed = RobloxAssets.CONTENT != null;
				y = para(g, installed ? "Roblox install found." : "Roblox isn't installed: install it from roblox.com/download and restart.",
					hx + 20, y, rw - 40, 18, true, installed ? 0xFF1BFC6B : 0xFFFF5A4A) + 8;
				y = para(g, "Getting an Open Cloud API key (free, needed to load the Roblox assets):", hx + 20, y, rw - 40, 18, true, 0xFFFFFFFF) + 4;
				for (String step : SETUP) y = para(g, step, hx + 32, y, rw - 52, 17, false, 0xFFC8C8C8);
				button(g, hx + 20, y + 6, 300, ROW - 12, "Open Roblox Credentials", false,
					() -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, CREDENTIALS_URL));
				var p = RocraftClient.profile;
				// avatar frame: your character as it loads, like the 2018 avatar thumbnail
				int px = hx + rw + (hw - rw) / 2, fy = rowsTop(), fw = AvatarFrame.W, fh = AvatarFrame.H;
				nine(g, UI + "MenuBarAssets/MenuButton.png", px - fw / 2 - 8, fy - 8, fw + 16, fh + 16, 54, 54, 8, 0xFFFFFFFF);
				Identifier pic = AvatarFrame.texture(p);
				if (pic != null) g.blit(RenderPipelines.GUI_TEXTURED, pic, px - fw / 2, fy, 0, 0, fw, fh, fw, fh);
				if (pic == null || AvatarFrame.busy() || RocraftClient.loading) {
					String l = "Loading...";
					RbxFont.draw(g, l, px - RbxFont.width(l, 20, false) / 2, fy + fh - 34, 20, false, 0xFFDCDCDC);
				}
				int py = fy + fh + 18;
				RbxFont.draw(g, p.name, px - RbxFont.width(p.name, 24, true) / 2, py, 24, true, 0xFFFFFFFF);
				String sub = p.guest ? "Guest" : "Account: 13+";
				RbxFont.draw(g, sub, px - RbxFont.width(sub, 18, false) / 2, py + 30, 18, false, 0xFFB4B4B4);
			}
			case 1 -> {
				y = toggle(g, y, "Roblox Movement", () -> c.robloxMovement, v -> c.robloxMovement = v, "On", "Off");
				y = toggle(g, y, "Fall Damage", () -> c.fallDamage, v -> c.fallDamage = v, "On", "Off");
				y = toggle(g, y, "Hunger", () -> c.hunger, v -> c.hunger = v, "On", "Off");
				// Account / R6 / R15, cycled; Account shows what the Roblox account uses
				y = toggle(g, y, "Avatar Type", () -> false, v -> {
					c.avatarType = switch (c.avatarType) { case "Account" -> "R6"; case "R6" -> "R15"; default -> "Account"; };
					RocraftClient.reloadProfile();
				}, "", c.avatarType.equals("Account") ? "Account (" + (RocraftClient.profile.r15 != null ? "R15" : "R6") + ")" : c.avatarType);
				RbxFont.draw(g, "Roblox movement: WalkSpeed 16, JumpPower 50, gravity 196.2", hx + 20, y + 14, 18, false, 0xFF8C8C8C);
			}
			default -> {
				y = toggle(g, y, "2018 Interface", () -> c.hud2018, v -> c.hud2018 = v, "On", "Off");
				y = toggle(g, y, "Roblox Font", () -> c.robloxFont, v -> c.robloxFont = v, "On", "Off");
				y = toggle(g, y, "Camera Mode", () -> c.robloxCamera, v -> c.robloxCamera = v, "Classic", "Minecraft");
				// optimisations another installed mod already does are left to it
				y = toggle(g, y, "Hide Unseen Entities", () -> c.entityCulling, v -> c.entityCulling = v,
					Perf.CULLING_BY == null ? "On" : "By " + Perf.CULLING_BY, Perf.CULLING_BY == null ? "Off" : "By " + Perf.CULLING_BY);
				y = toggle(g, y, "Unfocused FPS Limit", () -> c.unfocusedFps, v -> c.unfocusedFps = v,
					Perf.FPS_BY == null ? Perf.UNFOCUSED_FPS + " FPS" : "By " + Perf.FPS_BY, Perf.FPS_BY == null ? "Off" : "By " + Perf.FPS_BY);
				y = toggle(g, y, "Adaptive Performance", () -> c.adaptivePerf, v -> c.adaptivePerf = v, "On", "Off");
				y = toggle(g, y, "Stable Frame Pacing", () -> c.stableFps, v -> c.stableFps = v, "On", "Off");
				RbxFont.draw(g, "Icons, font and cursor load from your Roblox install.", hx + 20, y + 14, 18, false, 0xFF8C8C8C);
			}
		}

		// bottom bar, like the 2018 "Resume Game" button with its Esc key hint
		int bw = 300, bx = (W - bw) / 2, by = H - 90;
		button(g, bx, by, bw, 56, "Back", false, this::onClose);
		tex(g, UI + "Help/EscapeIcon.png", bx + bw - 52, by + 12, 32, 32, 0xFFFFFFFF);
		m.popMatrix();
	}

	/** Word-wrapped text; returns the y under it. */
	private static int para(GuiGraphicsExtractor g, String text, int x, int y, int w, int px, boolean bold, int argb) {
		String line = "";
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && RbxFont.width(next, px, bold) > w) { RbxFont.draw(g, line, x, y, px, bold, argb); y += px + 5; line = word; }
			else line = next;
		}
		RbxFont.draw(g, line, x, y, px, bold, argb);
		return y + px + 5;
	}

	private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

	private void label(GuiGraphicsExtractor g, int x, int y, int w, String text) {
		if (in(x, y, w, ROW)) g.fill(x, y, x + w, y + ROW, 0x14FFFFFF);
		RbxFont.draw(g, text, x + 20, y + (ROW - 30) / 2, 24, false, 0xFFFFFFFF);
		g.fill(x, y + ROW - 1, x + w, y + ROW, 0x33FFFFFF);
	}

	/** Text field background; the EditBox draws its text on top. */
	private void box(GuiGraphicsExtractor g, int x, int y, int w) {
		nine(g, UI + "MenuBarAssets/MenuButton.png", x, y + 8, w, ROW - 16, 54, 54, 8, 0xFFFFFFFF);
	}

	/** "Label      <  value  >" selector row; clicking anywhere on the selector flips it. */
	private int toggle(GuiGraphicsExtractor g, int y, String name, BooleanSupplier get, Consumer<Boolean> set, String on, String off) {
		int hx = hubX(), hw = hubW();
		label(g, hx, y, hw, name);
		int ax = hx + hw - 300, aw = 280;
		boolean hover = in(ax - 10, y, aw + 20, ROW);
		tex(g, UI + "Slider/Left.png", ax, y + (ROW - 31) / 2, 18, 31, hover ? 0xFFFFFFFF : 0xFFA0A0A0);
		tex(g, UI + "Slider/Right.png", ax + aw - 18, y + (ROW - 31) / 2, 18, 31, hover ? 0xFFFFFFFF : 0xFFA0A0A0);
		String v = get.getAsBoolean() ? on : off;
		RbxFont.draw(g, v, ax + (aw - RbxFont.width(v, 24, true)) / 2, y + (ROW - 30) / 2, 24, true, 0xFFFFFFFF);
		hits.add(new Hit(ax - 10, y, aw + 20, ROW, () -> { set.accept(!get.getAsBoolean()); RocraftConfig.INSTANCE.save(); }));
		return y + ROW;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean primary, Runnable action) {
		boolean hover = in(x, y, w, h);
		nine(g, UI + (primary || hover ? "MenuBarAssets/MenuButtonSelected.png" : "MenuBarAssets/MenuButton.png"), x, y, w, h, 54, 54, 8, 0xFFFFFFFF);
		RbxFont.draw(g, text, x + (w - RbxFont.width(text, 22, true)) / 2, y + (h - 28) / 2, 22, true, 0xFFFFFFFF);
		hits.add(new Hit(x, y, w, h, action));
	}

	/** Whole Roblox UI texture stretched to w x h. */
	private static void tex(GuiGraphicsExtractor g, String rel, int x, int y, int w, int h, int argb) {
		Identifier t = RobloxAssets.tex(rel);
		int[] sz = RobloxAssets.size(rel);
		if (t != null) g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0, 0, w, h, sz[0], sz[1], sz[0], sz[1], argb);
	}

	/** 9-slice a Roblox UI texture (corner c px); flat fill without a Roblox install. */
	private static void nine(GuiGraphicsExtractor g, String rel, int x, int y, int w, int h, int tw, int th, int c, int argb) {
		Identifier t = RobloxAssets.tex(rel);
		if (t == null) { g.fill(x, y, x + w, y + h, 0xFF4A4A4A); return; }
		int[] xs = {x, x + c, x + w - c}, ws = {c, w - 2 * c, c}, us = {0, c, tw - c}, uw = {c, tw - 2 * c, c};
		int[] ys = {y, y + c, y + h - c}, hs = {c, h - 2 * c, c}, vs = {0, c, th - c}, vh = {c, th - 2 * c, c};
		for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
			g.blit(RenderPipelines.GUI_TEXTURED, t, xs[i], ys[j], us[i], vs[j], ws[i], hs[j], uw[i], vh[j], tw, th, argb);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		int s = s(), x = (int) (e.x() * s), y = (int) (e.y() * s);
		for (Hit h : List.copyOf(hits))
			if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
				h.action.run();
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
				return true;
			}
		return false;
	}

	@Override public void onClose() {
		RocraftConfig.INSTANCE.save();
		minecraft.setScreenAndShow(parent);
	}
}
