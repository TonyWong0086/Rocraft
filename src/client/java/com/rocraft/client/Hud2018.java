package com.rocraft.client;

import com.rocraft.Rocraft;
import com.rocraft.RocraftConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 2018 Roblox HUD, drawn in real screen pixels like Roblox does: top bar (menu, chat, backpack, name, health),
 * player list under it, backpack hotbar at the bottom. Vanilla bars hide while it's on (Graphics > 2018 HUD).
 */
final class Hud2018 {
	static final String UI = "textures/ui/";
	static final int BG = 0x801F1F1F;            // Color3(31,31,31), transparency 0.5
	static final int EQUIP = 0xFF5A8EE9;         // Color3(90,142,233), equipped slot
	static final int TOP = 36, SLOT = 60, GAP = 5;

	/** Hotbar slots drawn last frame: {x, y, inventory slot} in screen pixels, for mouse clicks. */
	static final List<int[]> SLOTS = new ArrayList<>();

	/**
	 * Backpack: equip a slot, or unequip it if it is the one already held (number key or click). Unequipped = holding
	 * an empty hotbar slot, which the Roblox hotbar doesn't show.
	 */
	static void toggle(Minecraft mc, int slot) {
		var inv = mc.player.getInventory();
		if (slot != inv.getSelectedSlot() || inv.getItem(slot).isEmpty()) { inv.setSelectedSlot(slot); return; }
		for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) { inv.setSelectedSlot(i); return; }
		// ponytail: every hotbar slot is full, so there is no empty hand to switch to; the tool stays equipped
	}

	/** Hotbar slot under a screen-pixel point, or -1. */
	static int slotAt(double x, double y) {
		for (int[] s : SLOTS) if (x >= s[0] && x < s[0] + SLOT && y >= s[1] && y < s[1] + SLOT) return s[2];
		return -1;
	}

	static void register() {
		for (Identifier id : List.of(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
				VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.CHAT))
			HudElementRegistry.replaceElement(id, old -> (g, dt) -> { if (!RocraftConfig.INSTANCE.hud2018) old.extractRenderState(g, dt); });
		HudElementRegistry.addLast(Rocraft.id("hud2018"), Hud2018::draw);
	}

	static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (!RocraftConfig.INSTANCE.hud2018 || mc.player == null) return;
		int s = mc.getWindow().getGuiScale(), W = g.guiWidth() * s, H = g.guiHeight() * s;
		var m = g.pose();
		m.pushMatrix();
		m.scale(1f / s, 1f / s); // from here on, 1 unit = 1 screen pixel

		// top bar
		g.fill(0, 0, W, TOP, BG);
		icon(g, "Menu/Hamburger.png", 16, 6, 32, 25);
		boolean chatOpen = mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
		icon(g, chatOpen ? "Chat/ChatDown.png" : "Chat/Chat.png", 62, 5, 28, 27); // 2018 chat: white, Roblox blue (#00A2FE) when open
		int unread = RobloxChat.unread(mc);
		if (unread > 0 && !chatOpen) { // MessageCounter badge on the chat icon
			icon(g, "Chat/MessageCounter.png", 80, 2, 18, 18);
			String n = unread > 99 ? "99+" : String.valueOf(unread);
			RbxFont.draw(g, n, 89 - RbxFont.width(n, 13, true) / 2, 3, 13, true, 0xFFFFFFFF);
		}
		boolean invOpen = mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
		icon(g, invOpen ? "Backpack/Backpack_Down.png" : "Backpack/Backpack.png", 106, 4, 22, 28); // blue while the backpack is open
		// right side, as the 2018 top bar: [name / "Account: 13+" (health bar when hurt)] [stat columns: name over value]
		var prof = RocraftClient.profile;
		String[][] stats = leaderstats(mc);
		int nameW = Math.max(RbxFont.width(prof.name, 15, true), RbxFont.width("Account: 13+", 12, false));
		int[] colW = new int[stats.length];
		int total = nameW;
		for (int i = 0; i < stats.length; i++) total += colW[i] = Math.max(60, Math.max(RbxFont.width(stats[i][0], 13, false), RbxFont.width(stats[i][1], 15, true)) + 22);
		int nx = W - 24 - total;
		RbxFont.draw(g, prof.name, nx, 3, 15, true, 0xFFFFFFFF);
		float hp = Math.max(0, Math.min(1, mc.player.getHealth() / mc.player.getMaxHealth()));
		if (hp < 1) {
			bar(g, "TopBar/HealthBarBase.png", nx, 22, nameW, 0xFFFFFFFF);
			int col = hp > 0.5f ? 0xFF1BFC6B : hp > 0.25f ? 0xFFFFD21C : 0xFFFF1C00;
			if (hp > 0) bar(g, "TopBar/HealthBar.png", nx, 22, Math.max(6, Math.round(nameW * hp)), col);
		} else RbxFont.draw(g, prof.guest ? "Guest" : "Account: 13+", nx, 20, 12, false, 0xFFDCDCDC);
		int sx = nx + nameW;
		for (int i = 0; i < stats.length; i++) {
			int cw = colW[i];
			RbxFont.draw(g, stats[i][0], sx + (cw - RbxFont.width(stats[i][0], 13, false)) / 2, 3, 13, false, 0xFFDCDCDC);
			RbxFont.draw(g, stats[i][1], sx + (cw - RbxFont.width(stats[i][1], 15, true)) / 2, 17, 15, true, 0xFFFFFFFF);
			sx += cw;
		}

		RobloxChat.draw(g, mc, W, H, TOP);

		// backpack hotbar: only filled slots, keeping their real number so 1-9 keys still match
		var inv = mc.player.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 9; i++) if (!inv.getItem(i).isEmpty()) slots.add(i);
		int x = (W - (slots.size() * (SLOT + GAP) - GAP)) / 2, y = H - SLOT - 4;
		SLOTS.clear();
		for (int i : slots) {
			SLOTS.add(new int[]{x, y, i});
			if (i == inv.getSelectedSlot()) g.fill(x - 3, y - 3, x + SLOT + 3, y + SLOT + 3, EQUIP);
			g.fill(x, y, x + SLOT, y + SLOT, i == inv.getSelectedSlot() ? 0xE01F1F1F : BG);
			var st = inv.getItem(i);
			m.pushMatrix();
			m.translate(x + 8, y + 8);
			m.scale(44 / 16f, 44 / 16f);
			g.item(st, 0, 0);
			m.popMatrix();
			RbxFont.draw(g, String.valueOf(i + 1), x + 4, y + 1, 14, false, 0xFFFFFFFF);
			if (st.getCount() > 1) {
				String n = String.valueOf(st.getCount());
				RbxFont.draw(g, n, x + SLOT - 4 - RbxFont.width(n, 14, true), y + SLOT - 18, 14, true, 0xFFFFFFFF);
			}
			x += SLOT + GAP;
		}
		m.popMatrix();
	}

	/** Classic leaderstats: Level (XP level), KOs and Wipeouts (singleplayer: read from the integrated server's stats). */
	static String[][] leaderstats(Minecraft mc) {
		int kos = 0, wipeouts = 0;
		var srv = mc.getSingleplayerServer();
		var sp = srv == null ? null : srv.getPlayerList().getPlayer(mc.player.getUUID());
		if (sp != null) { // ponytail: multiplayer would need the server to send these
			var st = sp.getStats();
			kos = st.getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.MOB_KILLS) + st.getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.PLAYER_KILLS);
			wipeouts = st.getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.DEATHS);
		}
		return new String[][]{{"Level", String.valueOf(mc.player.experienceLevel)}, {"KOs", String.valueOf(kos)}, {"Wipeouts", String.valueOf(wipeouts)}};
	}

	/** Roblox icon at native size; a flat square if the user has no Roblox install. */
	static void icon(GuiGraphicsExtractor g, String rel, int x, int y, int w, int h) {
		Identifier t = RobloxAssets.tex(UI + rel);
		if (t == null) g.fill(x, y, x + w, y + h, 0x60FFFFFF);
		else g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0, 0, w, h, w, h);
	}

	/** 7x6 rounded health bar texture as a 3-slice, drawn 2x tall. */
	static void bar(GuiGraphicsExtractor g, String rel, int x, int y, int w, int argb) {
		Identifier t = RobloxAssets.tex(UI + rel);
		if (t == null) { g.fill(x, y, x + w, y + 12, argb); return; }
		g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0, 0, 6, 12, 3, 6, 7, 6, argb);
		if (w > 12) g.blit(RenderPipelines.GUI_TEXTURED, t, x + 6, y, 3, 0, w - 12, 12, 1, 6, 7, 6, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, t, x + w - 6, y, 4, 0, 6, 12, 3, 6, 7, 6, argb);
	}
}
