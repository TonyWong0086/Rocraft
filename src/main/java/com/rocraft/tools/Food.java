package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.Rocraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Taco / Burger / Chicken / Pizza (SandwichScript, PizzaScript): eating grip, sound, wait(.8), Health + 1.6. */
public final class Food extends Item {
	private final String eatSound;

	public Food(Properties p, String eatSound) { super(p); this.eatSound = eatSound; }

	String openSound() { return BuiltInRegistries.ITEM.getKey(this).getPath() + ".opensound"; }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		var stack = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		p.getCooldowns().addCooldown(stack, 16);
		if (level.isClientSide()) Tools.clientUse(this, 800);
		else {
			RbxSounds.play(p, RbxSounds.get(eatSound));
			Tools.later(16, () -> { if (p.isAlive()) p.heal(1.6f * 0.2f); });
		}
		return InteractionResult.SUCCESS;
	}
}
