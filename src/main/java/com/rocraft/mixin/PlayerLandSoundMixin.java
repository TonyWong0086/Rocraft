package com.rocraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Minecraft landing thud for players: Roblox's Landing sound plays instead (CharacterSounds). */
@Mixin(LivingEntity.class)
abstract class PlayerLandSoundMixin {
	@Inject(method = "playBlockFallSound", at = @At("HEAD"), cancellable = true)
	private void rocraft$noThud(CallbackInfo ci) { if ((Object) this instanceof Player) ci.cancel(); }
}
