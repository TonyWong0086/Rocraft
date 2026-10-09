package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.tools.Debris;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.joml.Quaternionf;

/** Explosion debris: the falling-block model, tumbling about its centre while it flies (Debris.spin). */
final class DebrisRenderer extends FallingBlockRenderer {
	static final class State extends FallingBlockRenderState { float spin; int axis; }

	DebrisRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public FallingBlockRenderState createRenderState() { return new State(); }

	@Override
	public void extractRenderState(FallingBlockEntity e, FallingBlockRenderState s, float partial) {
		super.extractRenderState(e, s, partial);
		if (e instanceof Debris d && s instanceof State st) {
			st.spin = d.spinO + (d.spin - d.spinO) * partial;
			st.axis = Math.floorMod(e.getId(), 3);
		}
	}

	@Override
	public void submit(FallingBlockRenderState s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		ps.pushPose();
		if (s instanceof State st) {
			float a = (float) Math.toRadians(st.spin);
			ps.translate(0, 0.5f, 0);
			ps.mulPose(st.axis == 0 ? new Quaternionf().rotateX(a) : st.axis == 1 ? new Quaternionf().rotateZ(a) : new Quaternionf().rotateX(a).rotateY(a * 0.5f));
			ps.translate(0, -0.5f, 0);
		}
		super.submit(s, ps, out, cam);
		ps.popPose();
	}
}
