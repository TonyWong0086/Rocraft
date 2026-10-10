package com.rocraft.client.mixin;

import com.rocraft.RocraftConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every frame's time goes to FrameStats; with the 2018 interface, F3 shows Roblox's stats panels instead. */
@Mixin(DebugScreenOverlay.class)
abstract class DebugOverlayMixin {
	@Inject(method = "logFrameDuration", at = @At("HEAD"))
	private void rocraft$frame(long nanos, CallbackInfo ci) { com.rocraft.client.FrameStats.frame(nanos); }

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void rocraft$robloxStats(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!RocraftConfig.INSTANCE.hud2018) return;
		ci.cancel();
		if (net.minecraft.client.Minecraft.getInstance().debugEntries.isOverlayVisible()) com.rocraft.client.RobloxStats.draw(g); // the full F3, not the always-on entries
	}
}
