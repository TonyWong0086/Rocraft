package com.rocraft.client.mixin;

import com.rocraft.RocraftConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With the 2018 interface on, the chat screen only takes the typing; Rocraft's Roblox chat draws the window and bar. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
	@Shadow protected EditBox input;

	@Inject(method = "init", at = @At("TAIL"))
	private void rocraft$hideInput(CallbackInfo ci) {
		if (RocraftConfig.INSTANCE.hud2018) input.setX(-10000); // still focused and typing, just not drawn on screen
	}

	// @Redirect here crashes MixinExtras 0.5.4 (FactoryRedirectWrapper ClassCastException), so skip the whole draw instead
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void rocraft$noVanillaChat(CallbackInfo ci) {
		if (RocraftConfig.INSTANCE.hud2018) ci.cancel();
	}
}
