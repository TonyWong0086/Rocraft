package com.rocraft.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Unfocused FPS cap (Perf). */
@Mixin(FramerateLimitTracker.class)
abstract class FpsMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void rocraft$unfocused(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(com.rocraft.client.Perf.framerateLimit(cir.getReturnValueI())); }
}
