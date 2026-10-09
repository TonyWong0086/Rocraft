package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.sim.McFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rolling Hoverboard (SkateboardModule "Segway"): the tool drops the board on the ground toward the mouse; stand on it
 * (or click it) to ride. W/S throttle: +15 studs/s per s up to 35, reverse at 20/s, coasting loses 15% every 0.1 s and
 * stops under 2. A/D turn at up to 2 rad/s, damped by 0.5 rad/s every 0.1 s. Space ollies (2 s between ollies).
 * Sneak to step off, hit it to pick it back up (its PickUp button). The rider drives it client side, like a boat.
 */
public final class Hoverboard extends Entity {
	static final double MAX_SPEED = 35, ACCEL = 15, DECEL = 20, TURN_MAX = 2, TURN_DAMP = 0.5, OLLIE = 35;
	private double speed, turn; // studs/s, rad/s (+ = left, like BodyAngularVelocity about +Y)
	private int throttleWas, ollieAt = -100, mountBlockedUntil;
	private boolean wasOnGround = true;

	public Hoverboard(EntityType<? extends Hoverboard> type, Level level) { super(type, level); }

	/** The Tool: click drops the board in front of you, toward the mouse, and the tool is gone. */
	public static final class Board extends Item {
		public Board(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player p, InteractionHand hand) {
			if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;
			Vec3 to = Tools.mouse(p).subtract(p.position()).multiply(1, 0, 1);
			if (to.lengthSqr() < 1e-6) to = p.getLookAngle().multiply(1, 0, 1);
			to = to.normalize();
			// (Torso.Size.Z / 2 + SkateboardSize.Z / 2) * 1.5 in front of the torso, dropped onto the highest ground below
			Vec3 at = p.position().add(to.scale((0.5 + 0.8) * 1.5 * McFrame.STUD)).add(0, 3 * McFrame.STUD, 0);
			var hit = sl.clip(new ClipContext(at, at.add(0, -8 * McFrame.STUD, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() == HitResult.Type.MISS) return InteractionResult.FAIL;
			var b = new Hoverboard(Tools.HOVERBOARD_ENTITY, sl);
			b.setPos(hit.getLocation());
			b.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
			if (!sl.noCollision(b)) return InteractionResult.FAIL; // the area has to be empty
			sl.addFreshEntity(b);
			RbxSounds.play(p, RbxSounds.get("hoverboard.drop"));
			p.getItemInHand(hand).shrink(1);
			return InteractionResult.CONSUME;
		}
	}

	@Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {}

	@Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }

	@Override protected double getDefaultGravity() { return McFrame.GRAVITY; }

	@Override public float maxUpStep() { return getControllingPassenger() != null ? 1.0f : 0; } // Minecraft's 1-block terrain

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && getPassengers().isEmpty() && tickCount >= mountBlockedUntil) // SkateboardPlatform: step on to ride
			for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().expandTowards(0, 0.3, 0)))
				if (!p.isShiftKeyDown() && !p.isPassenger() && p.getY() >= getBoundingBox().maxY - 0.1) { p.startRiding(this); break; }
		if (!isLocalInstanceAuthoritative()) return;
		double dt = 0.05;
		var rider = getControllingPassenger();
		int throttle = rider == null ? 0 : (int) Math.signum(rider.zza), steer = rider == null ? 0 : (int) Math.signum(rider.xxa);
		if (throttle > 0) speed = Math.min(MAX_SPEED, speed + ACCEL * dt);
		else if (throttle < 0) speed = Math.max(-MAX_SPEED, speed - DECEL * dt);
		else { speed *= Math.pow(0.85, dt / 0.1); if (Math.abs(speed) <= 2) speed = 0; }
		if (throttle == 0 && throttleWas != 0 && Math.abs(speed) >= 10) sound(rider, "hoverboard.stop");
		throttleWas = throttle;
		if (steer != 0) turn = steer * TURN_MAX;
		else turn = Math.abs(turn) > TURN_DAMP * dt / 0.1 ? turn - Math.signum(turn) * TURN_DAMP * dt / 0.1 : 0;
		setYRot(getYRot() - (float) Math.toDegrees(turn * dt));

		applyGravity();
		double vy = getDeltaMovement().y;
		if (rider != null && rider.isJumping() && onGround() && tickCount - ollieAt > 40) { vy = OLLIE * McFrame.STUD / 20; ollieAt = tickCount; sound(rider, "hoverboard.ollie"); }
		double yaw = Math.toRadians(getYRot()), v = speed * McFrame.STUD / 20;
		setDeltaMovement(-Math.sin(yaw) * v, vy, Math.cos(yaw) * v);
		move(MoverType.SELF, getDeltaMovement());
		if (horizontalCollision && Math.abs(speed) > 5) speed -= Math.signum(speed) * 3.5; // ThrustUpdater: losing speed against a wall
		if (onGround() && !wasOnGround) sound(rider, "hoverboard.land");
		wasOnGround = onGround();
	}

	private void sound(LivingEntity rider, String name) {
		SoundEvent s = RbxSounds.get(name);
		if (s != null) level().playSound(rider instanceof Player p ? p : null, getX(), getY(), getZ(), s, SoundSource.PLAYERS, 0.6f, 1f);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction move) {
		super.positionRider(passenger, move);
		passenger.setYRot(getYRot()); // the rider stands facing the way the board rolls
		passenger.setYHeadRot(getYRot());
		if (passenger instanceof LivingEntity l) l.setYBodyRot(getYRot());
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		mountBlockedUntil = tickCount + 20; // don't climb straight back on after stepping off
	}

	@Override
	public InteractionResult interact(Player p, InteractionHand hand, Vec3 location) {
		if (!level().isClientSide() && getPassengers().isEmpty()) p.startRiding(this);
		return InteractionResult.SUCCESS;
	}

	/** Hitting the board picks it up (the board's PickUp button): the tool goes back into the backpack. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource src, float amount) {
		if (isRemoved() || !(src.getEntity() instanceof Player p)) return false;
		ejectPassengers();
		if (!p.isCreative() && !p.getInventory().add(new ItemStack(Tools.HOVERBOARD))) spawnAtLocation(level, new ItemStack(Tools.HOVERBOARD));
		discard();
		return true;
	}

	@Override public boolean canBeCollidedWith(Entity other) { return true; }
	@Override public boolean isPickable() { return !isRemoved(); }
	@Override public boolean isPushable() { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
}
