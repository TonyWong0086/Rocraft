package com.rocraft.client;

import com.rocraft.RocraftConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class RocraftClient implements ClientModInitializer {
	public static volatile RobloxProfile profile = new RobloxProfile(); // Guest until loaded

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
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			RobloxMouse.tick();
			GearEffects.tick(mc);
			MouseAim.tick(mc);
		});
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.BOMB_ENTITY, BombRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.PROJECTILE, ProjectileRenderer::new);
		reloadProfile();
		boolean[] firstRun = {RocraftConfig.FIRST_RUN};
		boolean[] packOn = {false};
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof TitleScreen)) return;
			Screens.getWidgets(screen).add(Button.builder(Component.literal("Rocraft"),
				b -> mc.setScreenAndShow(new SettingsScreen(screen, 0))).bounds(4, 4, 70, 20).build());
			if (!packOn[0]) { packOn[0] = true; RobloxPack.enable(mc); }
			if (firstRun[0]) { firstRun[0] = false; mc.setScreenAndShow(new SettingsScreen(screen, 0)); }
		});
	}

	/** Called by SkinMixin; public so the mixin package can reach it. */
	public static net.minecraft.world.entity.player.PlayerSkin avatarSkin(boolean me, net.minecraft.world.entity.player.PlayerSkin base) {
		return AvatarSkin.skin(me ? profile : RobloxProfile.GUEST_PROFILE, base);
	}

	static void reloadProfile() {
		var c = RocraftConfig.INSTANCE;
		String u = c.username, k = c.apiKey;
		Thread.startVirtualThread(() -> profile = RobloxProfile.load(u, k));
	}
}
