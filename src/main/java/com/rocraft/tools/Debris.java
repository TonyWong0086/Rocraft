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

/**
 * A block knocked loose by a Roblox explosion: like a brick whose joints broke, it flies off with the blast, falls with
 * Roblox gravity, bounces and slides to a stop. After resting 5 s it turns into the block's normal item drops.
 * Rendered as a falling block (tumbling while it moves, see DebrisRenderer).
 */
public final class Debris extends FallingBlockEntity {
	static final int IDLE_TICKS = 5 * 20;
	private int idle;
	/** Client: tumble angle (degrees) while it flies, settling flat on a face once it stops. */
	public float spin, spinO;

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
		// a little bounce off whatever stopped it, ground friction, air drag
		double vy = verticalCollision && want.y < -0.15 ? -want.y * 0.3 : got.y;
		double fx = onGround() ? 0.75 : 0.99;
		double vx = horizontalCollision && Math.abs(got.x) < Math.abs(want.x) * 0.5 ? -want.x * 0.25 : got.x * fx;
		double vz = horizontalCollision && Math.abs(got.z) < Math.abs(want.z) * 0.5 ? -want.z * 0.25 : got.z * fx;
		setDeltaMovement(vx, vy, vz);
		spinO = spin;
		double speed = got.length();
		spin += speed > 0.03 ? (float) speed * 70 : (Math.round(spin / 90) * 90 - spin) * 0.35f;
		if (level() instanceof ServerLevel sl) {
			idle = onGround() && got.lengthSqr() < 0.0025 ? idle + 1 : 0;
			if (idle >= IDLE_TICKS || tickCount > 60 * 20 || getY() < sl.getMinY() - 64) {
				if (getY() >= sl.getMinY()) Block.dropResources(getBlockState(), sl, blockPosition());
				discard();
			}
		}
	}
}
