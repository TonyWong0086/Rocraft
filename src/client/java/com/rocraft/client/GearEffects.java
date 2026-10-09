package com.rocraft.client;

import com.rocraft.tools.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/** Client-side gear effects: Speed Coil smoke at the feet while running >= 10 studs/s (Smoke: RiseVelocity 2, Size .5). */
final class GearEffects {
	static void tick(Minecraft mc) {
		for (var p : mc.level.players()) {
			if (!p.getMainHandItem().is(Tools.SPEED_COIL)) continue;
			double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20 / 0.28;
			if (speed < 10) continue;
			for (int i = 0; i < 2; i++)
				mc.level.addParticle(com.rocraft.RbxParticles.SMOKE, p.getX() + (Math.random() - 0.5) * 0.3, p.getY() + 0.05,
					p.getZ() + (Math.random() - 0.5) * 0.3, 0, 2 * 0.28 / 20, 0);
		}
	}
}
