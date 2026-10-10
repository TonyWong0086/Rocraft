package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.sim.McFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Roblox Explosion: the Roblox blast effect (built client side from one rbx_explosion carrying the radius), its own
 * sound (no Minecraft boom), characters inside BlastRadius take `damage` (Float.MAX_VALUE = joints broken), everything
 * is pushed away (Parts lose their joints), and every block inside the radius breaks loose and flies off as physics debris (tools.Debris).
 */
final class Blast {
	static void at(ServerLevel sl, Entity source, double radius, float damage, DamageSource src, String sound) {
		Vec3 c = source.position();
		sl.sendParticles(com.rocraft.RbxParticles.EXPLOSION, c.x, c.y, c.z, 0, radius / McFrame.STUD, 0, 0, 1);
		RbxSounds.play(source, RbxSounds.get(sound), 8f); // heard ~128 blocks away, like Roblox's long rolloff
		for (Entity e : sl.getEntities(source, source.getBoundingBox().inflate(radius), e -> e.distanceTo(source) <= radius)) {
			var push = e.position().subtract(c).normalize().scale(0.8 * (1 - e.distanceTo(source) / radius) + 0.2);
			if (e instanceof RobloxPart part) { part.breakJoints(part.getDeltaMovement().add(push.x, push.y + 0.3, push.z)); continue; }
			e.push(push.x, push.y + 0.3, push.z);
			e.hurtMarked = true;
			if (e instanceof LivingEntity le) le.hurtServer(sl, src, damage);
		}
		breakBlocks(sl, c, radius);
	}

	/**
	 * Blocks whose centre is inside the blast break loose and are thrown straight away from it, like Roblox's
	 * BlastPressure throws loose bricks: ~120 studs/s at the centre down to ~45 at the edge. Blocks under the blast
	 * would be driven into the ground, so they rebound out of the crater in a cone: each one's angle is the flattest
	 * (from 40 degrees, steeper the deeper it sat) whose flight actually clears the crater walls around it.
	 */
	static void breakBlocks(ServerLevel sl, Vec3 c, double radius) {
		int r = (int) Math.ceil(radius);
		BlockPos base = BlockPos.containing(c);
		var rand = sl.getRandom();
		var loose = new java.util.ArrayList<BlockPos>();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -r, -r), base.offset(r, r, r))) {
			if (Vec3.atCenterOf(pos).distanceTo(c) > radius) continue;
			var state = sl.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) continue; // fluids stay, containers keep their contents
			if (state.getDestroySpeed(sl, pos) < 0 || state.getBlock().getExplosionResistance() >= 1200) continue; // bedrock, obsidian...
			loose.add(pos.immutable());
		}
		var states = loose.stream().map(sl::getBlockState).toList();
		for (var pos : loose) sl.removeBlock(pos, false); // the crater first, so each throw is aimed out of the real hole
		for (int i = 0; i < loose.size(); i++) {
			var pos = loose.get(i);
			Vec3 v = throwVelocity(Vec3.atCenterOf(pos).subtract(c), radius, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(),
				box -> !sl.noCollision(box.move(c)));
			Debris.launch(sl, pos, states.get(i), v);
		}
	}

	/**
	 * Launch velocity (blocks/tick) of a block whose centre is at `off` from the blast centre. r1..r3 are uniform
	 * randoms in [0, 1); `solid` says whether a box (relative to the blast centre) hits terrain.
	 */
	static Vec3 throwVelocity(Vec3 off, double radius, double r1, double r2, double r3, java.util.function.Predicate<AABB> solid) {
		double d = off.length();
		double speed = (45 + 75 * Math.max(0, 1 - d / radius)) * (0.85 + 0.3 * r1) * McFrame.STUD / 20;
		Vec3 dir = d < 1e-3 ? new Vec3(0, -1, 0) : off.scale(1 / d);
		if (dir.y >= 0) return dir.scale(speed);
		// into the ground: it rebounds out of the crater along its outward heading (any heading if it was right below)
		double h = Math.hypot(dir.x, dir.z), a = r2 * Math.PI * 2;
		Vec3 flat = h > 0.3 ? new Vec3(dir.x / h, 0, dir.z / h) : new Vec3(Math.cos(a), 0, Math.sin(a));
		double first = 40 + 28 * -dir.y + 6 * (r3 - 0.5);
		Vec3 best = null;
		for (double mul = 1; mul <= 1.61 && best == null; mul += 0.3)
			for (double deg = first; deg <= 86 && best == null; deg += 4) {
				double e = Math.toRadians(deg);
				Vec3 u = new Vec3(flat.x * Math.cos(e), Math.sin(e), flat.z * Math.cos(e));
				// at least fast enough to rise from its bottom to a little over the blast centre
				Vec3 v = u.scale(mul * Math.max(speed, Math.sqrt(2 * McFrame.GRAVITY * (0.8 - off.y)) * 1.15 / u.y));
				if (clears(off, v, radius, solid)) best = v;
			}
		return best != null ? best : new Vec3(flat.x * 0.3, 1, flat.z * 0.3).normalize().scale(speed * 1.6); // boxed in: up the wall and out
	}

	/**
	 * Does a debris box launched from `off` with velocity v get past the crater (radius + half a block out, or 12 ticks)?
	 * Moves like Debris: gravity then move, one axis at a time (y first, as Minecraft collides); while rising it scrapes
	 * up a wall it's pushed against, anything else that touches terrain doesn't clear.
	 */
	static boolean clears(Vec3 off, Vec3 v, double radius, java.util.function.Predicate<AABB> solid) {
		var box = new AABB(off.x - 0.49, off.y - 0.5, off.z - 0.49, off.x + 0.49, off.y + 0.48, off.z + 0.49).deflate(0.01);
		double vx = v.x, vy = v.y, vz = v.z;
		for (int t = 0; t < 12; t++) {
			vy -= McFrame.GRAVITY;
			for (int s = 0; s < 3; s++) {
				var next = box.move(0, vy / 3, 0);
				if (solid.test(next)) return false;
				box = next;
				next = box.move(vx / 3, 0, 0);
				if (!solid.test(next)) box = next; else if (vy <= 0) return false;
				next = box.move(0, 0, vz / 3);
				if (!solid.test(next)) box = next; else if (vy <= 0) return false;
			}
			var mid = box.getCenter();
			if (Math.hypot(mid.x, mid.z) > radius + 0.5 && box.minY > -0.2) return true;
		}
		return true;
	}

	/**
	 * Self-check on a flat field (ground top at y 0, the blast on it): every block of the crater is thrown out of the
	 * hole along a real flight path, none straight up unless it was dead centre, and they land well spread out.
	 */
	public static void main(String[] a) {
		for (double radius : new double[]{8 * McFrame.STUD, 12 * McFrame.STUD}) {
			var rnd = new java.util.Random(1);
			Vec3 c = new Vec3(0.5, 0, 0.5); // a block-centre column, standing on the ground
			int r = (int) Math.ceil(radius) + 1;
			var removed = new java.util.HashSet<BlockPos>();
			for (BlockPos pos : BlockPos.betweenClosed(-r, -r, -r, r, -1, r))
				if (Vec3.atCenterOf(pos).distanceTo(c) <= radius) removed.add(pos.immutable());
			java.util.function.Predicate<AABB> solid = box -> {
				var b = box.move(c);
				for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ)))
					if (pos.getY() < 0 && !removed.contains(pos)) return true;
				return false;
			};
			var landed = new java.util.ArrayList<Double>();
			int up = 0;
			for (var pos : removed) {
				Vec3 off = Vec3.atCenterOf(pos).subtract(c);
				Vec3 v = throwVelocity(off, radius, rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), solid);
				if (v.y < 0) throw new AssertionError("thrown into the ground " + off + " -> " + v);
				if (Math.hypot(v.x, v.z) < 0.3 * v.length()) up++;
				if (!clears(off, v, radius, solid)) throw new AssertionError("hits the crater wall " + off + " -> " + v);
				double t = (v.y + Math.sqrt(v.y * v.y + 2 * McFrame.GRAVITY * (off.y - 0.5))) / McFrame.GRAVITY; // back down to y 0
				landed.add(Math.hypot(off.x + v.x * t, off.z + v.z * t));
			}
			java.util.Collections.sort(landed);
			System.out.printf("radius %.2f: %d blocks, %d near-vertical, landing distance min %.1f median %.1f max %.1f%n",
				radius, removed.size(), up, landed.get(0), landed.get(landed.size() / 2), landed.get(landed.size() - 1));
			if (up > removed.size() / 10) throw new AssertionError("too many thrown straight up");
			if (landed.get(0) < radius + 0.5) throw new AssertionError("lands back in the crater");
			if (landed.get(landed.size() / 2) < 2.5 * radius) throw new AssertionError("doesn't fly far enough");
		}
		System.out.println("ok");
	}
}
