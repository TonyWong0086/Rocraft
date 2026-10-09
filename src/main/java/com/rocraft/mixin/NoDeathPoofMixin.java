package com.rocraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox characters don't vanish in a smoke poof when they die. */
@Mixin(LivingEntity.class)
abstract class NoDeathPoofMixin {
	@Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
	private void rocraft$noPoof(CallbackInfo ci) { if ((Object) this instanceof Player) ci.cancel(); }
}
