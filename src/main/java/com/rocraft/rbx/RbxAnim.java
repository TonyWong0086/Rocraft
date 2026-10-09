package com.rocraft.rbx;

import java.util.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A Roblox KeyframeSequence: per-joint Pose transforms over time, sampled with linear position / slerp rotation. */
public final class RbxAnim {
	public final boolean loop;
	public final float length;
	private final Map<String, List<float[]>> tracks = new HashMap<>(); // part name -> [time, cf(12)] sorted

	private RbxAnim(boolean loop, float length) { this.loop = loop; this.length = length; }

	public static RbxAnim read(byte[] data) {
		var model = RbxModel.read(data);
		var seq = model.first("KeyframeSequence");
		if (seq == null) throw new IllegalArgumentException("no KeyframeSequence");
		float len = 0;
		List<float[]> raw = new ArrayList<>();
		Map<float[], String> names = new IdentityHashMap<>();
		for (var kf : seq.children) {
			if (!kf.className.equals("Keyframe")) continue;
			float t = num(kf.props.get("Time"));
			len = Math.max(len, t);
			collect(kf, t, raw, names);
		}
		var a = new RbxAnim(bool(seq.props.get("Loop")), len);
		for (float[] r : raw) a.tracks.computeIfAbsent(names.get(r), k -> new ArrayList<>()).add(r);
		for (var l : a.tracks.values()) l.sort(Comparator.comparingDouble(r -> r[0]));
		return a;
	}

	private static void collect(RbxModel.Inst i, float t, List<float[]> out, Map<float[], String> names) {
		for (var c : i.children) {
			if (!c.className.equals("Pose")) continue;
			float[] cf = c.cframe("CFrame");
			Object w = c.props.get("Weight");
			if (cf != null && (w == null || num(w) > 0)) {
				float[] r = new float[13];
				r[0] = t;
				System.arraycopy(cf, 0, r, 1, 12);
				out.add(r);
				names.put(r, c.name());
			}
			collect(c, t, out, names);
		}
	}

	private static float num(Object o) { return o instanceof Float f ? f : o == null ? 0 : Float.parseFloat(o.toString()); }
	private static boolean bool(Object o) { return o instanceof Boolean b ? b : o != null && o.toString().equalsIgnoreCase("true"); }

	public boolean animates(String part) { return tracks.containsKey(part); }

	/** Joint transform for a part at time t (wrapped if looped, clamped otherwise), or null if this anim doesn't touch it. */
	public Matrix4f sample(String part, float t) {
		var tr = tracks.get(part);
		if (tr == null) return null;
		if (length > 0) t = loop ? (t % length + length) % length : Math.min(t, length);
		float[] a = tr.get(0), b = a;
		for (float[] k : tr) { if (k[0] <= t) a = k; if (k[0] >= t) { b = k; break; } }
		if (b[0] < a[0]) b = a;
		float f = b[0] > a[0] ? (t - a[0]) / (b[0] - a[0]) : 0;
		Quaternionf qa = quat(a), qb = quat(b);
		qa.slerp(qb, f);
		var pos = new Vector3f(a[1], a[2], a[3]).lerp(new Vector3f(b[1], b[2], b[3]), f);
		return new Matrix4f().translation(pos).rotate(qa);
	}

	private static Quaternionf quat(float[] k) {
		// k[4..12] = rows R00..R22; JOML Matrix3f(m00, m01, m02, ...) takes columns
		var m = new Matrix3f(k[4], k[7], k[10], k[5], k[8], k[11], k[6], k[9], k[12]);
		return new Quaternionf().setFromNormalized(m.normal());
	}
}
