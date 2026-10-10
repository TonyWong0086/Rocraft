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
		var helmet = player != null ? player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) : s.headEquipment;
		Vector3f above = character(ps, out, light, player, s.id, prof, me, s.rightHandItemStack, s.rightHandItemState, s.leftHandItemState,
			helmet, s.attackTime, s.xRot, s.outlineColor);
		if (player != null && RocraftConfig.INSTANCE.hud2018 && !(me && mc.options.getCameraType().isFirstPerson()))
			nameTag(ps, out, me ? prof.name : player.getName().getString(), player.getHealth() / player.getMaxHealth(), above);
	}

	/**
	 * A Roblox character in model space (Minecraft's living-entity pose: y down, origin 1.5 blocks above the feet):
	 * body, accessories, helmet-slot hat, held gear or item. Players and Robloxian mobs both draw through here.
	 * Returns the point just over the head in camera space, for the name display.
	 */
	static Vector3f character(PoseStack ps, SubmitNodeCollector out, int light, net.minecraft.world.entity.LivingEntity e, int id, RobloxProfile prof, boolean me,
			net.minecraft.world.item.ItemStack held, net.minecraft.client.renderer.item.ItemStackRenderState rightState,
			net.minecraft.client.renderer.item.ItemStackRenderState leftState, net.minecraft.world.item.ItemStack helmet, float attackTime, float pitch, int outline) {
		boolean gear = !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID);
		boolean lunging = e instanceof Player && me && System.currentTimeMillis() - Tools.clientLungeAt < 400;
		boolean flying = e instanceof Player pl && pl.getAbilities().flying;
		Matrix4f[] pose = Animator.of(e == null ? -1 : id).pose(e, gear, held.is(Tools.LINKED_SWORD) && (attackTime > 0 || lunging), lunging, flying);

		ps.pushPose();
		ps.translate(0, 6 / 16f, 0); // HumanoidRootPart centre = torso centre, 6 units below the neck
		ps.scale(-S, -S, S);         // Roblox (x, y, z) -> model (-x, -y, z), studs -> blocks
		if (flying) ps.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-pitch))); // admin fly: BodyGyro.CFrame = camera, pitch too
		var body = Rig.body();
		if (body != null) {
			Identifier skin = AvatarSkin.textureId(prof);
			for (int i = 0; i < 6; i++)
				if (i == 0 && prof.head != null) draw(ps, out, light, pose[0], prof.head, prof.head.texture());
				else draw(ps, out, light, pose[i], prof.bodyParts[i] != null ? prof.bodyParts[i] : body[i], skin);
		}
		for (var p : prof.accessories) draw(ps, out, light, pose[p.part()], p.draw(), p.draw().texture());
		var hat = Rig.HATS.get(helmet.getItem()); // a Roblox hat in the helmet slot
		if (hat != null) draw(ps, out, light, pose[hat.part()], hat.draw(), hat.draw().texture());
		var g = Rig.gear(held);
		if (me && e instanceof Player && held.getItem() == Tools.clientUseItem && System.currentTimeMillis() < Tools.clientUseUntil && Rig.GEAR_ALT.containsKey(held.getItem()))
			g = Rig.GEAR_ALT.get(held.getItem());
		if (held.is(Tools.GREEN_BALLOON)) {
			int st = com.rocraft.tools.Balloon.state(held);
			if (st != 1 && Rig.VARIANTS.containsKey("green_balloon/" + st)) g = Rig.VARIANTS.get("green_balloon/" + st);
			// WeldArm: the right arm welded straight up holding the string, the left hanging at the side
			pose[Rig.RIGHT_ARM] = new Matrix4f(pose[Rig.TORSO]).rotateX((float) -Math.PI).translate(1.5f, -1.5f, 0);
			pose[Rig.LEFT_ARM] = new Matrix4f(pose[Rig.TORSO]).translate(-1.5f, 0, 0);
		}
		if (g != null) draw(ps, out, light, pose[g.part()], g.draw(), g.draw().texture());
		else if (body != null && rightState != null) heldItem(ps, out, light, pose[Rig.RIGHT_ARM], rightState, 1, outline);
		if (body != null && leftState != null) heldItem(ps, out, light, pose[Rig.LEFT_ARM], leftState, -1, outline);
		Vector3f above = ps.last().pose().transformPosition(new Vector3f(0, 3.1f, 0)); // just over the head, in camera space
		ps.popPose();
		return above;
	}

	/**
	 * Roblox humanoid name display: the name in white Legacy (Arial) with a soft dark shadow, a constant size on screen
	 * however far away (it's a GUI, not part of the world), and under it the green health bar (red background) once hurt.
	 */
	static void nameTag(PoseStack ps, SubmitNodeCollector out, String name, float hp, Vector3f at) {
		var mc = Minecraft.getInstance();
		float dist = at.length(), stud = 0.28f;
		// NameDisplayDistance 100 studs: fades out over the last 20 (and back in as you come closer)
		float fade = Math.clamp((100 * stud - dist) / (20 * stud), 0, 1);
		if (fade <= 0) return;
		// world units per screen pixel at this distance: 1 unit below = 1 screen pixel, so it keeps one size on screen
		float perPx = (float) (2 * dist * Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / mc.getWindow().getHeight());
		int px = Math.max(16, mc.getWindow().getHeight() / 50), w = RbxFont.worldWidth(name, px, "r");
		boolean hurt = hp < 1;
		// Roblox's bar, measured: ~6x the text height wide, 1 px border + 2 px gap + 2 px fill (scaled with the text)
		int k = Math.max(1, Math.round(px / 17f)), bw = Math.round(px * 6f), bh = 8 * k;
		float ty = -px - (hurt ? bh + 3 : 2), h = Math.max(0, hp), x0 = -bw / 2f, y0 = -bh;
		int a = Math.round(fade * 255);
		// every layer sits a hair nearer the camera than the one before, so stroke / text and border / fill never z-fight
		layer(ps, at, perPx, 0, () -> RbxFont.world(ps, out, name, -w / 2f, ty, px, "r", 0, alpha(0x50000000, a)));
		layer(ps, at, perPx, 1, () -> RbxFont.world(ps, out, name, -w / 2f, ty, px, "r", alpha(0xFFFFFFFF, a), 0));
		if (!hurt) return;
		var white = net.minecraft.client.renderer.rendertype.RenderTypes.text(BombRenderer.BALL.texture()); // a 1x1 white texture
		int col = alpha(healthColor(h), a);
		// translucent dark grey inside, then the border and the fill both in the health colour (corners clipped by 1 px)
		layer(ps, at, perPx, 0, () -> out.submitCustomGeometry(ps, white, (pose, vc) -> rect(vc, pose, x0 + k, y0 + k, bw - 2 * k, bh - 2 * k, alpha(0x80646464, a))));
		layer(ps, at, perPx, 1, () -> out.submitCustomGeometry(ps, white, (pose, vc) -> {
			rect(vc, pose, x0 + k, y0, bw - 2 * k, k, col);           // top
			rect(vc, pose, x0 + k, y0 + bh - k, bw - 2 * k, k, col);  // bottom
			rect(vc, pose, x0, y0 + k, k, bh - 2 * k, col);           // left
			rect(vc, pose, x0 + bw - k, y0 + k, k, bh - 2 * k, col);  // right
			if (h > 0) rect(vc, pose, x0 + 3 * k, y0 + 3 * k, (bw - 6 * k) * h, bh - 6 * k, col);
		}));
	}

	private static void rect(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, float x, float y, float w, float h, int argb) {
		quad(vc, pose, argb, x, y, x, y + h, x + w, y + h, x + w, y);
	}

	private static int alpha(int argb, int a) { return ((argb >>> 24) * a / 255) << 24 | argb & 0xFFFFFF; }

	/** Billboard pose at `at` (camera space), pulled `k` steps toward the camera. */
	private static void layer(PoseStack ps, Vector3f at, float perPx, int k, Runnable draw) {
		ps.pushPose();
		float pull = 1 - k * 0.002f;
		ps.last().pose().identity().translate(at.x * pull, at.y * pull, at.z * pull).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
		ps.last().normal().identity();
		ps.scale(perPx * pull, -perPx * pull, perPx * pull);
		draw.run();
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
		int green = 0x1BFC6B, yellow = 0xFAEB00, red = 0xFF1C00;
		return 0xFF000000 | (hp > 0.5f ? BombRenderer.lerp(yellow, green, (hp - 0.5f) * 2) : BombRenderer.lerp(red, yellow, hp * 2)) & 0xFFFFFF;
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, int argb, float... p) {
		for (int i = 0; i < 4; i++) vc.addVertex(pose, p[i * 2], p[i * 2 + 1], 0).setColor(argb).setUv(0.5f, 0.5f).setLight(0xF000F0);
	}

	private static void draw(PoseStack ps, SubmitNodeCollector out, int light, Matrix4f part, MeshDraw d, Identifier tex) {
		ps.pushPose();
		ps.mulPose(part);
		MeshDraw.submit(ps, out, light, d, tex);
		ps.popPose();
	}
}
