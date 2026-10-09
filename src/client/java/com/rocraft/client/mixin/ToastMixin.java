package com.rocraft.client.mixin;

import com.rocraft.RocraftConfig;
import com.rocraft.client.RobloxNotify;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With the 2018 interface, "Advancement Made!" and "New Recipes Unlocked!" arrive as Roblox notifications. */
@Mixin(ToastManager.class)
abstract class ToastMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void rocraft$robloxNotification(Toast toast, CallbackInfo ci) {
		if (!RocraftConfig.INSTANCE.hud2018) return;
		if (toast instanceof AdvancementToast a) {
			var acc = (AdvancementToastAccessor) a;
			acc.rocraft$advancement().value().display().ifPresent(d ->
				RobloxNotify.show(d.getType().getDisplayName().getString(), d.getTitle().getString(), acc.rocraft$icon()));
			ci.cancel();
		} else if (toast instanceof RecipeToast) {
			RobloxNotify.show(Component.translatable("recipe.toast.title").getString(), Component.translatable("recipe.toast.description").getString(), null);
			ci.cancel();
		}
	}
}
