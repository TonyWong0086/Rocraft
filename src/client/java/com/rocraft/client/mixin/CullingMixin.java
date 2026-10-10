package com.rocraft.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity culling (Perf): entities walled off from the camera aren't drawn. */
@Mixin(EntityRenderDispatcher.class)
abstract class CullingMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void rocraft$cull(E e, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !com.rocraft.client.Perf.visible(e)) cir.setReturnValue(false);
	}
}
