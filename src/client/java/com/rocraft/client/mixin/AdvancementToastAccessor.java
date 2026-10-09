package com.rocraft.client.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
	@Accessor("advancement") AdvancementHolder rocraft$advancement();
	@Accessor("iconItem") ItemStack rocraft$icon();
}
