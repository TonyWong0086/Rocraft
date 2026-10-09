package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.sim.McFrame;
import com.rocraft.tools.BombEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * The planted classic bomb: a 2-stud ball with Reflectance = 1 (PlantBomb script). At reflectance 1 Roblox shows only
 * the sky reflected in the ball, so the script's Black/Bright red BrickColor flips never show; it stays a mirror ball.
 */
final class BombRenderer extends EntityRenderer<BombEntity, EntityRenderState> {
	static final MeshDraw BALL = MeshDraw.ball(2);
	static final int ZENITH = 0x5E8FD6, HORIZON = 0xDCE7F2, GROUND = 0x4B463E; // Roblox's default sky, mirrored

	BombRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

	@Override public EntityRenderState createRenderState() { return new EntityRenderState(); }

	@Override
	public void submit(EntityRenderState s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		ps.pushPose();
		float k = (float) McFrame.STUD;
		ps.translate(0, k, 0);
		ps.scale(k, k, k);
		var d = BALL;
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(d.texture()), (pose, vc) -> {
			var v = new Vector3f();
			var n = new Vector3f();
			for (int t = 0; t < d.count; t += 3)
				for (int c = 0; c < 4; c++) {
					int i = t + Math.min(c, 2);
					float x = d.pos[i * 3], y = d.pos[i * 3 + 1], z = d.pos[i * 3 + 2];
					// camera sits at the origin of this pose: reflect the view ray about the normal, look the sky up
					pose.pose().transformPosition(v.set(x, y, z)).normalize();
					pose.transformNormal(d.nrm[i * 3], d.nrm[i * 3 + 1], d.nrm[i * 3 + 2], n);
					float ry = v.y - 2 * v.dot(n) * n.y;
					vc.addVertex(pose, x, y, z).setColor(sky(ry)).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(0xF000F0).setNormal(pose, d.nrm[i * 3], d.nrm[i * 3 + 1], d.nrm[i * 3 + 2]);
				}
		});
		ps.popPose();
		super.submit(s, ps, out, cam);
	}

	/** Sky colour seen along a reflected ray with height ry (-1 down .. 1 up). */
	static int sky(float ry) {
		return ry >= 0 ? lerp(HORIZON, ZENITH, (float) Math.sqrt(ry)) : lerp(HORIZON, GROUND, Math.min(1, -ry * 4));
	}

	static int lerp(int a, int b, float t) {
		int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
		int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
