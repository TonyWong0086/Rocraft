package com.rocraft.client.mixin;

import com.rocraft.client.RobloxCamera;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WASD relative to the Roblox camera; the character turns to face where it walks. */
@Mixin(KeyboardInput.class)
abstract class WalkMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void rocraft$steer(CallbackInfo ci) {
		if (!RobloxCamera.orbiting()) return;
		var in = (MoveAccessor) this;
		in.rocraft$move(RobloxCamera.steer(in.rocraft$move()));
	}
}
