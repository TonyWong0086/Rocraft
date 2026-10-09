package com.rocraft.sim;

/** Headless oracle: ./gradlew bench. Fails (assert) if the sim or the MC fit drifts from Roblox's numbers. */
public final class Bench {
	public static void main(String[] a) {
		Humanoid h = new Humanoid();
		double apex = 0; int steps = 0;
		h.step(0, 0, true);
		while (!h.grounded) { apex = Math.max(apex, h.y); h.step(0, 0, false); steps++; }
		System.out.printf("roblox sim: apex=%.3f studs, air=%.3f s%n", apex, steps * Humanoid.STEP);
		assert Math.abs(apex - 6.37) < 0.15 : "jump apex";          // v^2/2g, JumpPower 50
		assert Math.abs(steps * Humanoid.STEP - 0.51) < 0.03 : "air time";
		Humanoid w = new Humanoid();
		for (int i = 0; i < 60; i++) w.step(1, 0, false);
		assert Math.abs(w.x - 16) < 0.01 : "walk speed";

		double[] van = McFrame.jump(0.08, 0.42), rbx = McFrame.jump(McFrame.GRAVITY, McFrame.JUMP);
		System.out.printf("mc vanilla: apex=%.3f m, %d ticks | mc roblox: apex=%.3f m (%.2f studs), %d ticks%n",
			van[0], (int) van[1], rbx[0], rbx[0] / McFrame.STUD, (int) rbx[1]);
		assert Math.abs(van[0] - 1.252) < 0.01 : "integrator no longer matches vanilla 1.25 m jump";
		assert Math.abs(rbx[0] / McFrame.STUD - 6.37) < 0.1 : "mc jump apex";
		assert Math.abs(rbx[1] / 20 - 0.5) <= 0.05 : "mc air time";
		// R6 rest pose (no animation) must match Roblox character.rbxm part positions
		var rest = com.rocraft.rbx.R6.solve(n -> null);
		float[][] want = {{0, 1.5f, 0}, {0, 0, 0}, {1.5f, 0, 0}, {-1.5f, 0, 0}, {0.5f, -2, 0}, {-0.5f, -2, 0}};
		for (int i = 0; i < 6; i++) {
			var p = rest[i].getTranslation(new org.joml.Vector3f());
			assert p.distance(want[i][0], want[i][1], want[i][2]) < 1e-4 : "R6 rest " + com.rocraft.rbx.R6.PARTS[i] + " at " + p;
			assert Math.abs(rest[i].m00() - 1) < 1e-4 && Math.abs(rest[i].m11() - 1) < 1e-4 : "R6 rest rotation " + i;
		}
		System.out.println("r6 rest pose ok");
		System.out.println("OK");
	}
}
