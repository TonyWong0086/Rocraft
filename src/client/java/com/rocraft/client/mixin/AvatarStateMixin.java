package com.rocraft.client.mixin;

import com.rocraft.RocraftConfig;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox characters: burning shows Roblox Fire (GearEffects), and the name is drawn Roblox-style (AvatarLayer). */
@Mixin(AvatarRenderer.class)
abstract class AvatarStateMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void rocraft$roblox(Avatar e, AvatarRenderState s, float partial, CallbackInfo ci) {
		s.displayFireAnimation = false;
		if (RocraftConfig.INSTANCE.hud2018) s.nameTag = null;
	}
}
