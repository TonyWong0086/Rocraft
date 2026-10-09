package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
 * ROCKET (RocketLauncher): 60 studs/s, no gravity, carries Fire (Heat 5, Size 2); explodes on touching anything,
 *   its own launcher included (once clear of them), or when another projectile hits it: radius 8, damage 60.
 * SUPERBALL (CannonScript/CannonBall): 2-stud ball, 200 studs/s, Elasticity 1, Friction 0, damage 25 halving on each
 *   non-character hit, sound at most every 0.1 s, gone after 5 s.
 * PELLET (Slingshot/PelletScript): 1-stud Bright red ball, 85 studs/s, damage 8 halving on each non-character hit, gone
 *   below 1 or after 2 s. Hurts its own shooter too.
 * PAINTBALL (PaintballGun/Paintball): 1-stud ball of a random paint colour, 300 studs/s, BodyForce lifting 90 of the
 *   196.2 gravity; on its first touch it deals 2, paints the part it hit and splats 3 plates of paint. Gone after 8 s.
 */
public final class Projectile extends Entity {
	public static final int ROCKET = 0, SUPERBALL = 1, PELLET = 2, PAINTBALL = 3;
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
		p.damage = kind == ROCKET ? 60 : kind == SUPERBALL ? 25 : kind == PELLET ? 8 : 2;
		p.setPos(pos);
		p.setDeltaMovement(velocityStudsPerSec.scale(McFrame.STUD / 20));
		level.addFreshEntity(p);
		return p;
	}

	public int kind() { return entityData.get(KIND); }
	public int color() { return entityData.get(COLOR); }

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(KIND, SUPERBALL); b.define(COLOR, -1); }

	@Override protected double getDefaultGravity() {
		int k = kind();
		return k == ROCKET ? 0 : k == PAINTBALL ? McFrame.GRAVITY * (196.2 - 90) / 196.2 : McFrame.GRAVITY;
	}

	/** May this projectile touch e? The shooter is only reachable once the shot is clear of them. */
	private boolean canHit(Entity e) {
		if (e == this || !e.isAlive()) return false;
		if (e != owner) return true;
		int kind = kind();
		return (kind == ROCKET || kind == PELLET) && tickCount > 3;
	}

	@Override
	public void tick() {
		super.tick();
		int kind = kind();
		if (tickCount > (kind == ROCKET ? 600 : kind == SUPERBALL ? 100 : kind == PELLET ? 40 : 160)) { discard(); return; } // Debris / lifetime
		applyGravity();
		Vec3 want = getDeltaMovement();
		move(MoverType.SELF, want);
		Vec3 got = getDeltaMovement();
		boolean hitBlock = horizontalCollision || verticalCollision;
		if (level().isClientSide()) return; // the rocket's Fire is emitted client side (GearEffects)
		var sl = (ServerLevel) level();
		var box = getBoundingBox().inflate(0.15);
		// any other projectile touching a rocket primes it (pellets, superballs and paintballs can shoot rockets down)
		for (Projectile other : sl.getEntitiesOfClass(Projectile.class, box, o -> o != this && o.isAlive())) {
			if (kind == ROCKET) { explode(sl); return; }
			if (other.kind() == ROCKET) { other.explode(sl); if (kind == PAINTBALL) { splat(sl, null); return; } }
		}
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
			if (kind == ROCKET) { explode(sl); return; }
			if (kind == PAINTBALL) { e.hurtServer(sl, sl.damageSources().thrown(this, owner), damage * 0.2f); splat(sl, null); return; }
			if (touch(sl)) e.hurtServer(sl, sl.damageSources().thrown(this, owner), damage * 0.2f);
		}
		RobloxPart part = null;
		if (hitBlock || kind == PAINTBALL) for (RobloxPart pt : sl.getEntitiesOfClass(RobloxPart.class, box)) { part = pt; hitBlock = true; }
		if (!hitBlock) return;
		if (kind == ROCKET) { explode(sl); return; }
		if (kind == PAINTBALL) { splat(sl, part); return; }
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

	/**
	 * Paintball onTouched: the part it hit takes the paint colour (light parts only, mass < 240), then three 1 x 0.4 x 1
	 * plates of paint fly off at 15 studs/s in random directions (math.random(-1,1), (0,1), (-1,1)) and clean up after 120 s.
	 */
	private void splat(ServerLevel sl, RobloxPart hit) {
		int c = color();
		if (hit != null) hit.paint(c);
		var r = sl.getRandom();
		for (int i = 0; i < 3; i++) {
			Vec3 v = new Vec3(r.nextInt(3) - 1, r.nextInt(2), r.nextInt(3) - 1);
			var plate = RobloxPart.place(sl, position().add(v.scale(McFrame.STUD)), 1, 0.4f, 1, c, false, 120 * 20);
			plate.setDeltaMovement(v.scale(15 * McFrame.STUD / 20));
		}
		discard();
	}

	/** Rocket: Explosion BlastRadius 8, BLAST_DAMAGE 60 to each character once; the blocks around it break loose. */
	void explode(ServerLevel sl) {
		if (isRemoved()) return;
		double r = 8 * McFrame.STUD;
		Blast.at(sl, this, r, 60 * 0.2f, sl.damageSources().explosion(this, owner), "rocket_launcher.boom"); // Boom: collide.wav
		discard();
	}

	@Override public boolean hurtServer(ServerLevel level, DamageSource src, float amount) { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
}
