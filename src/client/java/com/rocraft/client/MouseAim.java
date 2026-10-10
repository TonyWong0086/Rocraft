package com.rocraft.client;

import com.rocraft.Rocraft;
import com.rocraft.tools.MouseTarget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Roblox Mouse.Hit: the world point under the cursor (screen centre in first person). */
public final class MouseAim {
	private static Vec3 last = Vec3.ZERO;

	/** While holding gear, tell the server where the mouse points (tools aim there). */
	static void tick(Minecraft mc) {
		var held = mc.player.getMainHandItem();
		if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID)) return;
		Vec3 from = mc.gameRenderer.mainCamera().position(), to = from.add(ray(mc).scale(400 * 0.28));
		Vec3 target = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player)).getLocation();
		if (target.distanceToSqr(last) > 0.0025) {
			last = target;
			ClientPlayNetworking.send(new MouseTarget(target));
		}
	}

	/** Unit ray from the camera through the cursor (the view direction when the cursor is captured). */
	static Vec3 ray(Minecraft mc) {
		var cam = mc.gameRenderer.mainCamera();
		var win = mc.getWindow();
		double nx = 0, ny = 0;
		if (RobloxMouse.active()) {
			nx = mc.mouseHandler.xpos() / win.getScreenWidth() * 2 - 1;
			ny = 1 - mc.mouseHandler.ypos() / win.getScreenHeight() * 2;
		}
		double t = Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov()) / 2), aspect = (double) win.getWidth() / win.getHeight();
		var f = cam.forwardVector();
		var u = cam.upVector();
		var l = cam.leftVector();
		return new Vec3(f.x() + u.x() * ny * t - l.x() * nx * t * aspect, f.y() + u.y() * ny * t - l.y() * nx * t * aspect,
			f.z() + u.z() * ny * t - l.z() * nx * t * aspect).normalize();
	}

	/**
	 * Replaces Minecraft's crosshair pick while the Roblox cursor is free: the block or entity under the mouse, if the
	 * character can reach it (Minecraft's own interaction ranges, measured from the character as the server checks).
	 */
	public static void pickUnderCursor(Minecraft mc, float partial) {
		if (!RobloxMouse.active() || mc.player == null || mc.level == null) return;
		var p = mc.player;
		Vec3 from = mc.gameRenderer.mainCamera().position(), dir = ray(mc);
		double len = from.distanceTo(p.getEyePosition(partial)) + Math.max(p.blockInteractionRange(), p.entityInteractionRange()) + 1;
		Vec3 to = from.add(dir.scale(len));
		BlockHitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		var entity = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1),
			e -> e != p && !e.isSpectator() && e.isPickable(), from.distanceToSqr(end));
		if (entity != null && p.isWithinEntityInteractionRange(entity.getEntity().getBoundingBox(), 0)) {
			mc.hitResult = entity;
			mc.crosshairPickEntity = entity.getEntity();
			return;
		}
		mc.crosshairPickEntity = null;
		mc.hitResult = block.getType() == HitResult.Type.BLOCK && p.isWithinBlockInteractionRange(block.getBlockPos(), 0) ? block
			: BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
	}
}
