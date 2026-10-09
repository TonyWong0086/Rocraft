package com.rocraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rocraft.client.RobloxCamera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox's camera doesn't bob while walking. */
@Mixin(GameRenderer.class)
abstract class BobMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void rocraft$noBob(CameraRenderState cam, PoseStack ps, CallbackInfo ci) {
		if (RobloxCamera.enabled()) ci.cancel();
	}
}
