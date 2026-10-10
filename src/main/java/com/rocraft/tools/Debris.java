package com.rocraft.tools;

import com.rocraft.mixin.FallingBlockAccessor;
import com.rocraft.sim.McFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A block knocked loose by a Roblox explosion: like a brick whose joints broke, it flies off with the blast, falls with
 * Roblox gravity, and tumbles end over end. Landing it bounces (elasticity), loses speed to friction (Plastic, 0.3)
 * and tips over its edges until it settles flat on a face. After resting 5 s it turns into the block's normal drops.
 * Rendered as a falling block turned by `rot` (DebrisRenderer).
 */
public final class Debris extends FallingBlockEntity {
	static final int IDLE_TICKS = 5 * 20;
	static final double FRICTION = 0.3, ELASTICITY = 0.3;
	private int idle;
	/** Client: orientation (and last tick's, for interpolation) and angular velocity in radians/tick. */
	public final Quaternionf rot = new Quaternionf(), rotO = new Quaternionf();
	private Vector3f spin;

	public Debris(EntityType<? extends Debris> type, Level level) { super(type, level); }

	/** Replace the block at pos with flying debris pushed by velocity (blocks/tick). */
	static void launch(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity) {
		var d = new Debris(Tools.DEBRIS, level);
		((FallingBlockAccessor) (Object) d).rocraft$setBlockState(state);
		d.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		d.setDeltaMovement(velocity);
		d.setStartPos(pos);
		level.removeBlock(pos, false);
		level.addFreshEntity(d);
	}

	@Override protected double getDefaultGravity() { return McFrame.GRAVITY; }

	@Override
	public void tick() {
		baseTick(); // not FallingBlockEntity.tick: debris never places itself back
		if (getBlockState().isAir()) { discard(); return; }
		applyGravity();
		Vec3 want = getDeltaMovement();
		move(MoverType.SELF, want);
		Vec3 got = getDeltaMovement();
		double vx = got.x, vy = got.y, vz = got.z;
		// walls: bounce back off whichever side stopped it, except while still rising out of the blast, when it keeps
		// pushing outward and scrapes up the crater wall and over the rim
		if (horizontalCollision) {
			if (Math.abs(got.x) < Math.abs(want.x) * 0.5) vx = want.y > 0 ? want.x : -want.x * ELASTICITY;
			if (Math.abs(got.z) < Math.abs(want.z) * 0.5) vz = want.y > 0 ? want.z : -want.z * ELASTICITY;
		}
		boolean landed = verticalCollision && want.y < 0;
		if (landed) {
			double impact = -want.y;
			vy = impact > 2.5 * McFrame.GRAVITY ? impact * ELASTICITY : 0; // resting contact doesn't bounce
			// Coulomb friction: the impact (and the weight of a resting block) take away up to FRICTION x that much sliding speed
			double h = Math.hypot(vx, vz), loss = FRICTION * (impact + vy);
			double k = h > loss ? (h - loss) / h : 0;
			vx *= k; vz *= k;
		} else if (verticalCollision) vy = -want.y * ELASTICITY; // ceiling
		setDeltaMovement(vx, vy, vz);
		if (level().isClientSide()) tumble(landed, vx, vz);
		else if (level() instanceof ServerLevel sl) {
			idle = onGround() && got.lengthSqr() < 0.0025 ? idle + 1 : 0;
			if (idle >= IDLE_TICKS || tickCount > 60 * 20 || getY() < sl.getMinY() - 64) {
				if (getY() >= sl.getMinY()) Block.dropResources(getBlockState(), sl, blockPosition());
				discard();
			}
		}
	}

	/**
	 * Client: free tumble in the air about a random axis (faster the harder it was thrown); on the ground the spin turns
	 * into tipping over in the direction it slides, and once it stops it rocks down flat onto its nearest face.
	 */
	private void tumble(boolean landed, double vx, double vz) {
		rotO.set(rot);
		var rnd = spin == null ? new java.util.Random(getId()) : null;
		if (rnd != null) spin = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize()
			.mul((float) (0.15 + getDeltaMovement().length() * 0.35));
		double h = Math.hypot(vx, vz);
		if (landed) {
			// rolling over its edges: axis up x velocity, rate speed / half size
			var roll = new Vector3f((float) vz, 0, (float) -vx).mul(2);
			spin.lerp(roll, 0.6f);
		}
		if (onGround() && h < 0.02) {
			spin.zero();
			rot.slerp(nearestFace(rot), 0.35f);
		} else if (spin.lengthSquared() > 1e-8f) {
			float a = spin.length();
			rot.premul(new Quaternionf().fromAxisAngleRad(spin.x / a, spin.y / a, spin.z / a, a)).normalize();
		}
	}

	/** The axis-aligned orientation (one of the cube's 24) closest to q: the face it falls flat onto. */
	static Quaternionf nearestFace(Quaternionf q) {
		var m = new Matrix3f().rotation(q);
		Vector3f x = axis(m.getColumn(0, new Vector3f())), y = m.getColumn(1, new Vector3f());
		y = axis(y.sub(new Vector3f(x).mul(y.dot(x))));
		return new Matrix3f(x, y, new Vector3f(x).cross(y)).getNormalizedRotation(new Quaternionf());
	}

	private static Vector3f axis(Vector3f v) {
		float ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
		return ax >= ay && ax >= az ? new Vector3f(Math.signum(v.x), 0, 0) : ay >= az ? new Vector3f(0, Math.signum(v.y), 0) : new Vector3f(0, 0, Math.signum(v.z));
	}
}
