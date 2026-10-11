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
		var o = (Options) (Object) this;
		String id = com.rocraft.client.RocraftClient.ROBLOX_PACK;
		if (!o.resourcePacks.contains(id)) o.resourcePacks.add(id);
		// vanilla skips a pack for one launch when it comes off the incompatible list (an old pack format), so never leave it there
		o.incompatibleResourcePacks.remove(id);
	}
}
