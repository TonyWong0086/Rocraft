package com.rocraft.client.mixin;

import com.rocraft.client.MouseAim;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With the free Roblox cursor, mining, placing and using act on what's under the mouse, not the screen centre. */
@Mixin(Minecraft.class)
abstract class PickMixin {
	@Inject(method = "pick", at = @At("TAIL"))
	private void rocraft$cursorPick(float partial, CallbackInfo ci) { MouseAim.pickUnderCursor((Minecraft) (Object) this, partial); }
}
