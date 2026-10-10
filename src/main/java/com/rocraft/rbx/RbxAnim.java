package com.rocraft.rbx;

import java.util.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A Roblox animation: a KeyframeSequence (per-joint Pose transforms over time) or a CurveAnimation (per-joint float
 * curves, what newer emotes are), sampled with linear position / slerp rotation.
 */
public final class RbxAnim {
	public final boolean loop;
	public final float length;
	private final Map<String, List<float[]>> tracks = new HashMap<>(); // part name -> [time, cf(12)] sorted

	private RbxAnim(boolean loop, float length) { this.loop = loop; this.length = length; }

	public static RbxAnim read(byte[] data) {
		var model = RbxModel.read(data);
		var curves = model.first("CurveAnimation");
		if (curves != null) return curves(model, curves);
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

	/**
	 * CurveAnimation: Folders named after each part (nested like the rig) holding a Vector3Curve "Position" and an
	 * EulerRotationCurve "Rotation" (X/Y/Z FloatCurves, radians, XYZ order). Baked to 30 keys a second so it samples
	 * like a KeyframeSequence. ponytail: cubic keys are read as linear.
	 */
	private static RbxAnim curves(RbxModel model, RbxModel.Inst anim) {
		Map<String, float[][][]> parts = new HashMap<>(); // part -> {position x/y/z, rotation x/y/z} curves
		float len = 0;
		for (var c : model.all) {
			if (!c.className.equals("FloatCurve") || c.parent == null || c.parent.parent == null) continue;
			int axis = "XYZ".indexOf(c.name()), kind = c.parent.className.equals("Vector3Curve") ? 0 : c.parent.className.equals("EulerRotationCurve") ? 1 : -1;
			if (axis < 0 || kind < 0 || !(c.props.get("ValuesAndTimes") instanceof byte[] b) || b.length < 8) continue;
			float[][] keys = floatCurve(b);
			if (keys[0].length == 0) continue;
			parts.computeIfAbsent(c.parent.parent.name(), k -> new float[6][][])[kind * 3 + axis] = keys;
			len = Math.max(len, keys[0][keys[0].length - 1]);
		}
		var a = new RbxAnim(bool(anim.props.get("Loop")), len);
		for (var e : parts.entrySet()) {
			var list = new ArrayList<float[]>();
			for (int i = 0, n = Math.max(1, (int) Math.ceil(len * 30)); i <= n; i++) {
				float t = len * i / n;
				float[] v = new float[6];
				for (int k = 0; k < 6; k++) v[k] = e.getValue()[k] == null ? 0 : at(e.getValue()[k], t);
				var m = new Matrix3f().rotateXYZ(v[3], v[4], v[5]); // CFrame.fromEulerAnglesXYZ = Rx * Ry * Rz
				list.add(new float[]{t, v[0], v[1], v[2], m.m00, m.m10, m.m20, m.m01, m.m11, m.m21, m.m02, m.m12, m.m22});
			}
			a.tracks.put(e.getKey(), list);
		}
		return a;
	}

	/**
	 * FloatCurve.ValuesAndTimes: u32 version, u32 n, n keys {u8 interpolation, u8 tangent flags, f32 value, f32 left
	 * and right tangent}, u32 version, u32 n, n i32 times in ticks (2400 a second). Little-endian. Returns {times, values, interp}.
	 */
	static float[][] floatCurve(byte[] b) {
		var bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		bb.getInt();
		int n = bb.getInt();
		float[] val = new float[n], interp = new float[n], time = new float[n];
		for (int i = 0; i < n; i++) { interp[i] = bb.get(); bb.get(); val[i] = bb.getFloat(); bb.getFloat(); bb.getFloat(); }
		bb.getInt();
		int m = Math.min(n, bb.getInt());
		for (int i = 0; i < m; i++) time[i] = bb.getInt() / 2400f;
		return new float[][]{time, val, interp};
	}

	private static float at(float[][] c, float t) {
		float[] time = c[0], val = c[1];
		if (t <= time[0]) return val[0];
		for (int i = 1; i < time.length; i++) if (t < time[i]) {
			if (c[2][i - 1] == 0) return val[i - 1]; // Constant
			float f = (t - time[i - 1]) / (time[i] - time[i - 1]);
			return val[i - 1] + (val[i] - val[i - 1]) * f;
		}
		return val[val.length - 1];
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
