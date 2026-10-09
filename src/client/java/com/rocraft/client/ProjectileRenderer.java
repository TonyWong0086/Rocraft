package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.Rocraft;
import com.rocraft.rbx.RbxMesh;
import com.rocraft.sim.McFrame;
import com.rocraft.tools.Projectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/** Superball / pellet balls in their BrickColor, and the rocket: Roblox mesh 2251534 at Scale (0.35, 0.35, 0.25), facing its flight. */
final class ProjectileRenderer extends EntityRenderer<Projectile, ProjectileRenderer.State> {
	static final class State extends EntityRenderState { int kind, color; float yaw, pitch; }
	static final MeshDraw BALL2 = MeshDraw.ball(2), BALL1 = MeshDraw.ball(1);
	private static volatile MeshDraw rocket;
	private static boolean rocketTried;

	ProjectileRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(Projectile e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.kind = e.kind();
		s.color = e.color();
		var v = e.getDeltaMovement();
		s.yaw = (float) Math.atan2(v.x, v.z);
		s.pitch = (float) Math.atan2(v.y, Math.hypot(v.x, v.z));
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		float k = (float) McFrame.STUD;
		ps.pushPose();
		ps.translate(0, 0.15f, 0);
		ps.scale(k, k, k);
		if (s.kind == Projectile.ROCKET) {
			var m = rocket();
			if (m != null) {
				// mesh points along -Z (CFrame.new(pos, target)); turn -Z to the velocity
				ps.mulPose(new Matrix4f().rotateY(s.yaw + (float) Math.PI).rotateX(s.pitch));
				MeshDraw.submit(ps, out, s.lightCoords, m, m.texture(), 0xFFF2F3F3); // BrickColor = TeamColor (White when neutral)
			}
		} else {
			var b = s.kind == Projectile.SUPERBALL ? BALL2 : BALL1;
			MeshDraw.submit(ps, out, s.lightCoords, b, b.texture(), s.color);
		}
		ps.popPose();
		super.submit(s, ps, out, cam);
	}

	private static MeshDraw rocket() {
		if (!rocketTried) {
			rocketTried = true;
			Thread.startVirtualThread(() -> {
				try { rocket = MeshDraw.of(RbxMesh.read(RobloxApi.asset(2251534L)), new Matrix4f().scale(0.35f, 0.35f, 0.25f), null); }
				catch (Exception e) { Rocraft.LOGGER.warn("rocket mesh unavailable: {}", e.toString()); }
			});
		}
		return rocket;
	}
}
