package com.rocraft.tools;

import org.joml.Quaternionf;

import com.rocraft.sim.McFrame;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Roblox Part: a box of any stud size (Instance.new("Part") is 4 x 1.2 x 2) in a BrickColor, studs on top and
 * inlets underneath. Solid (characters stand on it). Anchored ones stay put, jointed to their neighbours, until an
 * explosion breaks the joints; unanchored ones fall, bounce and settle with Roblox gravity. Optional lifetime
 * (BrickCleanup / Debris). Creative players remove one by hitting it.
 */
public final class RobloxPart extends Entity {
	static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.INT);
	/** Size in studs along world X, Y, Z. */
	static final EntityDataAccessor<org.joml.Vector3fc> SIZE = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.VECTOR3);
	static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.BOOLEAN);

	public RobloxPart(EntityType<? extends RobloxPart> type, Level level) { super(type, level); }

	private int lifetime = -1; // ticks, -1 = forever
	/** Client: tumbling once a blast throws it (PartRenderer turns it about its centre). */
	public final Debris.Tumble tumble = new Debris.Tumble();

	/** A part whose bottom centre is at pos; size in studs (world X, Y, Z). */
	static RobloxPart place(ServerLevel level, Vec3 pos, float sx, float sy, float sz, int argb, boolean anchored, int lifetimeTicks) {
		var p = new RobloxPart(Tools.PART, level);
		p.entityData.set(COLOR, argb);
		p.entityData.set(SIZE, new org.joml.Vector3f(sx, sy, sz));
		p.entityData.set(ANCHORED, anchored);
		p.lifetime = lifetimeTicks;
		p.setPos(pos);
		level.addFreshEntity(p);
		return p;
	}

	public int color() { return entityData.get(COLOR); }
	public void paint(int argb) { entityData.set(COLOR, argb); }
	public org.joml.Vector3fc size() { return entityData == null ? new org.joml.Vector3f(4, 1.2f, 2) : entityData.get(SIZE); }
	public boolean anchored() { return entityData.get(ANCHORED); }

	/** Explosion: joints broken, flung with the blast (blocks/tick). */
	void breakJoints(Vec3 velocity) {
		entityData.set(ANCHORED, false);
		setDeltaMovement(velocity);
		hurtMarked = true;
	}

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(COLOR, 0xFFA3A2A5); // Medium stone grey, the default BrickColor
		b.define(SIZE, new org.joml.Vector3f(4, 1.2f, 2));
		b.define(ANCHORED, true);
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		var s = size();
		double k = McFrame.STUD, hx = s.x() * k / 2, hz = s.z() * k / 2;
		return new AABB(pos.x - hx, pos.y, pos.z - hz, pos.x + hx, pos.y + s.y() * k, pos.z + hz);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (SIZE.equals(key)) setBoundingBox(makeBoundingBox());
	}

	@Override protected double getDefaultGravity() { return McFrame.GRAVITY; }

	@Override
	public void tick() {
		super.tick();
		if (lifetime >= 0 && tickCount > lifetime && !level().isClientSide()) { discard(); return; }
		if (anchored()) { setDeltaMovement(Vec3.ZERO); return; }
		applyGravity();
		Vec3 want = getDeltaMovement();
		move(MoverType.SELF, want);
		Vec3 v = Debris.collide(want, getDeltaMovement(), horizontalCollision, verticalCollision); // same physics as blasted blocks
		setDeltaMovement(v);
		if (level().isClientSide()) tumble.step(getId(), v, verticalCollision && want.y < 0, onGround(), RobloxPart::lieFlat);
		if (getY() < level().getMinY() - 64) discard(); // fell out of the world (Workspace.FallenPartsDestroyHeight)
	}

	/**
	 * Resting orientation: the nearest of the four that keep its box lined up with its collision box (as placed,
	 * upside down either way, or turned half round), so what you see is what you stand on.
	 */
	static Quaternionf lieFlat(Quaternionf q) {
		Quaternionf best = null;
		float bestDot = -1;
		for (var c : new Quaternionf[]{new Quaternionf(), new Quaternionf(1, 0, 0, 0), new Quaternionf(0, 1, 0, 0), new Quaternionf(0, 0, 1, 0)}) {
			float d = Math.abs(c.dot(q));
			if (d > bestDot) { bestDot = d; best = c; }
		}
		return best;
	}

	@Override public boolean canBeCollidedWith(Entity other) { return true; }
	@Override public boolean isPickable() { return !isRemoved(); }
	@Override public boolean isPushable() { return false; }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource src, float amount) {
		if (src.getEntity() instanceof Player p && p.isCreative()) discard();
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		entityData.set(COLOR, in.getIntOr("color", 0xFFA3A2A5));
		boolean alongX = in.getBooleanOr("along_x", true); // parts saved before sizes existed were 4 x 1.2 x 2 bricks
		entityData.set(SIZE, new org.joml.Vector3f(in.getFloatOr("sx", alongX ? 4 : 2), in.getFloatOr("sy", 1.2f), in.getFloatOr("sz", alongX ? 2 : 4)));
		lifetime = in.getIntOr("lifetime", -1);
		entityData.set(ANCHORED, in.getBooleanOr("anchored", true));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		out.putInt("color", color());
		var s = size();
		out.putFloat("sx", s.x());
		out.putFloat("sy", s.y());
		out.putFloat("sz", s.z());
		if (lifetime >= 0) out.putInt("lifetime", Math.max(0, lifetime - tickCount));
		out.putBoolean("anchored", anchored());
	}
}
