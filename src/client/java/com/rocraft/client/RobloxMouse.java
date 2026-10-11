package com.rocraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rocraft.Rocraft;
import com.rocraft.tools.Tools;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Roblox mouse while the camera orbits: a free cursor drawn with Roblox's ArrowFarCursor, right-drag rotates the
 * camera, left click activates the held tool (Tool.Activated), the Linked Sword lunges on a double click, and a
 * right click that doesn't drag still does Minecraft's "use" (placing blocks etc.).
 */
public final class RobloxMouse {
	private static long cursor = -1;
	private static boolean dragging, dragged;
	private static float dragX, dragY;
	private static double moved;
	private static long lastLeftClick;

	/** Free-cursor mode: orbit camera, no screen open. */
	public static boolean active() {
		var mc = Minecraft.getInstance();
		return RobloxCamera.orbiting() && mc.gui.screen() == null;
	}

	public static boolean dragging() { return dragging; }

	/** Roblox button meanings apply in first and third person while the Roblox camera is on. */
	public static boolean handles() {
		var mc = Minecraft.getInstance();
		return RobloxCamera.enabled() && mc.gui.screen() == null && mc.player != null;
	}

	/** Every client tick: keep the cursor free and dressed as Roblox's arrow. */
	public static void tick() {
		var mc = Minecraft.getInstance();
		long win = mc.getWindow().handle();
		if (active()) {
			if (mc.mouseHandler.isMouseGrabbed() && !dragging) mc.mouseHandler.releaseMouse();
			long c = cursor();
			if (c != 0) SDLMouse.SDL_SetCursor(c);
		} else {
			if (dragging) endDrag(win);
			SDLMouse.SDL_SetCursor(SDLMouse.SDL_GetDefaultCursor());
			// zoomed into first person: the mouse becomes mouse-look right away, like Roblox
			if (handles() && RobloxCamera.firstPerson() && !mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive()) mc.mouseHandler.grabMouse();
		}
	}

	/** Mouse button while active; returns true when handled (vanilla skipped). */
	public static boolean button(int button, int action) {
		var mc = Minecraft.getInstance();
		long win = mc.getWindow().handle();
		boolean press = action == InputConstants.PRESS;
		var held = mc.player.getMainHandItem();
		boolean gear = !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(Rocraft.MOD_ID);
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (!active()) return gear; // first person: right click never activates a tool; other items keep vanilla use
			if (press) {
				try (var st = MemoryStack.stackPush()) {
					var x = st.mallocFloat(1);
					var y = st.mallocFloat(1);
					SDLMouse.SDL_GetMouseState(x, y);
					dragX = x.get(0);
					dragY = y.get(0);
				}
				dragging = true;
				dragged = false;
				moved = 0;
				SDLMouse.SDL_SetWindowRelativeMouseMode(win, true);
			} else if (dragging) {
				endDrag(win);
				if (!dragged && !gear) click(mc.options.keyUse); // plain right click: Minecraft use for non-Roblox items only
			}
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			if (active() && press && com.rocraft.RocraftConfig.INSTANCE.hud2018) { // clicking a backpack slot equips / unequips it
				var w = mc.getWindow();
				double k = (double) Hud2018.PX / w.getGuiScale(); // framebuffer pixels -> the HUD's Roblox pixels
				double sx = mc.mouseHandler.xpos() * w.getWidth() / w.getScreenWidth() * k, sy = mc.mouseHandler.ypos() * w.getHeight() / w.getScreenHeight() * k;
				int slot = Hud2018.slotAt(sx, sy);
				if (slot >= 0) { Hud2018.toggle(mc, slot); return true; }
				if (Hud2018.onEmotes(sx, sy)) { EmoteWheel.open(); return true; }
			}
			if (!active() && !gear) return false; // first person, non-Roblox item: vanilla attack/mine
			if (press) {
				long now = System.currentTimeMillis();
				if (held.is(Tools.LINKED_SWORD) && now - lastLeftClick < 200) click(mc.options.keyUse); // double click: lunge
				else if (gear && !held.is(Tools.LINKED_SWORD)) click(mc.options.keyUse);              // Tool.Activated
				else { KeyMapping.set(key(mc.options.keyAttack), true); KeyMapping.click(key(mc.options.keyAttack)); }
				lastLeftClick = now;
			} else KeyMapping.set(key(mc.options.keyAttack), false);
			return true;
		}
		return false;
	}

	/** Cursor movement (dx, dy since the last event) while right-dragging rotates the camera. */
	public static void move(double dx, double dy) {
		if (!dragging) return;
		moved += Math.abs(dx) + Math.abs(dy);
		if (moved > 3) dragged = true;
		double sens = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
		RobloxCamera.turn(dx * sens * 2, dy * sens * 2);
	}

	private static void endDrag(long win) {
		dragging = false;
		SDLMouse.SDL_SetWindowRelativeMouseMode(win, false);
		SDLMouse.SDL_WarpMouseInWindow(win, dragX, dragY); // Roblox puts the cursor back where the drag started
	}

	private static void click(KeyMapping k) {
		var key = key(k);
		KeyMapping.set(key, true);
		KeyMapping.click(key);
		KeyMapping.set(key, false);
	}

	private static InputConstants.Key key(KeyMapping k) { return InputConstants.getKey(k.saveString()); }

	/** SDL colour cursor from the user's Roblox ArrowFarCursor.png (hotspot = image centre, as Roblox draws it). */
	private static long cursor() {
		if (cursor != -1) return cursor;
		cursor = 0;
		var f = RobloxAssets.file("textures/ArrowFarCursor.png");
		if (f == null) return cursor;
		try {
			BufferedImage img = ImageIO.read(f.toFile());
			int w = img.getWidth(), h = img.getHeight();
			var buf = MemoryUtil.memAlloc(w * h * 4);
			for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
				int c = img.getRGB(x, y);
				buf.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
			}
			buf.flip();
			var surface = SDLSurface.SDL_CreateSurfaceFrom(w, h, SDLPixels.SDL_PIXELFORMAT_RGBA32, buf, w * 4);
			if (surface != null) {
				cursor = SDLMouse.SDL_CreateColorCursor(surface, w / 2, h / 2); // SDL copies the pixels
				SDLSurface.SDL_DestroySurface(surface);
			}
			MemoryUtil.memFree(buf);
		} catch (Exception e) {
			Rocraft.LOGGER.warn("Roblox cursor unavailable: {}", e.toString());
		}
		return cursor;
	}
}
