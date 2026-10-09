package com.rocraft.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Explosion debris (tools.Debris) is a falling block whose state is set directly. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockAccessor {
	@Accessor("blockState") void rocraft$setBlockState(BlockState state);
}
