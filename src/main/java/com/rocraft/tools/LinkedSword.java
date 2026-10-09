package com.rocraft.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Classic Roblox Linked Sword. The blade hurts whatever living thing it touches (Handle.Touched): 5 while just held,
 * 10 during a slash (one click), 30 during a lunge (a second click shortly after), Roblox damage x0.2 for Minecraft
 * health. The lunge also throws the character forward and up a little, which buys extra airtime in a jump.
 * Minecraft's own attack does nothing (the item's attack damage is 0) so only the blade counts.
 */
public final class LinkedSword extends Item {
	static final float IDLE = 5 * 0.2f, SLASH = 10 * 0.2f, LUNGE = 30 * 0.2f;
	static final int SLASH_TICKS = 10, LUNGE_TICKS = 20, LUNGE_COOLDOWN = 20;
	static final double REACH = 4 * com.rocraft.sim.McFrame.STUD; // the blade sticks out about 4 studs from the hand
	private static final Map<UUID, Long> SLASH_UNTIL = new HashMap<>(), LUNGE_UNTIL = new HashMap<>();

	public LinkedSword(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		var stack = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		p.getCooldowns().addCooldown(stack, LUNGE_COOLDOWN);
		// lunge: a slight forward push with a lift, so a lunge mid-jump carries further
		var look = p.getLookAngle().multiply(1, 0, 1).normalize();
		var v = p.getDeltaMovement();
		p.setDeltaMovement(v.x + look.x * 0.35, Math.max(v.y, 0.35), v.z + look.z * 0.35);
		if (level.isClientSide()) Tools.clientLungeAt = System.currentTimeMillis();
		if (level instanceof ServerLevel sl) {
			Tools.lunged(p);
			LUNGE_UNTIL.put(p.getUUID(), sl.getGameTime() + LUNGE_TICKS);
			com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.LUNGE);
			p.hurtMarked = true; // push the hop to the client
		}
		return InteractionResult.SUCCESS;
	}

	/** A click (swing packet) starts a slash. */
	public static void slashed(Player p) { SLASH_UNTIL.put(p.getUUID(), p.level().getGameTime() + SLASH_TICKS); }

	/** Every server tick for a player holding the sword: the blade's current damage to everything it touches. */
	static void touch(ServerPlayer p) {
		var sl = p.level();
		long now = sl.getGameTime();
		float dmg = now < LUNGE_UNTIL.getOrDefault(p.getUUID(), 0L) ? LUNGE : now < SLASH_UNTIL.getOrDefault(p.getUUID(), 0L) ? SLASH : IDLE;
		var look = p.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 hand = p.position().add(0, 0.9, 0).add(look.scale(0.4));
		AABB blade = new AABB(hand, hand.add(look.scale(REACH))).inflate(0.25, 0.4, 0.25);
		// Minecraft's hurt cooldown stands in for Touched firing again; a stronger hit still lands through it
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, blade, e -> e != p && e.isAlive()))
			e.hurtServer(sl, p.damageSources().playerAttack(p), dmg);
	}
}
