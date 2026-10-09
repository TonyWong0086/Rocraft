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
 * falls and rolls. The blast kills characters in the radius and blows the bricks around it loose.
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
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(getDeltaMovement().scale(onGround() ? 0.9 : 0.98)); // a ball keeps rolling
		if (!(level() instanceof ServerLevel sl)) return;
		for (int f : FLIPS) if (tickCount == f) com.rocraft.RbxSounds.play(this, com.rocraft.RbxSounds.get("bomb.tick")); // ticksound: clickfast.wav
		if (tickCount == FLIPS[FLIPS.length - 1] + 1) {
			Blast.at(sl, this, RADIUS, Float.MAX_VALUE, sl.damageSources().explosion(this, owner), "bomb.explode"); // Rocket shot.wav
			discard();
		}
	}

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
	@Override public boolean hurtServer(ServerLevel level, DamageSource src, float amount) { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
}
