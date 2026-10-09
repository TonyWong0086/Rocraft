package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.sim.McFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Green Balloon (BalloonScript): while held, a BodyForce lifts the whole character, 1.05 x its weight x 0.98 for 3 s
 * then x 0.92 for 2 s, so it drifts up in slow surges. The balloon swells (Mesh.Scale 2, then 3) as it nears 150 studs
 * above where it was equipped, and pops there: Pop sound, popped mesh, no more lift. Jumping is off while it's held
 * (its LocalScript cancels Humanoid.Jumping). Re-equipping gives a fresh balloon.
 * The state the renderer needs rides on the stack: custom_data "balloon" = 1, 2, 3 (scale) or 0 (popped).
 */
public final class Balloon {
	static final double MAX_RISE = 150;
	private record State(double startY, int t0) {}
	private static final Map<UUID, State> STATE = new HashMap<>();

	static void equipped(ServerPlayer p) {
		STATE.put(p.getUUID(), new State(p.getY(), p.tickCount));
		set(p.getMainHandItem(), 1);
	}

	static void tick(ServerPlayer p, boolean held) {
		ItemStack stack = p.getMainHandItem();
		State st = STATE.get(p.getUUID());
		boolean up = held && st != null && state(stack) != 0;
		double lift = 0;
		if (up) {
			double risen = (p.getY() - st.startY) / McFrame.STUD;
			if (risen > MAX_RISE) {
				set(stack, 0);
				RbxSounds.play(p, RbxSounds.get("green_balloon.pop"));
				up = false;
			} else {
				double range = MAX_RISE - risen;
				set(stack, range > 100 ? 1 : range > 50 ? 2 : 3);
				lift = 1.05 * ((p.tickCount - st.t0) % 100 < 60 ? 0.98 : 0.92);
			}
		}
		Tools.coil(p, Attributes.GRAVITY, "green_balloon", up, -lift);
		Tools.coil(p, Attributes.JUMP_STRENGTH, "green_balloon_jump", held, -1);
	}

	public static int state(ItemStack s) { return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("balloon", 1); }

	private static void set(ItemStack s, int v) {
		if (state(s) != v || !s.has(DataComponents.CUSTOM_DATA)) CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt("balloon", v));
	}
}
