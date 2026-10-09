package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.sim.McFrame;
import com.rocraft.tools.BombEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** The planted classic bomb: a 2-stud ball flashing Bright red (21) / Black (26) in step with BombEntity. */
final class BombRenderer extends EntityRenderer<BombEntity, EntityRenderState> {
	static final MeshDraw BALL = MeshDraw.ball(2);
	static final int BRIGHT_RED = 0xFFC4281C, BLACK = 0xFF1B2A35;

	BombRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public EntityRenderState createRenderState() { return new EntityRenderState(); }

	@Override
	public void submit(EntityRenderState s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		ps.pushPose();
		float k = (float) McFrame.STUD;
		ps.translate(0, k, 0);
		ps.scale(k, k, k);
		MeshDraw.submit(ps, out, s.lightCoords, BALL, BALL.texture(), BombEntity.red((int) s.ageInTicks) ? BRIGHT_RED : BLACK);
		ps.popPose();
		super.submit(s, ps, out, cam);
	}
}
