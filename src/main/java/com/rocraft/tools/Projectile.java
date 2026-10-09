package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
import net.minecraft.world.phys.Vec3;

/**
 * Classic Roblox projectiles, numbers from their scripts (Roblox damage x0.2 for Minecraft health):
 * ROCKET (RocketLauncher.Server/Rocket): 60 studs/s, no gravity, explodes on touch, radius 8, damage 60, carries Fire (Heat 5, Size 2).
 * SUPERBALL (CannonScript/CannonBall): 2-stud ball, 200 studs/s, Elasticity 1, Friction 0, damage 25 halving on each
 *   non-character hit, sound at most every 0.1 s, gone after 5 s.
 * PELLET (Slingshot/PelletScript): 1-stud Bright red ball, 85 studs/s, damage 8 halving; gone below 1 or after 2 s.
 */
public final class Projectile extends Entity {
	public static final int ROCKET = 0, SUPERBALL = 1, PELLET = 2;
	static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Projectile.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Projectile.class, EntityDataSerializers.INT);
	private Player owner;
	private float damage;
	private long lastBoing;

	public Projectile(EntityType<? extends Projectile> type, Level level) { super(type, level); }

	static Projectile spawn(ServerLevel level, Player owner, int kind, Vec3 pos, Vec3 velocityStudsPerSec, int color) {
		var p = new Projectile(Tools.PROJECTILE, level);
		p.entityData.set(KIND, kind);
		p.entityData.set(COLOR, color);
		p.owner = owner;
		p.damage = kind == ROCKET ? 60 : kind == SUPERBALL ? 25 : 8;
		p.setPos(pos);
		p.setDeltaMovement(velocityStudsPerSec.scale(McFrame.STUD / 20));
		level.addFreshEntity(p);
		return p;
	}

	public int kind() { return entityData.get(KIND); }
	public int color() { return entityData.get(COLOR); }

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(KIND, SUPERBALL); b.define(COLOR, -1); }
	@Override protected double getDefaultGravity() { return kind() == ROCKET ? 0 : McFrame.GRAVITY; }

	@Override
	public void tick() {
		super.tick();
		int kind = kind();
		if (tickCount > (kind == ROCKET ? 600 : kind == SUPERBALL ? 100 : 40)) { discard(); return; } // Debris / lifetime
		applyGravity();
		Vec3 want = getDeltaMovement();
		move(MoverType.SELF, want);
		Vec3 got = getDeltaMovement();
		boolean hitBlock = horizontalCollision || verticalCollision;
		if (level().isClientSide()) return; // the rocket's Fire is emitted client side (GearEffects)
		var sl = (ServerLevel) level();
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.15), e -> e != owner && e.isAlive())) {
			if (kind == ROCKET) { explode(sl); return; }
			if (touch(sl)) e.hurtServer(sl, sl.damageSources().thrown(this, owner), damage * 0.2f);
		}
		if (!hitBlock) return;
		if (kind == ROCKET) { explode(sl); return; }
		if (touch(sl)) { damage /= 2; if (kind == PELLET && damage < 1) { discard(); return; } }
		// Elasticity 1 / Friction 0: bounce back the blocked components
		double vx = Math.abs(got.x) < Math.abs(want.x) * 0.5 ? -want.x : got.x;
		double vz = Math.abs(got.z) < Math.abs(want.z) * 0.5 ? -want.z : got.z;
		double vy = verticalCollision ? -want.y * (kind == SUPERBALL ? 1 : 0.4) : got.y;
		setDeltaMovement(vx, vy, vz);
	}

	/** Touched debounce (CannonBall: 0.1 s) + its sound; false while debounced. */
	private boolean touch(ServerLevel sl) {
		if (tickCount - lastBoing < 2) return false;
		lastBoing = tickCount;
		if (kind() == SUPERBALL) com.rocraft.RbxSounds.play(this, com.rocraft.RbxSounds.get("superball.boing"));
		return true;
	}

	/** Rocket: Explosion BlastRadius 8, BLAST_DAMAGE 60 to each character once; the bricks around it break loose. */
	private void explode(ServerLevel sl) {
		double r = 8 * McFrame.STUD;
		Blast.at(sl, this, r, 60 * 0.2f, sl.damageSources().explosion(this, owner), "rocket_launcher.boom"); // Boom: collide.wav
		discard();
	}

	@Override public boolean hurtServer(ServerLevel level, DamageSource src, float amount) { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
}
