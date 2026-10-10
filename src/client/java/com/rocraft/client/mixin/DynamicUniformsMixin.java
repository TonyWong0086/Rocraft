package com.rocraft.client.mixin;

import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** End of frame for Rocraft's mesh uniforms too (MeshGpu). */
@Mixin(DynamicUniforms.class)
abstract class DynamicUniformsMixin {
	@Inject(method = "reset", at = @At("TAIL"))
	private void rocraft$endFrame(CallbackInfo ci) { com.rocraft.client.RocraftClient.meshEndFrame(); }
}
