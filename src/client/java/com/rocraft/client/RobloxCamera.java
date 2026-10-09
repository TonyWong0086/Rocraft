package com.rocraft.client;

import com.rocraft.RocraftConfig;
import com.rocraft.sim.McFrame;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Roblox "Classic" camera: orbits the character's head, scroll zoom 0.5-400 studs (12.5 default), first person at
 * the minimum, Poppercam-style pull-in when something is in the way. The character turns to face where it walks.
 * ponytail: mouse stays captured and always orbits (Roblox frees the cursor and orbits on right-drag).
 */
public final class RobloxCamera {
	static final float MIN = 0.5f, MAX = 400, DEFAULT = 12.5f;
	static float yaw, pitch = 15, zoom = DEFAULT;
	private static boolean wasFirst = true;

	public static float yaw() { return yaw; }
	public static float pitch() { return pitch; }

	public static boolean enabled() { return RocraftConfig.INSTANCE.robloxCamera && Minecraft.getInstance().player != null; }
	public static boolean firstPerson() { return zoom <= MIN + 1e-3f; }
	public static boolean orbiting() { return enabled() && !firstPerson(); }

	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -80, 80);
	}

	/** Mouse wheel: in towards first person, out to 400 studs. */
	public static void scroll(double notches) {
		var p = Minecraft.getInstance().player;
		boolean before = firstPerson();
		zoom = notches > 0 ? zoom / 1.25f : (before ? 2 : zoom * 1.25f);
		zoom = zoom < 1 ? MIN : Math.min(MAX, zoom);
		if (before && !firstPerson()) { yaw = p.getYRot(); pitch = p.getXRot(); }      // leaving first person
		if (!before && firstPerson()) { p.setYRot(yaw); p.setXRot(pitch); }            // entering it
	}

	/** Camera placement for this frame; null = first person (let vanilla do it). */
	public static Vec3 place(float partial) {
		var mc = Minecraft.getInstance();
		var p = mc.player;
		boolean first = firstPerson();
		var want = first ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
		if (mc.options.getCameraType() != want) mc.options.setCameraType(want);
		if (first) { wasFirst = true; return null; }
		if (wasFirst) { yaw = p.getYRot(); wasFirst = false; }
		Vec3 focus = p.getPosition(partial).add(0, 4.5 * McFrame.STUD, 0); // HumanoidRootPart + (0, 1.5, 0): the head
		Vec3 back = Vec3.directionFromRotation(pitch, yaw).scale(-zoom * McFrame.STUD);
		var hit = p.level().clip(new ClipContext(focus, focus.add(back), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
			return hit.getLocation().subtract(back.normalize().scale(0.1));
		return focus.add(back);
	}

	/**
	 * HD Admin / Adonis fly: the character's BodyGyro follows the camera, so while flying it faces exactly where the
	 * camera looks, pitch included (AvatarLayer tilts the body by it). Called every tick.
	 */
	public static void flyFacing() {
		var p = Minecraft.getInstance().player;
		if (p == null || !orbiting() || !p.getAbilities().flying) return;
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.setYBodyRot(yaw);
		p.setXRot(pitch);
	}

	/** WASD relative to the camera: the character faces the walk direction and moves forward. */
	public static Vec2 steer(Vec2 move) {
		var p = Minecraft.getInstance().player;
		if (p != null && p.getAbilities().flying) { flyFacing(); return move; } // admin fly: strafe relative to the camera
		if (move.lengthSquared() < 1e-4f || p == null || p.isPassenger()) return move; // riding: the vehicle reads raw W/S/A/D
		double r = Math.toRadians(yaw);
		double dx = move.y * -Math.sin(r) + move.x * Math.cos(r), dz = move.y * Math.cos(r) + move.x * Math.sin(r);
		float face = (float) Math.toDegrees(Math.atan2(-dx, dz));
		p.setYRot(face);
		p.setYHeadRot(face);
		p.setXRot(0);
		return new Vec2(0, Math.min(1, (float) Math.hypot(move.x, move.y)));
	}
}
