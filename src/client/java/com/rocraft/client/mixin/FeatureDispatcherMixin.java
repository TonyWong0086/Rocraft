package com.rocraft.client.mixin;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Rocraft's GPU mesh renderer (MeshGpu) to Minecraft's feature renderers. */
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureDispatcherMixin {
	@Shadow @Final private FeatureRendererMap featureRenderers;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void rocraft$meshes(CallbackInfo ci) { com.rocraft.client.RocraftClient.addMeshRenderer(featureRenderers); }
}
