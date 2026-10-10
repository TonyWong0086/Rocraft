package com.rocraft.client;

import com.rocraft.tools.Projectile;
import com.rocraft.tools.Tools;
import net.minecraft.client.Minecraft;

/** Client-side Roblox effect instances, emitted every tick (RbxParticle draws them). */
final class GearEffects {
	static final int FIRE_COLOR = 0xEC8B46; // Fire.Color default (236, 139, 70)
	private static final java.util.Set<Projectile> WHOOSHING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	static void tick(Minecraft mc) {
		var l = mc.level;
		for (var p : l.players()) {
			// Speed Coil: Smoke at the feet while running >= 10 studs/s (Size .5, Opacity .25, RiseVelocity 2)
			if (p.getMainHandItem().is(Tools.SPEED_COIL) && Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20 / 0.28 >= 10)
				RbxParticle.smoke(l, p.getX(), p.getY() + 0.05, p.getZ(), 0.5f, 0.25f, 2, 0xFFFFFF);
			// spawn ForceField (Tools.FORCEFIELD_TICKS after the character appears)
			if (p.tickCount < com.rocraft.tools.Tools.FORCEFIELD_TICKS && p.isAlive() && !(p == mc.player && mc.options.getCameraType().isFirstPerson()))
				RbxParticle.forceFieldSparkles(l, p); // the bubble and rings are drawn on the character (ForceFieldFx)
			// burning (lava, fire): a default Fire in the Torso instead of Minecraft's flames
			if (p.displayFireAnimation() && !p.isInvisible())
				RbxParticle.fire(l, p.getX(), p.getY() + 3 * 0.28, p.getZ(), 5, 9, FIRE_COLOR);
		}
		for (var e : l.entitiesForRendering()) {
			// RocketLauncher: local fire = Instance.new('Fire', Rocket) fire.Heat = 5 fire.Size = 2 -- at the back of the rocket
			if (e instanceof Projectile pr && pr.kind() == Projectile.ROCKET) {
				var v = pr.getDeltaMovement();
				var back = v.lengthSqr() < 1e-6 ? pr.position() : pr.position().subtract(v.normalize().scale(1.2 * 0.28));
				RbxParticle.fire(l, back.x, back.y + 0.15, back.z, 2, 5, FIRE_COLOR);
				// the rocket's Swoosh sound flies with it and stops when it explodes (the rocket is destroyed)
				var swoosh = com.rocraft.RbxSounds.get("rocket_launcher.swoosh");
				if (swoosh != null && WHOOSHING.add(pr))
					mc.getSoundManager().play(new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(swoosh, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f, pr, pr.getId()));
			}
		}
	}
}
