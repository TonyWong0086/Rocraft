package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.Rocraft;
import com.rocraft.RocraftConfig;
import com.rocraft.tools.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the Roblox character: R6 meshes posed by Roblox animations (Animator), accessories, held gear, and its name. */
final class AvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	static final float S = 6 / 16f; // studs -> blocks (R6Model scale)

	AvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
		if (s.isInvisibleToPlayer) return;
		var mc = Minecraft.getInstance();
		Player player = mc.level != null && mc.level.getEntity(s.id) instanceof Player pl ? pl : null;
		boolean me = player == null || player == mc.player;
		RobloxProfile prof = me ? RocraftClient.profile : RocraftClient.guest;

		var held = s.rightHandItemStack;
		boolean gear = !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID);
		boolean lunging = player != null && me && System.currentTimeMillis() - Tools.clientLungeAt < 400;
		boolean flying = player != null && player.getAbilities().flying;
		Matrix4f[] pose = Animator.of(player == null ? -1 : s.id).pose(player, gear, held.is(Tools.LINKED_SWORD) && (s.attackTime > 0 || lunging), lunging, flying);

		ps.pushPose();
		ps.translate(0, 6 / 16f, 0); // HumanoidRootPart centre = torso centre, 6 units below the neck
		ps.scale(-S, -S, S);         // Roblox (x, y, z) -> model (-x, -y, z), studs -> blocks
		if (flying) ps.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-s.xRot))); // admin fly: BodyGyro.CFrame = camera, pitch too
		var body = Rig.body();
		if (body != null) {
			Identifier skin = AvatarSkin.textureId(prof);
			for (int i = 0; i < 6; i++)
				if (i == 0 && prof.head != null) draw(ps, out, light, pose[0], prof.head, prof.head.texture());
				else draw(ps, out, light, pose[i], body[i], skin);
		}
		for (var p : prof.accessories) draw(ps, out, light, pose[p.part()], p.draw(), p.draw().texture());
		var g = Rig.gear(held);
		if (me && player != null && held.getItem() == Tools.clientUseItem && System.currentTimeMillis() < Tools.clientUseUntil && Rig.GEAR_ALT.containsKey(held.getItem()))
			g = Rig.GEAR_ALT.get(held.getItem());
		if (g != null) draw(ps, out, light, pose[g.part()], g.draw(), g.draw().texture());
		Vector3f above = ps.last().pose().transformPosition(new Vector3f(0, 3.1f, 0)); // just over the head, in camera space
		ps.popPose();
		if (player != null && RocraftConfig.INSTANCE.hud2018 && !(me && mc.options.getCameraType().isFirstPerson()))
			nameTag(ps, out, me ? prof.name : player.getName().getString(), player.getHealth() / player.getMaxHealth(), above);
	}

	/**
	 * Roblox humanoid name display: the name in white SourceSans facing the camera, and under it the green health bar
	 * (red background) once the humanoid has been hurt.
	 */
	private static void nameTag(PoseStack ps, SubmitNodeCollector out, String name, float hp, Vector3f at) {
		ps.pushPose();
		ps.last().pose().identity().translate(at).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
		ps.last().normal().identity();
		ps.scale(0.0125f, -0.0125f, 0.0125f); // 1 unit = 1 font pixel, ~16 px per block
		int px = 32, w = RbxFont.width(name, px, true);
		RbxFont.world(ps, out, name, -w / 2f + 2, -px - 8 + 2, px, true, 0x80000000); // light text stroke
		RbxFont.world(ps, out, name, -w / 2f, -px - 8, px, true, 0xFFFFFFFF);
		if (hp < 1) {
			int bw = 100, bh = 10;
			float g = bw * Math.max(0, hp);
			bar(ps, out, -bw / 2f, 0, g, bh, 0xFF4B974B);
			bar(ps, out, -bw / 2f + g, 0, bw - g, bh, 0xFFC4281C);
		}
		ps.popPose();
	}

	private static void bar(PoseStack ps, SubmitNodeCollector out, float x, float y, float w, float h, int argb) {
		var white = BombRenderer.BALL.texture(); // a 1x1 white texture
		out.submitCustomGeometry(ps, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(white), (pose, vc) -> {
			float[][] q = {{x, y}, {x, y + h}, {x + w, y + h}, {x + w, y}};
			for (float[] k : q)
				vc.addVertex(pose, k[0], k[1], 0).setColor(argb).setUv(0.5f, 0.5f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
					.setLight(0xF000F0).setNormal(pose, 0, 0, -1);
		});
	}

	private static void draw(PoseStack ps, SubmitNodeCollector out, int light, Matrix4f part, MeshDraw d, Identifier tex) {
		ps.pushPose();
		ps.mulPose(part);
		MeshDraw.submit(ps, out, light, d, tex);
		ps.popPose();
	}
}
