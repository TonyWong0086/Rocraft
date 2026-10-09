package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Trowel (WallMaker): at the mouse, facing the look direction snapped to an axis, build a wall 12 studs wide and
 * 4 high out of default Parts (4 x 1.2 x 2 studs), one every 0.04 s, all one BrickColor.Random(). Rows go up by the
 * brick height (0, 1.2, 2.4, 3.6) and each row is three bricks from -6 to +6 studs.
 */
public final class Trowel extends Item {
	static final double WALL_WIDTH = 12, WALL_HEIGHT = 4;

	public Trowel(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;
		Vec3 target = Tools.mouse(p), to = target.subtract(p.getEyePosition());
		// snap(): the wall faces the dominant horizontal axis of (target - head)
		boolean lookAlongX = Math.abs(to.x) > Math.abs(to.z);
		Vec3 look = lookAlongX ? new Vec3(Math.signum(to.x), 0, 0) : new Vec3(0, 0, Math.signum(to.z));
		Vec3 right = look.cross(new Vec3(0, 1, 0)); // CFrame.new(pos, pos + lookAt).RightVector
		int color = Launcher.randomBrickColor(p);
		com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.get("trowel.build")); // BuildSound = bass.wav
		double s = McFrame.STUD;
		int n = 0;
		for (double y = 0; y < WALL_HEIGHT; y += 1.2)
			for (double x = -WALL_WIDTH / 2; x < WALL_WIDTH / 2; x += 4) {
				// brick.CFrame = cf * CFrame.new(pos + brick.Size / 2); entity origin is the brick's bottom centre
				Vec3 at = target.add(right.scale((x + 2) * s)).add(0, y * s, 0).subtract(look.scale(1 * s));
				Tools.later(++n, () -> RobloxPart.place(sl, at, lookAlongX ? 2 : 4, 1.2f, lookAlongX ? 4 : 2, color, true, -1)); // wait(brickSpeed)
			}
		return InteractionResult.CONSUME;
	}
}
