package com.rocraft.client;

import com.rocraft.RocraftConfig;
import com.rocraft.Sync;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class RocraftClient implements ClientModInitializer {
	public static volatile RobloxProfile profile = new RobloxProfile(); // Guest until loaded
	/** How players without a Roblox username (or without Rocraft on the server) look: the 2017 Guest. */
	public static volatile RobloxProfile guest = new RobloxProfile();
	static volatile boolean loading;
	/** The private Roblox asset pack's id, for PackSelectMixin. */
	public static final String ROBLOX_PACK = RobloxPack.ID;

	@Override
	public void onInitializeClient() {
		GearSpecial.register();
		RbxParticle.register();
		RobloxPack.build(); // PackSelectMixin selects it before the first resource load
		Hud2018.register();
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> r) helper.register(new AvatarLayer(r));
		});
		Thread.startVirtualThread(() -> { com.rocraft.tools.Tools.GEAR.forEach(Rig::loadGear); com.rocraft.tools.Tools.HATS.forEach(Rig::loadHat); });
		Animator.load();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (mc.player == null || !RocraftConfig.INSTANCE.hud2018) return;
			// backpack number keys: equip that slot, or unequip it if it's already held (before vanilla sees the press)
			for (int i = 0; i < 9; i++) while (mc.options.keyHotbarSlots[i].consumeClick()) Hud2018.toggle(mc, i);
		});
		// "." opens the emote wheel, as in Roblox (the wheel closes itself on ".")
		boolean[] periodDown = {false};
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_PERIOD);
			if (down && !periodDown[0] && mc.gui.screen() == null) EmoteWheel.open();
			periodDown[0] = down;
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			RobloxMouse.tick();
			RobloxCamera.flyFacing();
			GearEffects.tick(mc);
			CharacterSounds.tick(mc);
			MouseAim.tick(mc);
			// KOs and Wipeouts for the top bar: the server only sends stats when asked (as the Statistics screen does)
			if (mc.player.tickCount % 100 == 1 && RocraftConfig.INSTANCE.hud2018) mc.getConnection().send(
				new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));
		});
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.BOMB_ENTITY, BombRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.PROJECTILE, ProjectileRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.DEBRIS, DebrisRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.PART, PartRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.rocraft.tools.Tools.HOVERBOARD_ENTITY, HoverboardRenderer::new);
		for (var type : com.rocraft.tools.Robloxian.TYPES.keySet()) net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, RobloxianRenderer::new);
		// joining: the chat opens with the "All" channel's welcome message, as in Roblox
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
			if (RocraftConfig.INSTANCE.hud2018) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(RobloxChat.WELCOME));
			sendAvatar();
		}));
		// multiplayer: other players' Roblox accounts and emotes, relayed by the server
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> AVATARS.clear());
		ClientPlayNetworking.registerGlobalReceiver(Sync.Avatar.TYPE, (msg, ctx) -> AVATARS.put(msg.player(), msg));
		ClientPlayNetworking.registerGlobalReceiver(Sync.Emote.TYPE, (msg, ctx) -> Animator.of(msg.entity()).emote(msg.anim()));
		// classic chat emotes: "/e dance", "/e wave", "/e laugh", "/e cheer", "/e point" play on your character
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if ((command.equals("?") || command.equals("help")) && RocraftConfig.INSTANCE.hud2018) { // Roblox's /? and /help
				var chat = mc.gui.hud.getChat();
				for (String line : new String[]{"These are the basic chat commands.", "/e <emote> : play an emote: dance, wave, laugh, cheer or point.",
						"Minecraft commands work too: /help <command> explains one."})
					chat.addClientSystemMessage(Component.literal(line));
				return false;
			}
			if (!command.startsWith("e ") || mc.player == null) return true;
			long id = Animator.pick(command.substring(2).trim().toLowerCase(java.util.Locale.ROOT), profile.r15 != null);
			if (id != 0) emote(id);
			else mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Unknown emote. Try /e dance, /e wave, /e laugh, /e cheer or /e point."));
			return false;
		});
		reloadProfile();
		Thread.startVirtualThread(() -> guest = RobloxProfile.load("", RocraftConfig.INSTANCE.apiKey));
		com.rocraft.tools.Robloxian.USERS.forEach(RobloxianRenderer::profile); // preload every mob's avatar at startup
		boolean[] firstRun = {RocraftConfig.FIRST_RUN};
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
			if (firstRun[0]) { firstRun[0] = false; mc.setScreenAndShow(new SettingsScreen(screen, 0)); }
		});
	}

	/** The Roblox R6 body is available, so AvatarLayer draws the character (and its held items). */
	public static boolean robloxAvatar() { return Rig.body() != null; }

	/** Called by FeatureDispatcherMixin and DynamicUniformsMixin; public so the mixin package can reach them. */
	public static void addMeshRenderer(net.minecraft.client.renderer.feature.FeatureRendererMap map) { map.put(MeshGpu.TYPE, new MeshGpu()); MeshGpu.ready = true; }
	public static void meshEndFrame() { MeshGpu.endFrame(); }

	/** Called by SkinMixin; public so the mixin package can reach it. */
	public static net.minecraft.world.entity.player.PlayerSkin avatarSkin(Player p, net.minecraft.world.entity.player.PlayerSkin base) {
		return AvatarSkin.skin(profileOf(p), base);
	}

	/** player UUID -> the Roblox account they wear, as the server relayed it */
	private static final Map<UUID, Sync.Avatar> AVATARS = new ConcurrentHashMap<>();

	/** Who a player is: you are your profile; others are their synced Roblox account, or the Guest. */
	static RobloxProfile profileOf(Player p) {
		if (p == null || p == Minecraft.getInstance().player) return profile;
		var a = AVATARS.get(p.getUUID());
		return a == null || a.username().isEmpty() ? guest : RobloxianRenderer.profile(a.username(), a.rig());
	}

	/** Play an emote on your character and show it to everyone nearby. */
	static void emote(long animId) {
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Animator.of(mc.player.getId()).emote(animId);
		if (ClientPlayNetworking.canSend(Sync.Emote.TYPE)) ClientPlayNetworking.send(new Sync.Emote(0, animId));
	}

	private static String rig() { return "R15".equals(RocraftConfig.INSTANCE.avatarType) ? "R15" : "R6"; }

	private static void sendAvatar() {
		if (ClientPlayNetworking.canSend(Sync.Avatar.TYPE)) ClientPlayNetworking.send(new Sync.Avatar(new UUID(0, 0), RocraftConfig.INSTANCE.username, rig()));
	}

	static void reloadProfile() {
		var c = RocraftConfig.INSTANCE;
		String u = c.username, k = c.apiKey, rig = rig();
		loading = true;
		Thread.startVirtualThread(() -> { profile = RobloxProfile.load(u, k, rig); loading = false; });
		if (Minecraft.getInstance().getConnection() != null) sendAvatar();
	}
}
