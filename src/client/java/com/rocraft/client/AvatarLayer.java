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
		var helmet = player != null ? player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) : s.headEquipment;
		var hat = Rig.HATS.get(helmet.getItem()); // a Roblox hat in the helmet slot
		if (hat != null) draw(ps, out, light, pose[hat.part()], hat.draw(), hat.draw().texture());
		var g = Rig.gear(held);
		if (me && player != null && held.getItem() == Tools.clientUseItem && System.currentTimeMillis() < Tools.clientUseUntil && Rig.GEAR_ALT.containsKey(held.getItem()))
			g = Rig.GEAR_ALT.get(held.getItem());
		if (held.is(Tools.GREEN_BALLOON)) {
			int st = com.rocraft.tools.Balloon.state(held);
			if (st != 1 && Rig.VARIANTS.containsKey("green_balloon/" + st)) g = Rig.VARIANTS.get("green_balloon/" + st);
			// WeldArm: the right arm welded straight up holding the string, the left hanging at the side
			pose[Rig.RIGHT_ARM] = new Matrix4f(pose[Rig.TORSO]).rotateX((float) -Math.PI).translate(1.5f, -1.5f, 0);
			pose[Rig.LEFT_ARM] = new Matrix4f(pose[Rig.TORSO]).translate(-1.5f, 0, 0);
		}
		if (g != null) draw(ps, out, light, pose[g.part()], g.draw(), g.draw().texture());
		else if (body != null) heldItem(ps, out, light, pose[Rig.RIGHT_ARM], s.rightHandItemState, 1, s.outlineColor);
		if (body != null) heldItem(ps, out, light, pose[Rig.LEFT_ARM], s.leftHandItemState, -1, s.outlineColor);
		Vector3f above = ps.last().pose().transformPosition(new Vector3f(0, 3.1f, 0)); // just over the head, in camera space
		ps.popPose();
		if (player != null && RocraftConfig.INSTANCE.hud2018 && !(me && mc.options.getCameraType().isFirstPerson()))
			nameTag(ps, out, me ? prof.name : player.getName().getString(), player.getHealth() / player.getMaxHealth(), above);
	}

	/**
	 * Roblox humanoid name display: the name in white Legacy (Arial) with a soft dark shadow, a constant size on screen
	 * however far away (it's a GUI, not part of the world), and under it the green health bar (red background) once hurt.
	 */
	private static void nameTag(PoseStack ps, SubmitNodeCollector out, String name, float hp, Vector3f at) {
		var mc = Minecraft.getInstance();
		float dist = at.length();
		if (dist > 100 * 0.28f) return; // NameDisplayDistance 100 studs
		// world units per screen pixel at this distance, so 1 unit below = 1 screen pixel
		float perPx = (float) (2 * dist * Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / mc.getWindow().getHeight());
		ps.pushPose();
		ps.last().pose().identity().translate(at).rotate(mc.gameRenderer.mainCamera().rotation());
		ps.last().normal().identity();
		ps.scale(perPx, -perPx, perPx);
		int px = Math.max(14, mc.getWindow().getHeight() / 60), w = RbxFont.worldWidth(name, px, RbxFont.LEGACY);
		boolean hurt = hp < 1;
		float ty = -px - (hurt ? 10 : 2);
		RbxFont.world(ps, out, name, -w / 2f + 1, ty + 1, px, RbxFont.LEGACY, 0x99000000); // shadow
		RbxFont.world(ps, out, name, -w / 2f, ty, px, RbxFont.LEGACY, 0xFFFFFFFF);
		if (hurt) { // a short bar on a grey track, its fill fading green -> yellow -> red as health drops
			int bw = Math.max(36, px * 3), bh = Math.max(4, px / 4);
			float h = Math.max(0, hp), g = bw * h;
			bar(ps, out, -bw / 2f, -bh - 1, bw, bh, 0x99505050);
			if (g > 0) bar(ps, out, -bw / 2f, -bh - 1, g, bh, healthColor(h));
		}
		ps.popPose();
	}

	/**
	 * A Minecraft item in the Roblox hand: from the Roblox arm into the frame Minecraft's arm would have (pivot at the
	 * shoulder, 1/16-block pixels, y down), then ItemInHandLayer's own hand offset. side: 1 right, -1 left.
	 */
	private static void heldItem(PoseStack ps, SubmitNodeCollector out, int light, Matrix4f arm, net.minecraft.client.renderer.item.ItemStackRenderState item, int side, int outline) {
		if (item.isEmpty()) return;
		ps.pushPose();
		ps.mulPose(arm);
		ps.scale(-1 / S, -1 / S, 1 / S);       // Roblox studs -> Minecraft model blocks (x and y flipped)
		ps.translate(side / 16f, -4 / 16f, 0); // arm centre -> shoulder pivot (the arm box spans -2..10 px below it)
		ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
		ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
		ps.translate(side / 16f, 2 / 16f, -10 / 16f);
		item.submit(ps, out, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, outline);
		ps.popPose();
	}

	/** Roblox's health colour: green at full, yellow at half, red when nearly dead. */
	static int healthColor(float hp) {
		int green = 0x1BFC6B, yellow = 0xFFD21C, red = 0xFF1C00;
		return 0xFF000000 | (hp > 0.5f ? BombRenderer.lerp(yellow, green, (hp - 0.5f) * 2) : BombRenderer.lerp(red, yellow, hp * 2)) & 0xFFFFFF;
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
