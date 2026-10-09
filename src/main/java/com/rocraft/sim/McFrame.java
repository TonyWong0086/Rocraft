package com.rocraft.sim;

/** The one Roblox -> Minecraft mapping: scale and the per-tick physics fit. No Minecraft imports. */
public final class McFrame {
	public static final double STUD = 0.28;                          // metres (= blocks) per stud
	// Fit so MC's player integrator (y += v; v = (v - g) * 0.98) gives Roblox's 6.37-stud, 0.5 s jump.
	public static final double GRAVITY = 0.1755, JUMP = 0.722;       // vanilla: 0.08, 0.42
	public static final double SPEED = 0.1 * (16 * STUD) / 4.317;    // speed attribute for WalkSpeed 16 (vanilla 0.1 = 4.317 m/s)

	/** {apex in blocks, ticks in the air} for MC's integrator. */
	public static double[] jump(double g, double j) {
		double y = 0, v = j, apex = 0; int t = 0;
		do { y += v; v = (v - g) * 0.98; apex = Math.max(apex, y); t++; } while (y > 0 && t < 200);
		return new double[]{apex, t};
	}
}
