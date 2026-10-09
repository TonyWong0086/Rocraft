package com.rocraft.tools;

import com.rocraft.RbxSounds;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Bloxy Cola (gear 10472779), from BloxyColaScript: on Activated switch to the drinking grip, DrinkSound, wait(3),
 * Health + 5 (of 100), back to the resting grip. Never runs out.
 */
public final class BloxyCola extends Item {
	public static final int DRINK_TICKS = 60;

	public BloxyCola(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		var stack = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL; // `enabled` flag
		p.getCooldowns().addCooldown(stack, DRINK_TICKS);
		if (level.isClientSide()) Tools.clientUse(this, DRINK_TICKS * 50);
		else {
			RbxSounds.play(p, RbxSounds.COLA_DRINK);
			Tools.later(DRINK_TICKS, () -> { if (p.isAlive()) p.heal(5 * 0.2f); }); // 5 of 100 -> 1 of 20
		}
		return InteractionResult.SUCCESS;
	}
}
