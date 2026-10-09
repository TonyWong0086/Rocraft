package com.rocraft.client;

import com.rocraft.Rocraft;
import com.rocraft.rbx.R6;
import com.rocraft.rbx.RbxAnim;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Roblox's R6 Animate script, reduced: picks idle/walk/jump/fall/climb/sit from the character's state, layers
 * toolnone (right arm) while holding gear and toolslash/toollunge on attacks. Animation data is Roblox's own.
 */
final class Animator {
	static final long WALK = 180426354L, IDLE = 180435571L, IDLE2 = 180435792L, JUMP = 125750702L, FALL = 180436148L,
		CLIMB = 180436334L, SIT = 178130996L, TOOLNONE = 182393478L, SLASH = 129967390L, LUNGE = 129967478L,
		WAVE = 128777973L, POINT = 128853357L, LAUGH = 129423131L, CHEER = 129423030L;
	/** Animate: dance = 3 variations picked at random, each loops. */
	static final long[] DANCE = {182435998L, 182491037L, 182491065L};
	/** Chat emotes: "/e name" (Animate's emoteNames; dance loops, the rest play once). */
	static final Map<String, long[]> EMOTES = Map.of("dance", DANCE, "wave", new long[]{WAVE}, "point", new long[]{POINT}, "laugh", new long[]{LAUGH}, "cheer", new long[]{CHEER});
	private static final Map<Long, RbxAnim> ANIMS = new ConcurrentHashMap<>();
	private static final Map<Integer, Animator> BY_ENTITY = new HashMap<>();

	static void load() {
		Thread.startVirtualThread(() -> {
			for (long id : new long[]{WALK, IDLE, IDLE2, JUMP, FALL, CLIMB, SIT, TOOLNONE, SLASH, LUNGE, WAVE, POINT, LAUGH, CHEER, DANCE[0], DANCE[1], DANCE[2]}) {
				try { ANIMS.put(id, RbxAnim.read(RobloxApi.asset(id))); }
				catch (Exception e) { Rocraft.LOGGER.warn("Roblox animation {} unavailable: {}", id, e.toString()); }
			}
		});
	}

	static Animator of(int entityId) {
		if (BY_ENTITY.size() > 256) BY_ENTITY.clear(); // ponytail: crude cap instead of tracking entity removal
		return BY_ENTITY.computeIfAbsent(entityId, k -> new Animator());
	}

	private long base = IDLE, action, prevBase;
	private long emote;
	private float t, actionT, jumpTimer, prevT, prevRate, fade = 1, fadeLen = 0.1f, rateNow = 1;
	private long lastNanos = System.nanoTime();
	private boolean wasOnGround = true, wasSwinging;

	/** Part CFrames relative to HumanoidRootPart (R6 order). */
	/** Start a chat emote ("/e dance"); stops as soon as the character moves, like Roblox. */
	boolean emote(String name) {
		long[] ids = EMOTES.get(name);
		if (ids == null) return false;
		emote = ids[(int) (Math.random() * ids.length)];
		return true;
	}

	/** A loaded Roblox animation by id (null until it has downloaded). */
	static RbxAnim anim(long id) { return ANIMS.get(id); }

	Matrix4f[] pose(Player p, boolean holdingGear, boolean swinging, boolean lunging, boolean flying) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;

		double vx = p == null ? 0 : (p.getX() - p.xo) * 20 / 0.28, vz = p == null ? 0 : (p.getZ() - p.zo) * 20 / 0.28, vy = p == null ? 0 : (p.getY() - p.yo) * 20 / 0.28;
		double speed = Math.hypot(vx, vz);
		boolean onGround = p == null || p.onGround();
		if (wasOnGround && !onGround && vy > 0) jumpTimer = 0.3f; // Animate: jumpAnimDuration
		wasOnGround = onGround;
		jumpTimer = Math.max(0, jumpTimer - dt);

		long want;
		float rate = 1;
		if (emote != 0 && (speed > 0.5 || !onGround || flying)) emote = 0; // Animate: any other pose replaces the emote
		if (flying) want = 0; // admin fly (PlatformStand + BodyGyro): no animation, just the rest pose
		else if (p != null && p.isPassenger()) want = SIT;
		else if (p != null && p.onClimbable() && !onGround) { want = CLIMB; rate = (float) (Math.abs(vy) / 12); }
		else if (!onGround && (p == null || !p.isInWater())) want = jumpTimer > 0 ? JUMP : FALL;
		else if (speed > 0.5) { want = WALK; rate = (float) (speed / 14.5); }
		else if (emote != 0) want = emote;
		else want = base == IDLE2 ? IDLE2 : IDLE;
		if (want != base && !(want == IDLE && base == IDLE2)) { // Animate: playAnimation(name, transitionTime) crossfades
			prevBase = base; prevT = t; prevRate = rateNow;
			fade = 0; fadeLen = want == FALL ? 0.3f : 0.1f;
			base = want; t = 0;
		}
		rateNow = rate;
		t += dt * rate;
		prevT += dt * prevRate;
		fade = Math.min(1, fade + dt / fadeLen);
		RbxAnim baseAnim = ANIMS.get(base);
		if (base == emote && emote != 0 && baseAnim != null && !baseAnim.loop && t >= baseAnim.length) emote = 0; // wave, laugh, cheer play once
		if (base == IDLE || base == IDLE2) { // Animate picks a new idle when one finishes: Animation1 weight 9, Animation2 weight 1
			if (baseAnim != null && t >= baseAnim.length) { base = Math.random() < 0.1 ? IDLE2 : IDLE; t = 0; baseAnim = ANIMS.get(base); }
		}

		if (swinging && !wasSwinging && holdingGear) { action = lunging ? LUNGE : SLASH; actionT = 0; }
		wasSwinging = swinging;
		RbxAnim act = action != 0 ? ANIMS.get(action) : null;
		if (act != null) { actionT += dt; if (actionT > act.length) { action = 0; act = null; } }
		RbxAnim tool = holdingGear ? ANIMS.get(TOOLNONE) : null;

		final RbxAnim b = baseAnim, a = act, pb = fade < 1 ? ANIMS.get(prevBase) : null;
		final float bt = t, at = actionT, pt = prevT, w = fade;
		return R6.solve(part -> {
			if (a != null && a.animates(part)) return a.sample(part, at);
			if (tool != null && part.equals("Right Arm")) return tool.sample(part, 0);
			Matrix4f cur = b != null ? b.sample(part, bt) : null;
			if (pb == null) return cur;
			return mix(pb.sample(part, pt), cur, w);
		});
	}

	/** Blend two joint transforms (null = rest pose): lerp position, slerp rotation. */
	static Matrix4f mix(Matrix4f a, Matrix4f b, float w) {
		if (a == null) a = new Matrix4f();
		if (b == null) b = new Matrix4f();
		var qa = a.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
		var qb = b.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
		var p = a.getTranslation(new org.joml.Vector3f()).lerp(b.getTranslation(new org.joml.Vector3f()), w);
		return new Matrix4f().translation(p).rotate(qa.slerp(qb, w));
	}
}
