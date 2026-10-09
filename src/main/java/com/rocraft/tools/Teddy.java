package com.rocraft.tools;

import com.rocraft.RbxSounds;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Teddy (TeddyScript): hug grip, one of five sayings, wait(2). */
public final class Teddy extends Item {
	public Teddy(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		if (level.isClientSide()) Tools.clientUse(this, 2000);
		else RbxSounds.play(p, RbxSounds.get("teddy.say" + (1 + p.getRandom().nextInt(5))));
		return InteractionResult.CONSUME;
	}
}
