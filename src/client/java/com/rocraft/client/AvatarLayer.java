package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.Rocraft;
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

/** Draws the Roblox character: R6 meshes posed by Roblox animations (Animator), accessories, and held gear. */
final class AvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	static final float S = 6 / 16f; // studs -> blocks (R6Model scale)

	AvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
		if (s.isInvisibleToPlayer) return;
		var mc = Minecraft.getInstance();
		// no entity behind it = the Settings preview widget: show your avatar idling
		Player player = mc.level != null && mc.level.getEntity(s.id) instanceof Player pl ? pl : null;
		boolean me = player == null || player == mc.player;
		RobloxProfile prof = me ? RocraftClient.profile : RobloxProfile.GUEST_PROFILE;

		var held = s.rightHandItemStack;
		boolean gear = !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID);
		boolean lunging = player != null && me && System.currentTimeMillis() - Tools.clientLungeAt < 400;
		Matrix4f[] pose = Animator.of(player == null ? -1 : s.id).pose(player, gear, s.attackTime > 0 || lunging, lunging);

		ps.pushPose();
		ps.translate(0, 6 / 16f, 0); // HumanoidRootPart centre = torso centre, 6 units below the neck
		ps.scale(-S, -S, S);         // Roblox (x, y, z) -> model (-x, -y, z), studs -> blocks
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
		ps.popPose();
	}

	private static void draw(PoseStack ps, SubmitNodeCollector out, int light, Matrix4f part, MeshDraw d, Identifier tex) {
		ps.pushPose();
		ps.mulPose(part);
		MeshDraw.submit(ps, out, light, d, tex);
		ps.popPose();
	}
}
