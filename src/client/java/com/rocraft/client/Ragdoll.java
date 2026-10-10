package com.rocraft.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Roblox death (BreakJoints): the six R6 parts come loose and fall as separate bricks, toppling, bouncing and
 * sliding to rest. Rigid boxes with gravity 196.2 studs/s^2, Plastic friction and elasticity, simulated in the
 * character's own frame (studs, y up, origin at the torso centre) against the ground plane under its feet.
 * ponytail: parts don't collide with each other or with terrain steps; add when a flat floor looks wrong.
 */
final class Ragdoll {
	private static final Map<Integer, Ragdoll> BY_ENTITY = new HashMap<>();
	static int active() { return BY_ENTITY.size(); }
	/** Half sizes in studs, R6 order: Head, Torso, Right Arm, Left Arm, Right Leg, Left Leg. */
	static final float[][] HALF = {{0.6f, 0.6f, 0.6f}, {1, 1, 0.5f}, {0.5f, 1, 0.5f}, {0.5f, 1, 0.5f}, {0.5f, 1, 0.5f}, {0.5f, 1, 0.5f}};
	static final float GROUND = -3, GRAVITY = 196.2f, FRICTION = 0.3f, ELASTICITY = 0.2f;

	final Vector3f[] p = new Vector3f[6], v = new Vector3f[6], w = new Vector3f[6];
	final Quaternionf[] q = new Quaternionf[6];
	private final int[] still = new int[6];
	private long last = System.nanoTime();

	Ragdoll(Matrix4f[] pose, long seed) {
		var r = new Random(seed);
		for (int i = 0; i < 6; i++) {
			p[i] = pose[i].getTranslation(new Vector3f());
			q[i] = pose[i].getNormalizedRotation(new Quaternionf());
			// the joints just let go. In Roblox the pieces knock each other over; these don't touch, so each starts
			// tipping over one of its bottom edges, fast enough (7-9 rad/s) to topple a 1x2x1 limb off its end
			boolean aboutX = i == 1 || r.nextBoolean(); // the torso falls on its front or back
			float sign = r.nextBoolean() ? 1 : -1, spin = 7 + 2 * r.nextFloat();
			var local = aboutX ? new Vector3f(sign * spin, 0, 0) : new Vector3f(0, 0, sign * spin);
			// centre relative to the edge it pivots on (the bottom edge on the side it falls toward)
			var fromEdge = aboutX ? new Vector3f(0, HALF[i][1], -sign * HALF[i][2]) : new Vector3f(sign * HALF[i][0], HALF[i][1], 0);
			v[i] = new Vector3f(local).cross(fromEdge).rotate(q[i]);
			w[i] = local.rotate(q[i]);
		}
	}

	/** Part CFrames for this character: its animated pose while alive, the falling pieces once it has died. */
	static Matrix4f[] pose(LivingEntity e, int id, Matrix4f[] alive) {
		if (e == null || !e.isDeadOrDying()) { BY_ENTITY.remove(id); return alive; }
		if (BY_ENTITY.size() > 64) BY_ENTITY.clear(); // ponytail: crude cap instead of tracking entity removal
		var r = BY_ENTITY.computeIfAbsent(id, k -> new Ragdoll(alive, id));
		long now = System.nanoTime();
		float dt = net.minecraft.client.Minecraft.getInstance().isPaused() ? 0 : Math.min(0.1f, (now - r.last) / 1e9f);
		r.last = now;
		r.step(dt);
		var out = new Matrix4f[alive.length];
		for (int i = 0; i < alive.length; i++) out[i] = i < 6 ? new Matrix4f().translation(r.p[i]).rotate(r.q[i]) : alive[i];
		return out;
	}

	void step(float dt) {
		int n = (int) Math.ceil(dt * 240);
		for (int s = 0; s < n; s++) for (int i = 0; i < 6; i++) body(i, dt / n);
	}

	private void body(int i, float dt) {
		float[] h = HALF[i];
		float m = 8 * h[0] * h[1] * h[2]; // Plastic: mass ~ volume
		var R = new Matrix3f().rotation(q[i]);
		// world inverse inertia: R diag(1 / I_body) R^T, I_body of a box = m/3 (b^2 + c^2)
		var Iinv = new Matrix3f(R).scale(3 / (m * (h[1] * h[1] + h[2] * h[2])), 3 / (m * (h[0] * h[0] + h[2] * h[2])), 3 / (m * (h[0] * h[0] + h[1] * h[1])))
			.mul(new Matrix3f(R).transpose());

		v[i].y -= GRAVITY * dt;
		p[i].fma(dt, v[i]);
		float ang = w[i].length() * dt;
		if (ang > 1e-6f) q[i].premul(new Quaternionf().fromAxisAngleRad(w[i].x / w[i].length(), w[i].y / w[i].length(), w[i].z / w[i].length(), ang)).normalize();

		float deepest = 0;
		var up = new Vector3f(0, 1, 0);
		var contacts = new java.util.ArrayList<Vector3f>();
		for (int c = 0; c < 8; c++) {
			var r = new Vector3f((c & 1) == 0 ? -h[0] : h[0], (c & 2) == 0 ? -h[1] : h[1], (c & 4) == 0 ? -h[2] : h[2]).rotate(q[i]);
			float depth = GROUND - (p[i].y + r.y);
			if (depth > 0) { contacts.add(r); deepest = Math.max(deepest, depth); }
		}
		// sequential impulses, a few passes so a face resting on 4 corners settles instead of rocking
		boolean bounce = true;
		for (int pass = 0; pass < 6 && !contacts.isEmpty(); pass++, bounce = false)
			for (var r : contacts) {
				var vp = new Vector3f(w[i]).cross(r).add(v[i]);
				if (vp.y >= 0) continue;
				float j = -(1 + (bounce && vp.y < -3 ? ELASTICITY : 0)) * vp.y / effMass(m, Iinv, r, up); // slow contacts don't bounce
				apply(i, m, Iinv, r, new Vector3f(0, j, 0));
				// Coulomb friction against the sliding direction, at most FRICTION * normal impulse
				vp = new Vector3f(w[i]).cross(r).add(v[i]);
				var t = new Vector3f(vp.x, 0, vp.z);
				float slide = t.length();
				if (slide < 1e-5f) continue;
				t.div(slide);
				float jt = Math.min(slide / effMass(m, Iinv, r, t), FRICTION * j);
				apply(i, m, Iinv, r, t.mul(-jt));
			}
		p[i].y += deepest;
		if (deepest > 0) w[i].mul(1 - 2 * dt); // rolling resistance on the ground so pieces settle
		// at rest once it has been slow on the ground for a quarter second (not just at the top of a topple)
		still[i] = deepest > 0 && v[i].lengthSquared() < 0.25f && w[i].lengthSquared() < 0.25f ? still[i] + 1 : 0;
		if (still[i] > 60) { v[i].zero(); w[i].zero(); }
	}

	/** 1 / m + n . ((Iinv (r x n)) x r): how much a unit impulse along n at r changes the speed there. */
	private static float effMass(float m, Matrix3f Iinv, Vector3f r, Vector3f n) {
		var a = new Vector3f(r).cross(n).mul(Iinv).cross(r);
		return 1 / m + a.dot(n);
	}

	private void apply(int i, float m, Matrix3f Iinv, Vector3f r, Vector3f impulse) {
		v[i].fma(1 / m, impulse);
		w[i].add(new Vector3f(r).cross(impulse).mul(Iinv));
	}

	/** Self-check: a dropped standing body ends up lying still on the ground. */
	public static void main(String[] a) {
		var pose = new Matrix4f[6];
		float[][] at = {{0, 1.5f, 0}, {0, 0, 0}, {1.5f, 0, 0}, {-1.5f, 0, 0}, {0.5f, -2, 0}, {-0.5f, -2, 0}};
		for (int i = 0; i < 6; i++) pose[i] = new Matrix4f().translation(at[i][0], at[i][1], at[i][2]);
		var r = new Ragdoll(pose, 7);
		for (int f = 0; f < 600; f++) r.step(1 / 60f);
		for (int i = 0; i < 6; i++) {
			float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
			for (int c = 0; c < 8; c++) {
				float y = r.p[i].y + new Vector3f((c & 1) == 0 ? -HALF[i][0] : HALF[i][0], (c & 2) == 0 ? -HALF[i][1] : HALF[i][1], (c & 4) == 0 ? -HALF[i][2] : HALF[i][2]).rotate(r.q[i]).y;
				lowest = Math.min(lowest, y); highest = Math.max(highest, y);
			}
			System.out.printf("part %d: lowest %.3f highest %.3f v %.3f w %.3f%n", i, lowest, highest, r.v[i].length(), r.w[i].length());
			if (Math.abs(lowest - GROUND) > 0.05f || r.v[i].length() > 0.5f || r.w[i].length() > 0.5f) throw new AssertionError("part " + i + " not at rest on the ground");
			if (i >= 1 && highest - lowest > 1.5f) throw new AssertionError("limb " + i + " still standing");
		}
		System.out.println("ok");
	}
}
