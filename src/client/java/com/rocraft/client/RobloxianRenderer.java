package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rocraft.RocraftConfig;
import com.rocraft.tools.Robloxian;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;

/** A Robloxian mob: that user's real avatar (loaded once per username), posed by Roblox's animations, with its name. */
final class RobloxianRenderer extends EntityRenderer<Robloxian, RobloxianRenderer.State> {
	static final class State extends EntityRenderState {
		int entityId; String user; float bodyRot, attackTime, hp; ItemStack held = ItemStack.EMPTY;
	}
	/** username -> avatar; the Guest look until their account has loaded. */
	private static final Map<String, RobloxProfile> PROFILES = new ConcurrentHashMap<>();

	RobloxianRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 0.5f; }

	static RobloxProfile profile(String user) {
		return PROFILES.computeIfAbsent(user, u -> {
			Thread.startVirtualThread(() -> PROFILES.put(u, RobloxProfile.load(u, RocraftConfig.INSTANCE.apiKey)));
			return RocraftClient.guest;
		});
	}

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(Robloxian e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.entityId = e.getId();
		s.user = e.username;
		s.bodyRot = net.minecraft.util.Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		s.attackTime = e.getAttackAnim(partial);
		s.hp = e.getHealth() / e.getMaxHealth();
		s.held = e.getMainHandItem().copy();
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		var e = net.minecraft.client.Minecraft.getInstance().level.getEntity(s.entityId) instanceof Robloxian r ? r : null;
		var prof = profile(s.user);
		ps.pushPose();
		// the living-entity model space AvatarLayer draws in: facing the body yaw, y down, origin 1.5 blocks up
		ps.mulPose(Axis.YP.rotationDegrees(180 - s.bodyRot));
		ps.scale(-1, -1, 1);
		ps.translate(0, -1.501f, 0);
		var above = AvatarLayer.character(ps, out, s.lightCoords, e, s.entityId, prof, false, s.held, null, null, ItemStack.EMPTY, s.attackTime, 0, 0);
		ps.popPose();
		if (RocraftConfig.INSTANCE.hud2018 && s.hp > 0) AvatarLayer.nameTag(ps, out, prof.guest ? s.user : prof.name, s.hp, above);
		super.submit(s, ps, out, cam);
	}
}
