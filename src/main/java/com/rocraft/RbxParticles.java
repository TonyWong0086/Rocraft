package com.rocraft;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Roblox particle effects (textures decoded from the user's Roblox install into the private pack). */
public final class RbxParticles {
	public static final SimpleParticleType SMOKE = reg("rbx_smoke"), FIRE = reg("rbx_fire"), SPARK = reg("rbx_spark"),
		EXPLOSION = reg("rbx_explosion"), EXPLOSION_SMOKE = reg("rbx_explosion_smoke"),
		SHOCKWAVE = reg("rbx_shockwave"), IMPLOSION = reg("rbx_implosion");

	private static SimpleParticleType reg(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Rocraft.id(name), FabricParticleTypes.simple(true));
	}

	static void init() {}
}
