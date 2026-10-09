package com.rocraft.rbx;

import java.util.function.Function;
import org.joml.Matrix4f;

/**
 * Roblox R6 rig: Motor6D joints (standard C0/C1) and the solve Part1 = Part0 * C0 * Transform * C1^-1.
 * Part order: 0 Head, 1 Torso, 2 Right Arm, 3 Left Arm, 4 Right Leg, 5 Left Leg. Results are relative to HumanoidRootPart.
 */
public final class R6 {
	public static final String[] PARTS = {"Head", "Torso", "Right Arm", "Left Arm", "Right Leg", "Left Leg"};
	// x, y, z, R00..R22 per joint (index = Part1): Neck, RootJoint, Right Shoulder, Left Shoulder, Right Hip, Left Hip
	private static final float[][] C0 = {
		{0, 1, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0},
		{0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0},
		{1, 0.5f, 0, 0, 0, 1, 0, 1, 0, -1, 0, 0},
		{-1, 0.5f, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0},
		{1, -1, 0, 0, 0, 1, 0, 1, 0, -1, 0, 0},
		{-1, -1, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0}};
	private static final float[][] C1 = {
		{0, -0.5f, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0},
		{0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0},
		{-0.5f, 0.5f, 0, 0, 0, 1, 0, 1, 0, -1, 0, 0},
		{0.5f, 0.5f, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0},
		{0.5f, 1, 0, 0, 0, 1, 0, 1, 0, -1, 0, 0},
		{-0.5f, 1, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0}};

	public static Matrix4f cframe(float[] c) {
		if (c == null) return new Matrix4f();
		return new Matrix4f(c[3], c[6], c[9], 0, c[4], c[7], c[10], 0, c[5], c[8], c[11], 0, c[0], c[1], c[2], 1);
	}

	/** transform(partName) gives the animated joint Transform, or null for rest pose. */
	public static Matrix4f[] solve(Function<String, Matrix4f> transform) {
		Matrix4f[] out = new Matrix4f[6];
		out[1] = joint(new Matrix4f(), 1, transform.apply(PARTS[1]));
		for (int i : new int[]{0, 2, 3, 4, 5}) out[i] = joint(out[1], i, transform.apply(PARTS[i]));
		return out;
	}

	private static Matrix4f joint(Matrix4f parent, int i, Matrix4f t) {
		var m = new Matrix4f(parent).mul(cframe(C0[i]));
		if (t != null) m.mul(t);
		return m.mul(cframe(C1[i]).invert());
	}
}
