package com.rocraft.client.mixin;

import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures the frame-rate limiter's sleep, so FrameStats sees work time only. */
@Mixin(FramerateLimiter.class)
abstract class FrameLimiterMixin {
	@Unique private static long rocraft$start;

	@Inject(method = "limitDisplayFPS", at = @At("HEAD"))
	private static void rocraft$start(int fps, CallbackInfo ci) { rocraft$start = System.nanoTime(); }

	@Inject(method = "limitDisplayFPS", at = @At("RETURN"))
	private static void rocraft$end(int fps, CallbackInfo ci) { com.rocraft.client.FrameStats.waited(System.nanoTime() - rocraft$start); }
}
