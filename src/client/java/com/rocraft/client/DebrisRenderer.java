package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.tools.Debris;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/**
 * Explosion debris: the block's model turned about its centre by its tumble (Debris.rot), lit evenly by the light
 * where the debris is (like a block in a minecart). The falling-block renderer lights each face from the world cell
 * beside it, which goes black once the block has rolled over (its old bottom face, lit from the ground, ends up on top or the side).
 */
final class DebrisRenderer extends EntityRenderer<Debris, DebrisRenderer.State> {
	static final class State extends EntityRenderState {
		final Quaternionf rot = new Quaternionf();
		final BlockModelRenderState block = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	DebrisRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		blocks = ctx.getBlockModelResolver();
		shadowRadius = 0.5f;
	}

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(Debris d, State s, float partial) {
		super.extractRenderState(d, s, partial);
		d.rotO.slerp(d.rot, partial, s.rot);
		blocks.update(s.block, d.getBlockState(), AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		ps.pushPose();
		ps.translate(0, 0.5f, 0);
		ps.mulPose(s.rot);
		ps.translate(-0.5f, -0.5f, -0.5f);
		s.block.submit(ps, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
		ps.popPose();
		super.submit(s, ps, out, cam);
	}
}
