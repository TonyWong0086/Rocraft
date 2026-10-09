package com.rocraft.client.mixin;

import com.rocraft.client.RobloxCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In game, the mouse wheel zooms the Roblox camera (hotbar is on the number keys, like Roblox). */
@Mixin(MouseHandler.class)
abstract class ScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void rocraft$zoom(long window, double xOff, double yOff, CallbackInfo ci) {
		if (Minecraft.getInstance().gui.screen() == null && RobloxCamera.enabled() && yOff != 0) { RobloxCamera.scroll(yOff); ci.cancel(); }
	}
}
