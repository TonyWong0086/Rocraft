package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.sim.McFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Roblox Explosion: the Roblox blast effect (built client side from one rbx_explosion carrying the radius), its own
 * sound (no Minecraft boom), characters inside BlastRadius take `damage` (Float.MAX_VALUE = joints broken), everything
 * is pushed away, and every brick inside the radius breaks loose and flies off as physics debris (tools.Debris).
 */
final class Blast {
	static void at(ServerLevel sl, Entity source, double radius, float damage, DamageSource src, String sound) {
		Vec3 c = source.position();
		sl.sendParticles(com.rocraft.RbxParticles.EXPLOSION, c.x, c.y, c.z, 0, radius / McFrame.STUD, 0, 0, 1);
		RbxSounds.play(source, RbxSounds.get(sound));
		for (Entity e : sl.getEntities(source, source.getBoundingBox().inflate(radius), e -> e.distanceTo(source) <= radius)) {
			var push = e.position().subtract(c).normalize().scale(0.8 * (1 - e.distanceTo(source) / radius) + 0.2);
			e.push(push.x, push.y + 0.3, push.z);
			e.hurtMarked = true;
			if (e instanceof LivingEntity le) le.hurtServer(sl, src, damage);
		}
		breakBlocks(sl, c, radius);
	}

	/** Blocks whose centre is inside the blast fly outward; the closer to the centre, the harder. */
	static void breakBlocks(ServerLevel sl, Vec3 c, double radius) {
		int r = (int) Math.ceil(radius);
		BlockPos base = BlockPos.containing(c);
		var rand = sl.getRandom();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -r, -r), base.offset(r, r, r))) {
			Vec3 mid = Vec3.atCenterOf(pos);
			double d = mid.distanceTo(c);
			if (d > radius) continue;
			var state = sl.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) continue; // fluids stay, containers keep their contents
			if (state.getDestroySpeed(sl, pos) < 0 || state.getBlock().getExplosionResistance() >= 1200) continue; // bedrock, obsidian...
			Vec3 dir = d < 1e-3 ? new Vec3(0, 1, 0) : mid.subtract(c).scale(1 / d);
			double strength = 0.25 + 0.75 * (1 - d / radius);
			Vec3 v = dir.scale(strength).add((rand.nextDouble() - 0.5) * 0.2, 0.25 + rand.nextDouble() * 0.2, (rand.nextDouble() - 0.5) * 0.2);
			Debris.launch(sl, pos.immutable(), state, v);
		}
	}
}
