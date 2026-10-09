package com.rocraft.tools;

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
 * A Roblox Part: Instance.new("Part"), the default 4 x 1.2 x 2 stud brick with studs on top and inlets underneath,
 * in a BrickColor. Solid (characters stand on it). It stays where it was built, jointed to its neighbours, until an
 * explosion breaks its joints; then it falls, bounces and settles with Roblox gravity. The long side runs along X
 * or Z. Creative players remove one by hitting it.
 */
public final class RobloxPart extends Entity {
	static final double W = 4 * McFrame.STUD, H = 1.2 * McFrame.STUD, D = 2 * McFrame.STUD;
	static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Boolean> ALONG_X = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.BOOLEAN);
	static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(RobloxPart.class, EntityDataSerializers.BOOLEAN);

	public RobloxPart(EntityType<? extends RobloxPart> type, Level level) { super(type, level); }

	/** A brick whose bottom centre is at pos. */
	static RobloxPart place(ServerLevel level, Vec3 pos, boolean alongX, int argb) {
		var p = new RobloxPart(Tools.PART, level);
		p.entityData.set(COLOR, argb);
		p.entityData.set(ALONG_X, alongX);
		p.setPos(pos);
		level.addFreshEntity(p);
		return p;
	}

	public int color() { return entityData.get(COLOR); }
	public boolean alongX() { return entityData != null && entityData.get(ALONG_X); }
	public boolean anchored() { return entityData.get(ANCHORED); }

	/** Explosion: joints broken, flung with the blast (blocks/tick). */
	void breakJoints(Vec3 velocity) {
		entityData.set(ANCHORED, false);
		setDeltaMovement(velocity);
		hurtMarked = true;
	}

	@Override protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(COLOR, 0xFFA3A2A5); // Medium stone grey, the default BrickColor
		b.define(ALONG_X, true);
		b.define(ANCHORED, true);
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		double hx = (alongX() ? W : D) / 2, hz = (alongX() ? D : W) / 2;
		return new AABB(pos.x - hx, pos.y, pos.z - hz, pos.x + hx, pos.y + H, pos.z + hz);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ALONG_X.equals(key)) setBoundingBox(makeBoundingBox());
	}

	@Override protected double getDefaultGravity() { return McFrame.GRAVITY; }

	@Override
	public void tick() {
		super.tick();
		if (anchored()) { setDeltaMovement(Vec3.ZERO); return; }
		applyGravity();
		Vec3 want = getDeltaMovement();
		move(MoverType.SELF, want);
		Vec3 got = getDeltaMovement();
		double vy = verticalCollision && want.y < -0.15 ? -want.y * 0.3 : got.y;
		double f = onGround() ? 0.7 : 0.99;
		setDeltaMovement(horizontalCollision ? -got.x * 0.2 : got.x * f, vy, horizontalCollision ? -got.z * 0.2 : got.z * f);
		if (getY() < level().getMinY() - 64) discard(); // fell out of the world (Workspace.FallenPartsDestroyHeight)
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
		entityData.set(ALONG_X, in.getBooleanOr("along_x", true));
		entityData.set(ANCHORED, in.getBooleanOr("anchored", true));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		out.putInt("color", color());
		out.putBoolean("along_x", alongX());
		out.putBoolean("anchored", anchored());
	}
}
