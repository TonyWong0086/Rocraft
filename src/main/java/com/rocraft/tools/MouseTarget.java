package com.rocraft.tools;

import com.rocraft.Rocraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Client -> server: where the player's mouse points (Roblox tools aim at Mouse.Hit). */
public record MouseTarget(Vec3 pos) implements CustomPacketPayload {
	public static final Type<MouseTarget> TYPE = new Type<>(Rocraft.id("mouse"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MouseTarget> CODEC = StreamCodec.composite(Vec3.STREAM_CODEC, MouseTarget::pos, MouseTarget::new);

	@Override public Type<MouseTarget> type() { return TYPE; }

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (msg, ctx) -> {
			var p = ctx.player();
			// trust only nearby points (Roblox clamps Mouse.Hit too)
			if (msg.pos().distanceTo(p.getEyePosition()) < 400 * 0.28) Tools.MOUSE.put(p.getUUID(), msg.pos());
		});
	}
}
