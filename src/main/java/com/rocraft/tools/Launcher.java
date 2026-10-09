package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Rocket Launcher, Superball and Slingshot: Tool.Activated fires at the mouse (Tools.mouse). */
public final class Launcher extends Item {
	private final int kind;

	public Launcher(Properties p, int kind) { super(p); this.kind = kind; }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		double stud = McFrame.STUD;
		Vec3 head = p.position().add(0, 4.5 * stud, 0), target = Tools.mouse(p);
		Vec3 dir = target.subtract(head).normalize();
		switch (kind) {
			case Projectile.ROCKET -> { // spawn 5 studs out along the launcher, fly straight at the mouse
				Projectile.spawn(sl, p, kind, head.add(dir.scale(5 * stud)), dir.scale(60), -1);
				com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.get("rocket_launcher.swoosh"));
			}
			case Projectile.SUPERBALL -> { // from the root part, 5 studs out, BrickColor.Random()
				Vec3 root = p.position().add(0, 3 * stud, 0);
				Projectile.spawn(sl, p, kind, root.add(dir.scale(5 * stud)), dir.scale(200), randomBrickColor(p));
				com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.get("superball.boing"));
			}
			default -> { // Slingshot: launch 5 studs out, lowest ballistic angle that reaches the mouse at 85 studs/s
				Vec3 launch = head.add(dir.scale(5 * stud));
				Vec3 d = target.subtract(launch).scale(1 / stud);
				double dx = Math.hypot(d.x, d.z), theta = launchAngle(dx, d.y, 85, 196.2);
				Vec3 flat = new Vec3(d.x, 0, d.z).normalize();
				Vec3 v = new Vec3(flat.x * Math.cos(theta), Math.sin(theta), flat.z * Math.cos(theta)).scale(85);
				Projectile.spawn(sl, p, kind, launch, v, 0xFFC4281C); // BrickColor 21
				com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.get("slingshot.sling"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Slingshot.computeLaunchAngle: lower of the two angles, 45 degrees if out of reach. */
	static double launchAngle(double dx, double dy, double v, double g) {
		double in = v * v * v * v - g * (g * dx * dx + 2 * dy * v * v);
		if (in <= 0 || dx < 1e-6) return 0.25 * Math.PI;
		double r = Math.sqrt(in);
		return Math.min(Math.atan((v * v + r) / (g * dx)), Math.atan((v * v - r) / (g * dx)));
	}

	/** A few classic BrickColors, like BrickColor.Random() lands on. */
	static int randomBrickColor(Player p) {
		int[] c = {0xFFC4281C, 0xFF0D69AC, 0xFFF5CD30, 0xFF4B974B, 0xFFA34B4B, 0xFFDA8541, 0xFF6B327C, 0xFFF2F3F3, 0xFF1B2A35, 0xFFA4BD47};
		return c[p.getRandom().nextInt(c.length)];
	}
}
