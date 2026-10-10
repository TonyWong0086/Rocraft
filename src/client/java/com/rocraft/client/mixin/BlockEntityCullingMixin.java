package com.rocraft.client.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block entity culling (Perf): chests, signs etc. walled off from the camera aren't drawn. Beacon beams and other
 * off-screen renderers are left alone (they reach far past their block). */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityCullingMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity, S extends BlockEntityRenderState> void rocraft$cull(E be, float partial, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean offScreen, CallbackInfoReturnable<S> cir) {
		if (offScreen) return;
		var renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(be);
		if (renderer != null && !renderer.shouldRenderOffScreen() && !com.rocraft.client.Perf.visible(be)) cir.setReturnValue(null);
	}
}
