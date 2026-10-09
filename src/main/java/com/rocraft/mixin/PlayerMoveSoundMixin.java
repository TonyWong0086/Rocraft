package com.rocraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox characters make Roblox movement sounds (client CharacterSounds), so Minecraft's steps and swim strokes are off. */
@Mixin(Entity.class)
abstract class PlayerMoveSoundMixin {
	@Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
	private void rocraft$noSteps(CallbackInfo ci) { if ((Object) this instanceof Player) ci.cancel(); }

	@Inject(method = "waterSwimSound", at = @At("HEAD"), cancellable = true)
	private void rocraft$noSwim(CallbackInfo ci) { if ((Object) this instanceof Player) ci.cancel(); }
}
