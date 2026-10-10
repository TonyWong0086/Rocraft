package com.rocraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players' held items are drawn by AvatarLayer in the Roblox hand, not on the hidden Minecraft arm. */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void rocraft$robloxHand(ArmedEntityRenderState s, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack ps, SubmitNodeCollector out, int light, CallbackInfo ci) {
		if (s instanceof AvatarRenderState && com.rocraft.client.RocraftClient.robloxAvatar()) ci.cancel();
	}
}
