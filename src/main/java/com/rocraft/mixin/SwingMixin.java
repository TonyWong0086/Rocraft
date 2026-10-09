package com.rocraft.mixin;

import com.rocraft.RbxSounds;
import com.rocraft.tools.Tools;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Roblox slashes on every click, hit or miss; a left click only reaches the server as a swing packet. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class SwingMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleAnimate", at = @At("TAIL"))
	private void rocraft$slash(ServerboundSwingPacket packet, CallbackInfo ci) {
		if (!player.getItemInHand(packet.getHand()).is(Tools.LINKED_SWORD) || Tools.justLunged(player)) return;
		RbxSounds.play(player, RbxSounds.SLASH);
		com.rocraft.tools.LinkedSword.slashed(player); // the blade hits for 10 for the next half second
	}
}
