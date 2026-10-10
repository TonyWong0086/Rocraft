package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The planted classic bomb (Bomb script): a 2-stud mirror ball (Reflectance 1) ticking each beat;
 * the beat starts at 0.4 s and shrinks x0.9 until it's under 0.1 s, then Explosion BlastRadius 12. Unanchored, so it
 * falls, bounces and rolls, and characters walking into it kick it along. The blast kills characters in the radius
 * and blows the bricks around it loose.
 */
public final class BombEntity extends Entity {
	static final double RADIUS = 12 * McFrame.STUD;
	/** tick (from spawn) of each colour flip, from updateInterval = .4, *= .9 while > .1 */
	public static final int[] FLIPS;
	static {
		var l = new java.util.ArrayList<Integer>();
		double t = 0, interval = 0.4;
		while (interval > 0.1) { t += interval; interval *= 0.9; l.add((int) Math.round(t * 20)); }
		FLIPS = l.stream().mapToInt(Integer::intValue).toArray();
	}
	private Player owner;

	public BombEntity(EntityType<? extends BombEntity> type, Level level) { super(type, level); }

	void setOwner(Player p) { owner = p; }

	@Override protected double getDefaultGravity() { return McFrame.GRAVITY; }

	@Override
	public void tick() {
		super.tick();
		applyGravity();
		var want = getDeltaMovement();
		move(MoverType.SELF, want);
		var got = getDeltaMovement();
		double vx = got.x, vy = got.y, vz = got.z;
		if (horizontalCollision) { // a ball bounces off walls
			if (Math.abs(got.x) < Math.abs(want.x) * 0.5) vx = -want.x * 0.5;
			if (Math.abs(got.z) < Math.abs(want.z) * 0.5) vz = -want.z * 0.5;
		}
		if (verticalCollision && want.y < 0) vy = -want.y > 2.5 * McFrame.GRAVITY ? -want.y * 0.4 : 0; // bounces when dropped
		double roll = onGround() ? 0.97 : 0.995; // a ball keeps rolling: only a little rolling resistance
		setDeltaMovement(vx * roll, vy, vz * roll);
		if (!(level() instanceof ServerLevel sl)) return;
		kick(sl);
		for (int f : FLIPS) if (tickCount == f) com.rocraft.RbxSounds.play(this, com.rocraft.RbxSounds.get("bomb.tick")); // ticksound: clickfast.wav
		if (tickCount == FLIPS[FLIPS.length - 1] + 1) {
			Blast.at(sl, this, RADIUS, Float.MAX_VALUE, sl.damageSources().explosion(this, owner), "bomb.explode"); // Rocket shot.wav
			discard();
		}
	}

	/**
	 * Characters walking into the ball knock it along: it picks up the part of their velocity aimed at it (a light
	 * ball off a heavy character: about 1.5x), so you can dribble it, and explosions push it like any part.
	 */
	private void kick(ServerLevel sl) {
		for (var e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.1), LivingEntity::isAlive)) {
			var n = new net.minecraft.world.phys.Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
			if (n.lengthSqr() < 1e-6) continue;
			n = n.normalize();
			var ev = new net.minecraft.world.phys.Vec3(e.getX() - e.xo, 0, e.getZ() - e.zo);
			double closing = ev.dot(n) - getDeltaMovement().dot(n);
			if (closing > 0) setDeltaMovement(getDeltaMovement().add(n.scale(closing * 1.5 + 0.02)).add(0, 0.05, 0));
			else setDeltaMovement(getDeltaMovement().add(n.scale(0.02))); // standing against it: nudged out of the way
			hurtMarked = true; // send the new velocity to clients now
		}
	}

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
	@Override public boolean hurtServer(ServerLevel level, DamageSource src, float amount) { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
}
