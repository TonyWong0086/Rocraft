package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Roblox's emote wheel (EmotesMenu, Large textures from the install): eight slots around a dark circle, slot 1 at the
 * top going clockwise, each with its number and the emote's catalog thumbnail; the slot under the mouse lights up
 * (SelectedGradient + SelectedLine) and its name shows in the middle ("Select an Emote" otherwise). Click a slot or
 * press 1-8 to play it; "." or Esc closes. The emotes are the ones equipped on the account; without any, the R15
 * Animate emotes. Emotes need an R15 avatar, as on Roblox.
 */
public final class EmoteWheel extends Screen {
	static final String UI = "textures/ui/Emotes/";
	/** One wheel slot: what it shows and what it plays (emote asset id, or a plain animation id when asset = 0). */
	record Slot(String name, long asset, long anim) {}

	private final Slot[] slots = new Slot[8];
	private int selected = -1;
	private int mx, my;

	EmoteWheel() {
		super(Component.literal("Emotes"));
		var p = RocraftClient.profile;
		if (!p.emotes.isEmpty()) for (var e : p.emotes) { if (e.slot() >= 1 && e.slot() <= 8) slots[e.slot() - 1] = new Slot(e.name(), e.assetId(), 0); }
		else { // nothing equipped: Animate's own emotes
			String[] names = {"wave", "point", "dance", "dance2", "dance3", "laugh", "cheer"};
			long[] ids = {507770239L, 507770453L, 507771019L, 507776043L, 507777268L, 507770818L, 507770677L};
			for (int i = 0; i < names.length; i++) slots[i] = new Slot(names[i], 0, ids[i]);
		}
		thumbnails(java.util.Arrays.stream(slots).filter(s -> s != null && s.asset() != 0).map(Slot::asset).toList());
	}

	/** Opens the wheel (topbar button, "." key). */
	static void open() {
		var mc = Minecraft.getInstance();
		if (mc.player != null && mc.gui.screen() == null) mc.setScreenAndShow(new EmoteWheel());
	}

	@Override public boolean isPauseScreen() { return false; }

	private int s() { return minecraft.getWindow().getGuiScale(); }

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
		int s = s(), W = width * s, H = height * s;
		mx = mouseX * s;
		my = mouseY * s;
		var m = g.pose();
		m.pushMatrix();
		m.scale(1f / s, 1f / s); // screen pixels
		int D = H >= 900 ? 400 : 300, cx = W / 2, cy = H / 2;
		float k = D / 400f; // Large textures are laid out for a 400 px wheel
		boolean r15 = RocraftClient.profile.r15 != null;
		double dx = mx - cx, dy = my - cy;
		selected = !r15 || Math.hypot(dx, dy) < 20 * k || Math.hypot(dx, dy) > D / 2f + 40 * k ? -1
			: Math.floorMod((int) Math.round((Math.toDegrees(Math.atan2(dy, dx)) + 90) / 45), 8);
		if (selected >= 0 && slots[selected] == null) selected = -1;

		tex(g, "Large/CircleBackground.png", cx - D / 2, cy - D / 2, D, D);
		if (selected >= 0) rotated(g, cx, cy, selected, () -> tex(g, "Large/SelectedGradient.png", 0, Math.round(-70.5f * k), Math.round(183 * k), Math.round(141 * k)));
		tex(g, "Large/SegmentedCircle.png", cx - D / 2, cy - D / 2, D, D);
		if (selected >= 0) rotated(g, cx, cy, selected, () -> tex(g, "Large/SelectedLine.png", Math.round(86 * k), Math.round(-33.5f * k), Math.round(11 * k), Math.round(67 * k)));

		int thumb = Math.round(76 * k), px = Math.round(15 * k);
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(-90 + 45 * i);
			int tx = cx + (int) Math.round(Math.cos(a) * 148 * k), ty = cy + (int) Math.round(Math.sin(a) * 148 * k);
			int nx = cx + (int) Math.round(Math.cos(a) * 108 * k), ny = cy + (int) Math.round(Math.sin(a) * 108 * k);
			int col = i == selected ? 0xFFFFFFFF : 0xFFB4B4B4;
			String n = String.valueOf(i + 1);
			RbxFont.draw(g, n, nx - RbxFont.width(n, px, true) / 2, ny - px / 2 - 2, px, true, col);
			var sl = slots[i];
			if (sl == null) continue;
			Identifier t = sl.asset() != 0 ? THUMBS.get(sl.asset()) : null;
			if (t != null) g.blit(RenderPipelines.GUI_TEXTURED, t, tx - thumb / 2, ty - thumb / 2, 0, 0, thumb, thumb, thumb, thumb);
			else { // no thumbnail (Animate's emotes, or still loading): the name
				int w = RbxFont.width(sl.name(), px, false);
				RbxFont.draw(g, sl.name(), tx - w / 2, ty - px / 2 - 2, px, false, col);
			}
		}

		// middle: the selected emote's name, "Select an Emote", or why there are no emotes
		int inner = Math.round(80 * k), tpx = Math.round(20 * k);
		if (!r15) {
			int ic = Math.round(52 * k);
			tex(g, "ErrorIcon.png", cx - ic / 2, cy - ic - 4, ic, ic);
			center(g, "Emotes need an R15 avatar", cx, cy + 6, 2 * inner, Math.round(15 * k));
		} else center(g, selected >= 0 ? slots[selected].name() : "Select an Emote", cx, cy - tpx / 2, 2 * inner, tpx);
		m.popMatrix();
	}

	/** Draws with (0, 0) at the wheel centre and +x pointing at the given slot. */
	private static void rotated(GuiGraphicsExtractor g, int cx, int cy, int slot, Runnable draw) {
		var m = g.pose();
		m.pushMatrix();
		m.translate(cx, cy);
		m.rotate((float) Math.toRadians(-90 + 45 * slot));
		draw.run();
		m.popMatrix();
	}

	/** White text centred on x, wrapped to w (two lines at most, like the wheel's label). */
	private static void center(GuiGraphicsExtractor g, String text, int x, int y, int w, int px) {
		String line = "", second = "";
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (second.isEmpty() && (line.isEmpty() || RbxFont.width(next, px, true) <= w)) line = next;
			else second = second.isEmpty() ? word : second + " " + word;
		}
		if (!second.isEmpty()) y -= (px + 4) / 2;
		RbxFont.draw(g, line, x - RbxFont.width(line, px, true) / 2, y, px, true, 0xFFFFFFFF);
		if (!second.isEmpty()) RbxFont.draw(g, second, x - RbxFont.width(second, px, true) / 2, y + px + 4, px, true, 0xFFFFFFFF);
	}

	private static void tex(GuiGraphicsExtractor g, String rel, int x, int y, int w, int h) {
		Identifier t = RobloxAssets.tex(UI + rel);
		int[] sz = RobloxAssets.size(UI + rel);
		if (t != null) g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0, 0, w, h, sz[0], sz[1], sz[0], sz[1]);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (selected >= 0) play(selected);
		onClose(); // a click anywhere else closes it
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
			if (slots[key - GLFW.GLFW_KEY_1] != null) play(key - GLFW.GLFW_KEY_1);
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_PERIOD) { onClose(); return true; }
		return super.keyPressed(e);
	}

	private void play(int i) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || RocraftClient.profile.r15 == null) return;
		var sl = slots[i];
		var animator = Animator.of(mc.player.getId());
		if (sl.anim() != 0) { animator.emote(sl.anim()); return; }
		Long known = ANIM_OF.get(sl.asset());
		if (known != null) { animator.emote(known); return; }
		Thread.startVirtualThread(() -> { // an emote asset is a model holding the Animation that points at the animation
			try {
				var anim = com.rocraft.rbx.RbxModel.read(RobloxApi.asset(sl.asset())).first("Animation");
				long id = anim == null ? -1 : anim.assetId("AnimationId");
				if (id <= 0) return;
				ANIM_OF.put(sl.asset(), id);
				mc.execute(() -> animator.emote(id));
			} catch (Exception ex) { Rocraft.LOGGER.warn("emote {} unavailable: {}", sl.asset(), ex.toString()); }
		});
	}

	private static final Map<Long, Long> ANIM_OF = new ConcurrentHashMap<>();
	private static final Map<Long, Identifier> THUMBS = new ConcurrentHashMap<>();
	private static final java.util.Set<Long> THUMB_LOADING = ConcurrentHashMap.newKeySet();

	/** The emotes' catalog thumbnails, in one request (public endpoint; one call each gets rate limited), uploaded as they arrive. */
	private static void thumbnails(java.util.List<Long> assets) {
		var want = assets.stream().filter(THUMB_LOADING::add).toList();
		if (want.isEmpty()) return;
		Thread.startVirtualThread(() -> {
			try {
				var ids = String.join(",", want.stream().map(String::valueOf).toList());
				var data = RobloxProfile.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
					"https://thumbnails.roblox.com/v1/assets?assetIds=" + ids + "&size=150x150&format=Png&isCircular=false")).build())
					.getAsJsonObject().getAsJsonArray("data");
				for (var e : data) {
					var o = e.getAsJsonObject();
					long asset = o.get("targetId").getAsLong();
					if (!o.has("imageUrl") || o.get("imageUrl").isJsonNull()) { THUMB_LOADING.remove(asset); continue; } // still rendering: next time
					byte[] png = RobloxApi.HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(o.get("imageUrl").getAsString())).build(),
						java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();
					BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
					Minecraft.getInstance().execute(() -> {
						var ni = new NativeImage(img.getWidth(), img.getHeight(), true);
						for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
						Identifier id = Rocraft.id("emote/" + asset);
						Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft emote", ni));
						THUMBS.put(asset, id);
					});
				}
			} catch (Exception ex) {
				want.forEach(THUMB_LOADING::remove); // try again next time the wheel opens
				Rocraft.LOGGER.warn("emote thumbnails unavailable: {}", ex.toString());
			}
		});
	}
}
