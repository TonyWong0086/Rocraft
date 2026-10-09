package com.rocraft.client.mixin;

import com.rocraft.client.RobloxCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the Roblox camera orbits, mouse look turns the camera, not the character. */
@Mixin(Entity.class)
abstract class TurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void rocraft$orbit(double dx, double dy, CallbackInfo ci) {
		if ((Object) this == Minecraft.getInstance().player && RobloxCamera.orbiting()) { RobloxCamera.turn(dx, dy); ci.cancel(); }
	}
}
