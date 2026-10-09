package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rocraft.sim.McFrame;
import com.rocraft.tools.Hoverboard;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** The dropped Rolling Hoverboard: the tool's own board mesh, long side across, rolling along the board's LookVector. */
final class HoverboardRenderer extends EntityRenderer<Hoverboard, HoverboardRenderer.State> {
	static final class State extends EntityRenderState { float yRot; }

	HoverboardRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(Hoverboard e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.yRot = e.getYRot(partial);
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		var d = Rig.HANDLES.get("hoverboard");
		if (d != null) {
			float k = (float) McFrame.STUD;
			ps.pushPose();
			ps.mulPose(Axis.YP.rotationDegrees(180 - s.yRot)); // Roblox LookVector (-Z) -> the entity's facing
			ps.scale(k, k, k);
			ps.translate(0, 0.5f, 0); // Handle is 1 stud tall, centred
			MeshDraw.submit(ps, out, s.lightCoords, d, d.texture());
			ps.popPose();
		}
		super.submit(s, ps, out, cam);
	}
}
