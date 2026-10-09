package com.rocraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox characters kick up no sprint dust. */
@Mixin(Entity.class)
abstract class NoSprintDustMixin {
	@Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void rocraft$noDust(CallbackInfo ci) { if ((Object) this instanceof Player) ci.cancel(); }
}
