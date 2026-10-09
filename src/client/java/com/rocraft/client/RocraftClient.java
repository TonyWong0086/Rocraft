package com.rocraft.client;

import com.rocraft.RocraftConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class RocraftClient implements ClientModInitializer {
	public static volatile RobloxProfile profile = new RobloxProfile(); // Guest until loaded
	/** How everyone else looks (their Roblox accounts aren't known): the 2017 Guest. */
	public static volatile RobloxProfile guest = new RobloxProfile();
	static volatile boolean loading;

	@Override
	public void onInitializeClient() {
		GearSpecial.register();
		RbxParticle.register();
		RobloxPack.build();
		Hud2018.register();
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> r) helper.register(new AvatarLayer(r));
		});
		Thread.startVirtualThread(() -> com.rocraft.tools.Tools.GEAR.forEach(Rig::loadGear));
		Animator.load();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (mc.player == null || !RocraftConfig.INSTANCE.hud2018) return;
			// backpack number keys: equip that slot, or unequip it if it's already held (before vanilla sees the press)
			for (int i = 0; i < 9; i++) while (mc.options.keyHotbarSlots[i].consumeClick()) Hud2018.toggle(mc, i);
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			RobloxMouse.tick();
			RobloxCamera.flyFacing();
			GearEffects.tick(mc);
			CharacterSounds.tick(mc);
			MouseAim.tick(mc);
		});
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.BOMB_ENTITY, BombRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.PROJECTILE, ProjectileRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.DEBRIS, DebrisRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.PART, PartRenderer::new);
		// classic chat emotes: "/e dance", "/e wave", "/e laugh", "/e cheer", "/e point" play on your character
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (!command.startsWith("e ") || mc.player == null) return true;
			if (!Animator.of(mc.player.getId()).emote(command.substring(2).trim().toLowerCase(java.util.Locale.ROOT)))
				mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Unknown emote. Try /e dance, /e wave, /e laugh, /e cheer or /e point."));
			return false;
		});
		reloadProfile();
		Thread.startVirtualThread(() -> guest = RobloxProfile.load("", RocraftConfig.INSTANCE.apiKey));
		boolean[] firstRun = {RocraftConfig.FIRST_RUN};
		boolean[] packOn = {false};
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof TitleScreen)) return;
			var widgets = Screens.getWidgets(screen);
			var friends = widgets.stream().filter(wd -> wd instanceof net.minecraft.client.gui.components.FriendsButton).findFirst();
			int bx = 4, by = 4;
			if (friends.isPresent()) { // beside Friends: the first free 20x20 spot to its left, else to its right
				var f = friends.get();
				by = f.getY();
				for (int step = 1; step < 8; step++) {
					int cx = f.getX() + (step % 2 == 1 ? -24 : 24) * ((step + 1) / 2);
					if (widgets.stream().noneMatch(wd -> wd.getY() < f.getY() + 20 && wd.getY() + wd.getHeight() > f.getY() && wd.getX() < cx + 20 && wd.getX() + wd.getWidth() > cx)) { bx = cx; break; }
				}
			}
			widgets.add(new RocraftButton(bx, by, () -> mc.setScreenAndShow(new SettingsScreen(screen, 0))));
			if (!packOn[0]) { packOn[0] = true; RobloxPack.enable(mc); }
			if (firstRun[0]) { firstRun[0] = false; mc.setScreenAndShow(new SettingsScreen(screen, 0)); }
		});
	}

	/** Called by SkinMixin; public so the mixin package can reach it. */
	public static net.minecraft.world.entity.player.PlayerSkin avatarSkin(boolean me, net.minecraft.world.entity.player.PlayerSkin base) {
		return AvatarSkin.skin(me ? profile : guest, base);
	}

	static void reloadProfile() {
		var c = RocraftConfig.INSTANCE;
		String u = c.username, k = c.apiKey;
		loading = true;
		Thread.startVirtualThread(() -> { profile = RobloxProfile.load(u, k); loading = false; });
	}
}
