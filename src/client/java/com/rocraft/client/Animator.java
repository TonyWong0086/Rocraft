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
 * Roblox's Animate script (R6 and R15 versions), reduced: picks idle/walk/jump/fall/climb/sit from the character's
 * state, layers toolnone (right arm) while holding gear and toolslash/toollunge on attacks, and plays emotes (chat
 * "/e", the emote wheel). Animation data is Roblox's own.
 */
final class Animator {
	/** Animate's animations: one id table per rig, indexed by these. */
	static final int WALK = 0, IDLE = 1, IDLE2 = 2, JUMP = 3, FALL = 4, CLIMB = 5, SIT = 6, TOOLNONE = 7, SLASH = 8, LUNGE = 9, BOARD_STAND = 10;
	static final long[] R6_IDS = {180426354L, 180435571L, 180435792L, 125750702L, 180436148L, 180436334L, 178130996L, 182393478L, 129967390L, 129967478L, 398645802L};
	/** R15 Animate as of 2018 (walk plays at speed / 16; the hoverboard has no R15 stand, so idle). */
	static final long[] R15_IDS = {507777826L, 507766388L, 507766666L, 507765000L, 507767968L, 507765644L, 2506281703L, 507768375L, 522635514L, 522638767L, 507766388L};
	/** Chat emotes: "/e name" (Animate's emoteNames; dance picks one of three and loops, the rest play once). */
	static final Map<String, long[]> EMOTES = Map.of("dance", new long[]{182435998L, 182491037L, 182491065L}, "wave", new long[]{128777973L},
		"point", new long[]{128853357L}, "laugh", new long[]{129423131L}, "cheer", new long[]{129423030L});
	static final Map<String, long[]> R15_EMOTES = Map.of("dance", new long[]{507771019L, 507776043L, 507777268L}, "wave", new long[]{507770239L},
		"point", new long[]{507770453L}, "laugh", new long[]{507770818L}, "cheer", new long[]{507770677L});
	private static final Map<Long, RbxAnim> ANIMS = new ConcurrentHashMap<>();
	private static final java.util.Set<Long> LOADING = ConcurrentHashMap.newKeySet();
	private static final Map<Integer, Animator> BY_ENTITY = new HashMap<>();
	static int active() { return BY_ENTITY.size(); }

	static void load() {
		Thread.startVirtualThread(() -> {
			for (long[] ids : new long[][]{R6_IDS, R15_IDS}) for (long id : ids) fetch(id);
			for (var m : java.util.List.of(EMOTES, R15_EMOTES)) for (long[] ids : m.values()) for (long id : ids) fetch(id);
		});
	}

	/** Downloads an animation once (blocking); later calls are no-ops. */
	static void fetch(long id) {
		if (ANIMS.containsKey(id) || !LOADING.add(id)) return;
		try { ANIMS.put(id, RbxAnim.read(RobloxApi.asset(id))); }
		catch (Exception e) { Rocraft.LOGGER.warn("Roblox animation {} unavailable: {}", id, e.toString()); }
	}

	static Animator of(int entityId) {
		if (BY_ENTITY.size() > 256) BY_ENTITY.clear(); // ponytail: crude cap instead of tracking entity removal
		return BY_ENTITY.computeIfAbsent(entityId, k -> new Animator());
	}

	private long base, action, prevBase;
	private long emote;
	private float t, actionT, jumpTimer, prevT, prevRate, fade = 1, fadeLen = 0.1f, rateNow = 1;
	private long lastNanos = System.nanoTime();
	private boolean wasOnGround = true, wasSwinging;

	/** Start a chat emote ("/e dance"); stops as soon as the character moves, like Roblox. */
	boolean emote(String name, boolean r15) {
		long[] ids = (r15 ? R15_EMOTES : EMOTES).get(name);
		if (ids == null) return false;
		emote = ids[(int) (Math.random() * ids.length)];
		return true;
	}

	/** Play an animation as an emote (the emote wheel), downloading it first if needed. */
	void emote(long animId) {
		if (!ANIMS.containsKey(animId)) Thread.startVirtualThread(() -> fetch(animId));
		emote = animId;
	}

	/** A loaded Roblox animation by id (null until it has downloaded). */
	static RbxAnim anim(long id) { return ANIMS.get(id); }

	private Matrix4f[] last; // the last pose worked out, reused on throttled frames (Perf.poseEvery)
	private final int stagger = System.identityHashCode(this) & 0xFFFF; // so far avatars don't all update on the same frame

	/** Part CFrames relative to HumanoidRootPart: R6 order (6), or R15.PARTS order (15) when body != null. */
	Matrix4f[] pose(net.minecraft.world.entity.LivingEntity p, boolean holdingGear, boolean swinging, boolean lunging, boolean flying, R15Body body) {
		int every = Perf.poseEvery(p);
		if (last != null && last.length == (body != null ? 15 : 6) && every > 1 && (FrameStats.frames + stagger) % every != 0) { // the time skipped is caught up next solve
			Perf.poseCounted(true);
			return last.clone(); // callers may swap parts of the array (balloon arm)
		}
		Perf.poseCounted(false);
		last = solve(p, holdingGear, swinging, lunging, flying, body);
		return last.clone();
	}

	private boolean wasR15;

	private Matrix4f[] solve(net.minecraft.world.entity.LivingEntity p, boolean holdingGear, boolean swinging, boolean lunging, boolean flying, R15Body body) {
		boolean r15 = body != null;
		long[] ids = r15 ? R15_IDS : R6_IDS;
		if (r15 != wasR15 || base == 0) { wasR15 = r15; base = ids[IDLE]; prevBase = 0; action = 0; fade = 1; t = 0; } // rig changed: start over
		long now = System.nanoTime();
		float dt = net.minecraft.client.Minecraft.getInstance().isPaused() ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
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
		if (flying) want = -1; // admin fly (PlatformStand + BodyGyro): no animation, just the rest pose
		else if (p != null && p.getVehicle() instanceof com.rocraft.tools.Hoverboard) want = ids[BOARD_STAND]; // the board's Stand animation
		else if (p != null && p.isPassenger()) want = ids[SIT];
		else if (p != null && p.onClimbable() && !onGround) { want = ids[CLIMB]; rate = (float) (Math.abs(vy) / 12); }
		else if (!onGround && (p == null || !p.isInWater())) want = jumpTimer > 0 ? ids[JUMP] : ids[FALL];
		else if (speed > 0.5) { want = ids[WALK]; rate = (float) (speed / (r15 ? 16 : 14.5)); }
		else if (emote != 0) want = emote;
		else want = base == ids[IDLE2] ? ids[IDLE2] : ids[IDLE];
		if (want != base && !(want == ids[IDLE] && base == ids[IDLE2])) { // Animate: playAnimation(name, transitionTime) crossfades
			prevBase = base; prevT = t; prevRate = rateNow;
			fade = 0; fadeLen = want == ids[FALL] ? 0.3f : 0.1f;
			base = want; t = 0;
		}
		rateNow = rate;
		t += dt * rate;
		prevT += dt * prevRate;
		fade = Math.min(1, fade + dt / fadeLen);
		RbxAnim baseAnim = ANIMS.get(base);
		if (base == emote && emote != 0 && baseAnim != null && !baseAnim.loop && t >= baseAnim.length) emote = 0; // wave, laugh, cheer play once
		if (base == ids[IDLE] || base == ids[IDLE2]) { // Animate picks a new idle when one finishes: Animation1 weight 9, Animation2 weight 1
			if (baseAnim != null && t >= baseAnim.length) { base = Math.random() < 0.1 ? ids[IDLE2] : ids[IDLE]; t = 0; baseAnim = ANIMS.get(base); }
		}

		if (swinging && !wasSwinging && holdingGear) { action = lunging ? ids[LUNGE] : ids[SLASH]; actionT = 0; }
		wasSwinging = swinging;
		RbxAnim act = action != 0 ? ANIMS.get(action) : null;
		if (act != null) { actionT += dt; if (actionT > act.length) { action = 0; act = null; } }
		RbxAnim tool = holdingGear ? ANIMS.get(ids[TOOLNONE]) : null;

		final RbxAnim b = baseAnim, a = act, pb = fade < 1 ? ANIMS.get(prevBase) : null;
		final float bt = t, at = actionT, pt = prevT, w = fade;
		java.util.function.Function<String, Matrix4f> transform = part -> {
			if (a != null && a.animates(part)) return a.sample(part, at);
			// toolnone holds the right arm out (R15: upper arm, lower arm, hand)
			if (tool != null && (r15 ? part.startsWith("Right") && (part.endsWith("Arm") || part.endsWith("Hand")) : part.equals("Right Arm")))
				return tool.sample(part, 0);
			Matrix4f cur = b != null ? b.sample(part, bt) : null;
			if (pb == null) return cur;
			return mix(pb.sample(part, pt), cur, w);
		};
		return r15 ? com.rocraft.rbx.R15.solve(body.c0, body.c1, transform) : R6.solve(transform);
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
