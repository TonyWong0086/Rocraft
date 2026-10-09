package com.rocraft.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Minecraft flames over the first-person view while burning; the character carries Roblox Fire instead. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenFireMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void rocraft$noFire(CallbackInfo ci) { ci.cancel(); }
}
