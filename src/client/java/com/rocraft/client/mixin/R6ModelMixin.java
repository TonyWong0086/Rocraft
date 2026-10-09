package com.rocraft.client.mixin;

import com.rocraft.client.R6Model;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every player is an R6 Robloxian (wide and slim alike); skins come from AvatarSkin. */
@Mixin(PlayerModel.class)
abstract class R6ModelMixin {
	@Inject(method = "createMesh", at = @At("HEAD"), cancellable = true)
	private static void rocraft$r6(CubeDeformation def, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
		cir.setReturnValue(R6Model.mesh());
	}
}
