package com.rocraft.client.mixin;

import com.rocraft.client.RobloxCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox orbit camera replaces vanilla third person (RobloxCamera). */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow private boolean detached;
	@Shadow protected abstract void setPosition(double x, double y, double z);
	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void rocraft$orbit(float partial, CallbackInfo ci) {
		if (!RobloxCamera.enabled()) return;
		var pos = RobloxCamera.place(partial);
		if (pos == null) return;
		detached = true;
		setRotation(RobloxCamera.yaw(), RobloxCamera.pitch());
		setPosition(pos.x, pos.y, pos.z);
	}
}
