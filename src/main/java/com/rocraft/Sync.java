package com.rocraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Multiplayer: which Roblox account each player wears, and the emotes they play, relayed by the server to everyone. */
public final class Sync {
	/** A player's Roblox username (blank = Guest) and rig ("Account", "R6", "R15"). Client -> server (player ignored), server -> clients. */
	public record Avatar(UUID player, String username, String rig) implements CustomPacketPayload {
		public static final Type<Avatar> TYPE = new Type<>(Rocraft.id("avatar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Avatar> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Avatar::player,
			ByteBufCodecs.stringUtf8(20), Avatar::username, ByteBufCodecs.stringUtf8(7), Avatar::rig, Avatar::new);
		@Override public Type<Avatar> type() { return TYPE; }
	}

	/** An emote animation starting on a player. Client -> server (entity ignored), server -> players who can see them. */
	public record Emote(int entity, long anim) implements CustomPacketPayload {
		public static final Type<Emote> TYPE = new Type<>(Rocraft.id("emote"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Emote> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Emote::entity,
			ByteBufCodecs.VAR_LONG, Emote::anim, Emote::new);
		@Override public Type<Emote> type() { return TYPE; }
	}

	private static final Map<UUID, Avatar> AVATARS = new ConcurrentHashMap<>();

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Avatar.TYPE, Avatar.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Avatar.TYPE, Avatar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Emote.TYPE, Emote.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Emote.TYPE, Emote.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Avatar.TYPE, (msg, ctx) -> {
			// Roblox usernames are letters, digits and one underscore; anything else is a Guest
			String user = msg.username().matches("\\w{3,20}") ? msg.username() : "";
			var a = new Avatar(ctx.player().getUUID(), user, msg.rig().matches("R6|R15") ? msg.rig() : "Account");
			AVATARS.put(a.player(), a);
			for (var p : PlayerLookup.all(ctx.server())) if (ServerPlayNetworking.canSend(p, Avatar.TYPE)) ServerPlayNetworking.send(p, a);
		});
		ServerPlayNetworking.registerGlobalReceiver(Emote.TYPE, (msg, ctx) -> {
			if (msg.anim() <= 0) return;
			var e = new Emote(ctx.player().getId(), msg.anim());
			for (var p : PlayerLookup.tracking(ctx.player())) if (ServerPlayNetworking.canSend(p, Emote.TYPE)) ServerPlayNetworking.send(p, e);
		});
		// someone joining learns everyone's avatar; leaving forgets theirs
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
			if (ServerPlayNetworking.canSend(h.player, Avatar.TYPE)) AVATARS.values().forEach(sender::sendPacket);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> AVATARS.remove(h.player.getUUID()));
	}
}
