package com.rocraft.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Roblox's RbxCharacterSounds for every character in view (players and Robloxian mobs), with the sound files from the user's install:
 * Running (footsteps, pitch 1.85) while moving on the ground, Climbing on ladders, Swimming (pitch 1.6), FreeFalling
 * fading in past 75 studs/s, Jumping, Landing (only hard landings, louder the faster), Splash entering water.
 * Died is the "oof" (PlayerSoundMixin). Minecraft's own step/swim/splash/land sounds are off for players.
 */
final class CharacterSounds {
	static final String FOOTSTEPS = "sounds/action_footsteps_plastic.mp3", FALLING = "sounds/action_falling.ogg", JUMP = "sounds/action_jump.mp3",
		LAND = "sounds/action_jump_land.mp3", SWIM = "sounds/action_swim.mp3", SPLASH = "sounds/impact_water.mp3";
	static final float VOLUME = 1f; // Roblox mixes these files quietly
	private static final Map<Integer, CharacterSounds> BY_ENTITY = new HashMap<>();

	enum State { RUNNING, CLIMBING, SWIMMING, FREEFALL, DEAD }

	private State state = State.RUNNING;
	private RbxAudio.Voice running, climbing, swimming, falling;
	private double lastVy;

	static void tick(Minecraft mc) {
		BY_ENTITY.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null && stopAll(e.getValue()));
		for (var e : mc.level.entitiesForRendering())
			if (e instanceof Player || e instanceof com.rocraft.tools.Robloxian) BY_ENTITY.computeIfAbsent(e.getId(), k -> new CharacterSounds()).update((LivingEntity) e);
	}

	private static boolean stopAll(CharacterSounds c) {
		c.loop(null, null);
		return true;
	}

	private void update(LivingEntity p) {
		double k = 20 / 0.28; // blocks/tick -> studs/s
		double vx = (p.getX() - p.xo) * k, vy = (p.getY() - p.yo) * k, vz = (p.getZ() - p.zo) * k, speed = Math.hypot(vx, vz);
		boolean ground = p.onGround(), water = p.isInWater(), flying = p instanceof Player pl && pl.getAbilities().flying;
		State now = !p.isAlive() ? State.DEAD : p.onClimbable() && !ground ? State.CLIMBING : water ? State.SWIMMING
			: ground || flying ? State.RUNNING : State.FREEFALL;

		if (now != state) {
			if (now == State.FREEFALL && state == State.RUNNING && vy > 5) RbxAudio.play(JUMP, p, false, VOLUME, 1); // Jumping
			if (state == State.FREEFALL && now == State.RUNNING && -lastVy > 75) // Landed: map(verticalSpeed, 50, 100, 0, 1)
				RbxAudio.play(LAND, p, false, (float) Math.clamp((-lastVy - 50) / 50, 0, 1) * VOLUME, 1);
			if (now == State.SWIMMING && Math.abs(lastVy) > 0.1) // Splash: map(verticalSpeed, 100, 350, 0.28, 1)
				RbxAudio.play(SPLASH, p, false, (float) Math.clamp(0.28 + (Math.abs(lastVy) - 100) / 250 * 0.72, 0.28, 1) * VOLUME, 1);
			state = now;
		}
		lastVy = vy;

		// looped sounds: only the current state's one plays, and only while its condition holds
		double v3 = Math.sqrt(vx * vx + vy * vy + vz * vz);
		switch (state) {
			case RUNNING -> loop(flying || speed <= 0.5 ? null : State.RUNNING, p);
			case CLIMBING -> loop(Math.abs(vy) > 0.1 ? State.CLIMBING : null, p);
			case SWIMMING -> loop(v3 > 1 ? State.SWIMMING : null, p);
			case FREEFALL -> {
				loop(State.FREEFALL, p);
				if (falling != null) falling.volume(v3 > 75 ? Math.min(VOLUME, falling.getVolume() + 0.9f * 0.05f * VOLUME) : 0);
			}
			default -> loop(null, p);
		}
	}

	/** Keep only `which` looped sound going (null = none). */
	private void loop(State which, LivingEntity p) {
		running = keep(running, which == State.RUNNING, FOOTSTEPS, p, VOLUME, 1.85f);
		climbing = keep(climbing, which == State.CLIMBING, FOOTSTEPS, p, VOLUME, 1);
		swimming = keep(swimming, which == State.SWIMMING, SWIM, p, VOLUME, 1.6f);
		falling = keep(falling, which == State.FREEFALL, FALLING, p, 0, 1);
	}

	private static RbxAudio.Voice keep(RbxAudio.Voice v, boolean want, String file, LivingEntity p, float volume, float pitch) {
		if (want) return v != null && !v.isStopped() ? v : p == null ? null : RbxAudio.play(file, p, true, volume, pitch);
		if (v != null) v.end();
		return null;
	}
}
