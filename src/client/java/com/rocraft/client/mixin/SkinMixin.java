package com.rocraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** You wear your Roblox avatar; everyone else is a 2018 Guest (their Minecraft skins don't fit the R6 rig). */
@Mixin(AbstractClientPlayer.class)
abstract class SkinMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void rocraft$robloxSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		boolean me = ((Object) this) == Minecraft.getInstance().player;
		cir.setReturnValue(com.rocraft.client.RocraftClient.avatarSkin(me, cir.getReturnValue()));
	}
}
