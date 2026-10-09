package com.rocraft.tools;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Classic Roblox Linked Sword. Roblox damage x0.2 (100 HP -> 20 HP):
 * slash (left click) 10 -> 2, set by item attributes; lunge (right click) 30 -> 6 to everything in front, with a hop.
 */
public final class LinkedSword extends Item {
	static final float LUNGE_DAMAGE = 30 * 0.2f;
	static final int LUNGE_COOLDOWN = 20; // ticks
	static final double REACH = 3.0;

	public LinkedSword(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		var stack = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		p.getCooldowns().addCooldown(stack, LUNGE_COOLDOWN);
		// Roblox lunge holds the character up (BodyVelocity 10 studs/s for 0.25 s); one impulse with a similar rise
		var v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, Math.max(v.y, 0.5), v.z);
		if (level.isClientSide()) Tools.clientLungeAt = System.currentTimeMillis();
		if (level instanceof ServerLevel sl) {
			Tools.lunged(p);
			com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.LUNGE);
			var look = p.getLookAngle();
			var box = p.getBoundingBox().expandTowards(look.x * REACH, 0, look.z * REACH).inflate(0.75);
			for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive()))
				e.hurtServer(sl, p.damageSources().playerAttack(p), LUNGE_DAMAGE);
			p.hurtMarked = true; // push the hop to the client
		}
		return InteractionResult.SUCCESS;
	}
}
