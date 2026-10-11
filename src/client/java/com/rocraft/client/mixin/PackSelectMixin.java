package com.rocraft.client.mixin;

import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rocraft's Roblox asset pack is selected before Minecraft's first resource load, so items never load without their icons. */
@Mixin(Options.class)
abstract class PackSelectMixin {
	@Inject(method = "loadSelectedResourcePacks", at = @At("HEAD"))
	private void rocraft$selectRobloxPack(PackRepository repository, CallbackInfo ci) {
		var packs = ((Options) (Object) this).resourcePacks;
		if (!packs.contains(com.rocraft.client.RocraftClient.ROBLOX_PACK)) packs.add(com.rocraft.client.RocraftClient.ROBLOX_PACK);
	}
}
