package com.rocraft.tools;

import com.rocraft.RbxSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A Roblox Explosion: the blast puff, its own sound (no Minecraft boom on top), characters inside BlastRadius take
 * `damage` (Float.MAX_VALUE = joints broken), everything gets pushed away from the centre. Terrain is untouched.
 */
final class Blast {
	static void at(ServerLevel sl, Entity source, double radius, float damage, DamageSource src, String sound) {
		// Roblox Explosion: fireball core + dark smoke, sized to the blast
		sl.sendParticles(com.rocraft.RbxParticles.EXPLOSION, source.getX(), source.getY(), source.getZ(), 6, radius * 0.25, radius * 0.25, radius * 0.25, 0);
		sl.sendParticles(com.rocraft.RbxParticles.EXPLOSION_SMOKE, source.getX(), source.getY(), source.getZ(), 8, radius * 0.35, radius * 0.3, radius * 0.35, 0.02);
		sl.sendParticles(com.rocraft.RbxParticles.SPARK, source.getX(), source.getY(), source.getZ(), 16, 0.2, 0.2, 0.2, 0.25);
		RbxSounds.play(source, RbxSounds.get(sound));
		for (Entity e : sl.getEntities(source, source.getBoundingBox().inflate(radius), e -> e.distanceTo(source) <= radius)) {
			var push = e.position().subtract(source.position()).normalize().scale(0.8 * (1 - e.distanceTo(source) / radius) + 0.2);
			e.push(push.x, push.y + 0.3, push.z);
			e.hurtMarked = true;
			if (e instanceof LivingEntity le) le.hurtServer(sl, src, damage);
		}
	}
}
