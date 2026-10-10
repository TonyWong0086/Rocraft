package com.rocraft.client;

import com.rocraft.Rocraft;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Private resource pack in this profile's resourcepacks/, filled from Roblox at runtime: gear icons and sounds
 * (Open Cloud), oof (local Roblox install). Never shipped in the jar; delete the folder to re-download.
 */
final class RobloxPack {
	static final String NAME = "Rocraft Roblox Assets", ID = "file/" + NAME;
	static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(NAME);
	static final Path ASSETS = DIR.resolve("assets/rocraft");
	static final Map<String, String> GEAR = com.rocraft.tools.Tools.GEAR;
	/** Sounds a tool script creates in code rather than as Sound objects (TeddyScript s1..s5). */
	static final Map<String, long[]> SCRIPT_SOUNDS = Map.of("teddy", new long[]{12844799, 12844794, 12803520, 12803507, 12803498});
	/** Classic rbxasset:// sounds the old tools use, from Roblox's library copies (collide.wav, clickfast.wav, ...). */
	static final Map<String, Long> CLASSIC_SOUNDS = Map.ofEntries(Map.entry("bomb/tick", 12221976L), Map.entry("bomb/explode", 12222084L),
		Map.entry("rocket_launcher/swoosh", 12222095L), Map.entry("rocket_launcher/boom", 12221984L), Map.entry("superball/boing", 12222124L),
		Map.entry("slingshot/sling", 12222103L), Map.entry("trowel/build", 12221944L), Map.entry("paintball_gun/fire", 11900833L),
		// the classic Linked Sword's swordslash.wav / swordlunge.wav / unsheath.wav
		Map.entry("linked_sword/classic_slash", 12222216L), Map.entry("linked_sword/classic_lunge", 12222208L), Map.entry("linked_sword/classic_unsheath", 12222225L),
		// the Rolling Hoverboard's board (SkateboardModule "Segway"): BoardOllie, BoardDrop, BoardStop
		Map.entry("hoverboard/ollie", 22921446L), Map.entry("hoverboard/land", 22920550L), Map.entry("hoverboard/stop", 22920633L));

	/** Blocking; only fetches what's missing. ponytail: runs before the title screen; async it if first launch gets slow. */
	static void build() {
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve("pack.mcmeta"),
				"{\"pack\":{\"description\":\"Roblox assets for Rocraft (local, private)\",\"min_format\":88,\"max_format\":88}}");
			copyLocal("sounds/oof.ogg", ASSETS.resolve("sounds/oof.ogg"));
			RbxParticle.writeTextures(ASSETS);
			for (var e : GEAR.entrySet()) gear(e.getKey(), e.getValue());
			for (String user : com.rocraft.tools.Robloxian.USERS) { // spawn eggs: the user's avatar headshot
				String id = user.toLowerCase(java.util.Locale.ROOT) + "_spawn_egg";
				Path icon = ASSETS.resolve("textures/item/" + id + ".png");
				Long outfit = RobloxProfile.OUTFITS.get(user); // drawn in a saved outfit: its thumbnail, redone when the outfit changes
				boolean look = RobloxProfile.LOOKS.containsKey(user); // a fixed look: drawn ourselves, Roblox has no picture of it
				Path mark = ASSETS.resolve("textures/item/" + id + (outfit != null ? ".outfit" + outfit : look ? ".look" + RobloxProfile.LOOKS.get(user).hashCode() : ".headshot"));
				try {
					if (!Files.exists(icon) || (outfit != null || look) && !Files.exists(mark)) {
						write(icon, square(outfit != null ? outfitThumbnail(outfit) : look ? drawn(user) : headshot(user), 128));
						Files.writeString(mark, "");
					}
				}
				catch (Exception ex) { Rocraft.LOGGER.warn("{} spawn egg icon skipped: {}", user, ex.toString()); }
			}
			for (var e : com.rocraft.tools.Tools.HATS.entrySet()) { // hat icons: the catalog thumbnail, as the 2018 inventory showed them
				Path icon = ASSETS.resolve("textures/item/" + e.getKey() + ".png");
				try { if (!Files.exists(icon)) write(icon, square(thumbnail(e.getValue()), 128)); }
				catch (Exception ex) { Rocraft.LOGGER.warn("{} icon skipped: {}", e.getKey(), ex.toString()); }
			}
			if (RobloxApi.hasKey()) for (var e : CLASSIC_SOUNDS.entrySet()) {
				String[] p = e.getKey().split("/");
				try { sound(p[0], p[1], e.getValue()); } catch (Exception ex) { Rocraft.LOGGER.warn("classic sound {} skipped: {}", e.getKey(), ex.toString()); }
			}
		} catch (Exception ex) {
			Rocraft.LOGGER.warn("Roblox asset pack incomplete: {}", ex.toString());
		}
	}

	/** Tool icon (TextureId, what the 2018 backpack shows) + its sounds, read from the tool's own model. */
	static void gear(String name, String source) {
		try {
			Path icon = ASSETS.resolve("textures/item/" + name + ".png"), mark = ASSETS.resolve("textures/item/" + name + ".from_tool2");
			if (source.startsWith("asset:") && !RobloxApi.hasKey()) { // public thumbnail until a key is set
				if (!Files.exists(icon)) write(icon, square(thumbnail(Long.parseLong(source.substring(6))), 128));
				return;
			}
			var model = Rig.model(name, source);
			if (!Files.exists(mark)) {
				String tex = model.first("Tool").str("TextureId");
				BufferedImage img = tex != null && !tex.isBlank() ? ImageIO.read(new ByteArrayInputStream(Rig.content(tex)))
					: thumbnail(Long.parseLong(source.substring(6)));
				write(icon, square(img, 128));
				Files.writeString(mark, String.valueOf(tex));
			}
			for (var s : model.all) // every Sound with a web asset, saved as <gear>/<name>.ogg
				if (s.className.equals("Sound") && s.assetId("SoundId") > 0) sound(name, s.name().toLowerCase(java.util.Locale.ROOT), s.assetId("SoundId"));
			long[] extra = SCRIPT_SOUNDS.get(name);
			if (extra != null) for (int i = 0; i < extra.length; i++) sound(name, "say" + (i + 1), extra[i]);
		} catch (Exception e) {
			Rocraft.LOGGER.warn("{} assets incomplete: {}", name, e.toString());
		}
	}

	static void sound(String gear, String file, long id) throws Exception {
		Path out = ASSETS.resolve("sounds/" + gear + "/" + file + ".ogg");
		if (Files.exists(out)) return;
		byte[] b = RobloxApi.asset(id);
		if (b.length < 4 || b[0] != 'O' || b[1] != 'g') { Rocraft.LOGGER.info("{} sound {} isn't OGG, skipped", gear, file); return; }
		Files.createDirectories(out.getParent());
		Files.write(out, b);
	}

	static void copyLocal(String rel, Path out) throws Exception {
		Path src = RobloxAssets.file(rel);
		if (src == null || Files.exists(out)) return;
		Files.createDirectories(out.getParent());
		Files.copy(src, out);
	}

	/** Turns the pack on once (first title screen); reloads if new files arrived. */
	static void enable(Minecraft mc) {
		var repo = mc.getResourcePackRepository();
		repo.reload();
		if (repo.getSelectedIds().contains(ID) || !repo.isAvailable(ID)) return;
		repo.addPack(ID);
		mc.options.updateResourcePacks(repo);
		mc.reloadResourcePacks();
	}

	/** A user's official 150x150 avatar headshot (public endpoints, no key). */
	static BufferedImage headshot(String username) throws Exception {
		var body = "{\"usernames\":[\"" + username + "\"],\"excludeBannedUsers\":false}";
		long id = RobloxProfile.send(HttpRequest.newBuilder(URI.create("https://users.roblox.com/v1/usernames/users"))
			.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).timeout(Duration.ofSeconds(10)).build())
			.getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsLong();
		var json = RobloxProfile.send(HttpRequest.newBuilder(URI.create(
			"https://thumbnails.roblox.com/v1/users/avatar-headshot?userIds=" + id + "&size=150x150&format=Png&isCircular=false"))
			.timeout(Duration.ofSeconds(10)).build());
		var t = json.getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject();
		if (!"Completed".equals(t.get("state").getAsString())) return drawn(username); // banned accounts' thumbnails are blocked
		byte[] png = RobloxApi.HTTP.send(HttpRequest.newBuilder(URI.create(t.get("imageUrl").getAsString())).timeout(Duration.ofSeconds(10)).build(),
			HttpResponse.BodyHandlers.ofByteArray()).body();
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	/** The user's avatar as Rocraft draws it, full body, centred in a square. */
	static BufferedImage drawn(String username) {
		var full = AvatarFrame.render(RobloxProfile.load(username, com.rocraft.RocraftConfig.INSTANCE.apiKey));
		var sq = new BufferedImage(full.getHeight(), full.getHeight(), BufferedImage.TYPE_INT_ARGB);
		var g = sq.createGraphics();
		g.drawImage(full, (sq.getWidth() - full.getWidth()) / 2, 0, null);
		g.dispose();
		return sq;
	}

	/** Official 150x150 full-body thumbnail of a saved outfit (public endpoint, no key). */
	static BufferedImage outfitThumbnail(long outfitId) throws Exception {
		com.google.gson.JsonObject t = null;
		for (int tries = 0; tries < 5; tries++) { // an outfit nobody asked for lately is rendered on demand: "Pending" with no image yet
			t = RobloxProfile.send(HttpRequest.newBuilder(URI.create(
				"https://thumbnails.roblox.com/v1/users/outfits?userOutfitIds=" + outfitId + "&size=150x150&format=Png"))
				.timeout(Duration.ofSeconds(10)).build()).getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject();
			if (t.has("imageUrl") && !t.get("imageUrl").isJsonNull() && !t.get("imageUrl").getAsString().isBlank()) break;
			Thread.sleep(2000);
		}
		byte[] png = RobloxApi.HTTP.send(HttpRequest.newBuilder(URI.create(t.get("imageUrl").getAsString())).timeout(Duration.ofSeconds(10)).build(),
			HttpResponse.BodyHandlers.ofByteArray()).body();
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	/** Official 150x150 asset thumbnail (public endpoint, no key). */
	static BufferedImage thumbnail(long assetId) throws Exception {
		var json = RobloxProfile.send(HttpRequest.newBuilder(URI.create(
			"https://thumbnails.roblox.com/v1/assets?assetIds=" + assetId + "&size=150x150&format=Png&isCircular=false"))
			.timeout(Duration.ofSeconds(10)).build());
		String url = json.getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject().get("imageUrl").getAsString();
		byte[] png = RobloxApi.HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
			HttpResponse.BodyHandlers.ofByteArray()).body();
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	static void write(Path out, BufferedImage img) throws Exception {
		Files.createDirectories(out.getParent());
		ImageIO.write(img, "png", out.toFile());
	}

	/** Power-of-two square so the item atlas can mipmap it. */
	static BufferedImage square(BufferedImage src, int n) {
		var dst = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		var g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(src, 0, 0, n, n, null);
		g.dispose();
		// item icons are alpha-tested: faint antialiased edges would turn into solid white specks, so cut them cleanly
		for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
			int c = dst.getRGB(x, y);
			dst.setRGB(x, y, (c >>> 24) < 128 ? 0 : c | 0xFF000000);
		}
		return dst;
	}
}
