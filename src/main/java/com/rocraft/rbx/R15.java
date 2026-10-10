package com.rocraft.rbx;

import java.util.function.Function;
import org.joml.Matrix4f;

/**
 * Roblox R15 rig (characterR15.rbxm): 15 parts on Motor6Ds, Part1 = Part0 * C0 * Transform * C1^-1, results relative to
 * HumanoidRootPart. Every joint's C0/C1 is a pure offset (identity rotation) in the default rig.
 */
public final class R15 {
	public static final String[] PARTS = {"Head", "UpperTorso", "LowerTorso", "RightUpperArm", "RightLowerArm", "RightHand",
		"LeftUpperArm", "LeftLowerArm", "LeftHand", "RightUpperLeg", "RightLowerLeg", "RightFoot", "LeftUpperLeg", "LeftLowerLeg", "LeftFoot"};
	public static final int HEAD = 0, UPPER_TORSO = 1, LOWER_TORSO = 2, RIGHT_UPPER_ARM = 3, RIGHT_HAND = 5, LEFT_UPPER_ARM = 6, LEFT_HAND = 8,
		RIGHT_UPPER_LEG = 9, RIGHT_FOOT = 11, LEFT_UPPER_LEG = 12, LEFT_FOOT = 14;
	/** Part0 of each part's joint (-1 = HumanoidRootPart); listed so a parent always comes first in SOLVE_ORDER. */
	public static final int[] PARENT = {1, 2, -1, 1, 3, 4, 1, 6, 7, 2, 9, 10, 2, 12, 13};
	static final int[] SOLVE_ORDER = {2, 1, 0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
	/** Joint C0 (in Part0) and C1 (in Part1) offsets, studs: Neck, Waist, Root, shoulders, elbows, wrists, hips, knees, ankles. */
	public static final float[][] C0 = {{0, 0.80002f, 0}, {0, 0.20002f, 0}, {0, -0.35f, 0}, {1, 0.56302f, 0}, {0, -0.33417f, 0}, {0, -0.50093f, 0},
		{-1, 0.56302f, 0}, {0.00048f, -0.33406f, 0}, {0.00048f, -0.50093f, 0}, {0.5f, -0.19997f, 0}, {0, -0.40095f, 0}, {0, -0.54716f, 0},
		{-0.5f, -0.19997f, 0}, {0, -0.4011f, 0}, {0, -0.54716f, 0}};
	public static final float[][] C1 = {{0, -0.5f, -0.00027f}, {0, -0.79999f, 0}, {0, -0.19997f, 0}, {-0.5f, 0.39433f, 0}, {0, 0.25858f, 0}, {0, 0.12505f, 0},
		{0.5f, 0.39433f, 0}, {0.00048f, 0.25869f, 0}, {0.00048f, 0.12505f, 0}, {0, 0.42078f, 0}, {0, 0.37917f, 0}, {0, 0.10194f, 0},
		{0, 0.42078f, 0}, {0, 0.37902f, 0}, {0, 0.10194f, 0}};

	/** transform(partName) gives the animated joint Transform, or null for rest pose. */
	public static Matrix4f[] solve(Function<String, Matrix4f> transform) { return solve(C0, C1, transform); }

	/** With a character's own joint offsets (body packages move them). */
	public static Matrix4f[] solve(float[][] c0, float[][] c1, Function<String, Matrix4f> transform) {
		Matrix4f[] out = new Matrix4f[15];
		for (int i : SOLVE_ORDER) {
			var m = PARENT[i] < 0 ? new Matrix4f() : new Matrix4f(out[PARENT[i]]);
			m.translate(c0[i][0], c0[i][1], c0[i][2]);
			Matrix4f t = transform.apply(PARTS[i]);
			if (t != null) m.mul(t);
			out[i] = m.translate(-c1[i][0], -c1[i][1], -c1[i][2]);
		}
		return out;
	}

	/** The R6 part (0 Head, 1 Torso, 2 Right Arm, 3 Left Arm, 4 Right Leg, 5 Left Leg) each R15 part belongs to. */
	public static final int[] R6_PART = {0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5};
	/** Where the R6 part's centre sits in its R15 stand-in (part, offset): Torso = UpperTorso less 0.2 (neck at +1), limbs hang from the shoulder / hip. */
	public static final int[] R6_FROM = {HEAD, UPPER_TORSO, RIGHT_UPPER_ARM, LEFT_UPPER_ARM, RIGHT_UPPER_LEG, LEFT_UPPER_LEG};
	public static final float[] R6_DY = {0, -0.2f, -0.369f, -0.369f, -0.579f, -0.579f}; // so the stand-ins sit where R6 parts would at rest

	/** R6 part matrices standing in for an R15 pose (ragdolls, things placed on R6 parts). */
	public static Matrix4f[] toR6(Matrix4f[] r15) {
		Matrix4f[] out = new Matrix4f[6];
		for (int i = 0; i < 6; i++) out[i] = new Matrix4f(r15[R6_FROM[i]]).translate(0, R6_DY[i], 0);
		return out;
	}

	/** R15 parts carried rigidly by R6 part matrices (a ragdoll), each where it sits on its stand-in at rest: limbs stay straight. */
	public static Matrix4f[] fromR6(Matrix4f[] r6, float[][] c0, float[][] c1) {
		var rest = solve(c0, c1, n -> null);
		var standIn = toR6(rest);
		Matrix4f[] out = new Matrix4f[15];
		for (int i = 0; i < 15; i++) out[i] = new Matrix4f(r6[R6_PART[i]]).mul(new Matrix4f(standIn[R6_PART[i]]).invert().mul(rest[i]));
		return out;
	}
}
