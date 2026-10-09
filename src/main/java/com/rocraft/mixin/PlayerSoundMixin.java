package com.rocraft.mixin;

import com.rocraft.RbxSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 2018 Roblox: silent when hurt, "oof" on death. */
@Mixin(Player.class)
abstract class PlayerSoundMixin {
	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void rocraft$oof(CallbackInfoReturnable<SoundEvent> cir) { cir.setReturnValue(RbxSounds.OOF); }

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void rocraft$silentHurt(DamageSource src, CallbackInfoReturnable<SoundEvent> cir) { cir.setReturnValue(null); }
}
