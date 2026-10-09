package com.rocraft.client.mixin;

import com.rocraft.client.RobloxMouse;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Roblox cursor while the camera orbits (RobloxMouse): no grabbing, Roblox button meanings, right-drag orbit. */
@Mixin(MouseHandler.class)
abstract class MouseMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void rocraft$stayFree(CallbackInfo ci) {
		if (RobloxMouse.active() && !RobloxMouse.dragging()) ci.cancel();
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void rocraft$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (RobloxMouse.handles() && RobloxMouse.button(info.button(), action)) ci.cancel();
	}

	@Inject(method = "onMove", at = @At("HEAD"))
	private void rocraft$drag(long window, double x, double y, CallbackInfo ci) {
		if (RobloxMouse.active()) RobloxMouse.move(x, y);
	}
}
