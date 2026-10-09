package com.rocraft.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** 20x20 title-screen icon button (beside Friends) with the Roblox logo from the install; opens Rocraft settings. */
final class RocraftButton extends AbstractButton {
	private final Runnable action;

	RocraftButton(int x, int y, Runnable action) {
		super(x, y, 20, 20, Component.literal("Rocraft"));
		this.action = action;
		setTooltip(Tooltip.create(Component.literal("Rocraft settings")));
	}

	@Override public void onPress(InputWithModifiers input) { action.run(); }

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
		extractDefaultSprite(g);
		var logo = RobloxAssets.tex("textures/ui/TopBar/coloredlogo.png");
		if (logo != null) g.blit(RenderPipelines.GUI_TEXTURED, logo, getX() + 2, getY() + 2, 0, 0, 16, 16, 24, 24, 24, 24);
		else g.centeredText(net.minecraft.client.Minecraft.getInstance().font, "R", getX() + 10, getY() + 6, 0xFFFFFFFF);
	}

	@Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
