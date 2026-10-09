package com.rocraft.client;

import com.rocraft.Rocraft;
import com.rocraft.tools.MouseTarget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** Roblox Mouse.Hit: the world point under the cursor (screen centre in first person), sent while holding gear. */
final class MouseAim {
	private static Vec3 last = Vec3.ZERO;

	static void tick(Minecraft mc) {
		var held = mc.player.getMainHandItem();
		if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID)) return;
		var cam = mc.gameRenderer.mainCamera();
		var win = mc.getWindow();
		double nx = 0, ny = 0;
		if (RobloxMouse.active()) {
			nx = mc.mouseHandler.xpos() / win.getScreenWidth() * 2 - 1;
			ny = 1 - mc.mouseHandler.ypos() / win.getScreenHeight() * 2;
		}
		double t = Math.tan(Math.toRadians(mc.options.fov().get()) / 2), aspect = (double) win.getWidth() / win.getHeight();
		var f = cam.forwardVector();
		var u = cam.upVector();
		var l = cam.leftVector();
		Vec3 dir = new Vec3(f.x() + u.x() * ny * t - l.x() * nx * t * aspect, f.y() + u.y() * ny * t - l.y() * nx * t * aspect,
			f.z() + u.z() * ny * t - l.z() * nx * t * aspect).normalize();
		Vec3 from = cam.position(), to = from.add(dir.scale(400 * 0.28));
		var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
		Vec3 target = hit.getLocation();
		if (target.distanceToSqr(last) > 0.0025) {
			last = target;
			ClientPlayNetworking.send(new MouseTarget(target));
		}
	}
}
