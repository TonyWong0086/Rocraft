package com.rocraft.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
interface MoveAccessor {
	@Accessor("moveVector") Vec2 rocraft$move();
	@Accessor("moveVector") void rocraft$move(Vec2 v);
}
