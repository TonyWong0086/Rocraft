package com.rocraft.tools;

import com.rocraft.Rocraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Client -> server: unequip (or re-equip) the tool in a hotbar slot when every hotbar slot is full, so there's no empty
 * hand to switch to. Minecraft always holds its selected slot, so the tool waits in the main inventory while unequipped
 * and goes back to its slot when re-equipped or when another slot is picked.
 */
public record Holster(int slot) implements CustomPacketPayload {
	public static final Type<Holster> TYPE = new Type<>(Rocraft.id("holster"));
	public static final StreamCodec<RegistryFriendlyByteBuf, Holster> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Holster::slot, Holster::new);
	/** player -> {hotbar slot, inventory slot the tool waits in} */
	private static final Map<UUID, int[]> HELD = new HashMap<>();

	@Override public Type<Holster> type() { return TYPE; }

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (msg, ctx) -> {
			var p = ctx.player();
			int[] h = HELD.remove(p.getUUID());
			if (h != null) { restore(p, h); return; } // clicking again: re-equip
			var inv = p.getInventory();
			int slot = msg.slot();
			if (slot < 0 || slot > 8 || slot != inv.getSelectedSlot() || inv.getItem(slot).isEmpty()) return;
			for (int i = 9; i < 36; i++)
				if (inv.getItem(i).isEmpty()) {
					inv.setItem(i, inv.getItem(slot));
					inv.setItem(slot, ItemStack.EMPTY);
					HELD.put(p.getUUID(), new int[]{slot, i});
					return;
				}
		});
	}

	/** Server tick: picking another slot (or the slot filling up) puts the waiting tool back. */
	static void tick(ServerPlayer p) {
		int[] h = HELD.get(p.getUUID());
		if (h == null) return;
		var inv = p.getInventory();
		if (inv.getSelectedSlot() != h[0] || !inv.getItem(h[0]).isEmpty()) { HELD.remove(p.getUUID()); restore(p, h); }
	}

	private static void restore(ServerPlayer p, int[] h) {
		var inv = p.getInventory();
		if (inv.getItem(h[0]).isEmpty() && !inv.getItem(h[1]).isEmpty()) {
			inv.setItem(h[0], inv.getItem(h[1]));
			inv.setItem(h[1], ItemStack.EMPTY);
		}
	}
}
