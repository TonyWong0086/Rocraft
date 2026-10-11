package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Reads UI textures and fonts from the user's own Roblox install at runtime. Nothing is copied into the mod. */
final class RobloxAssets {
	/** Where Roblox Player/Studio keep their files on each OS; "*" in a folder name matches anything (case-insensitive). Must stay above CONTENT (static init order). */
	private static final String[] INSTALLS = {
		"%LOCALAPPDATA%/Roblox/Versions", "C:/Program Files (x86)/Roblox/Versions", "C:/Program Files/Roblox/Versions",
		// macOS: Roblox.app, "Roblox Studio.app", RobloxStudio.app, ... in either Applications folder
		"/Applications/*roblox*.app/Contents/Resources", "~/Applications/*roblox*.app/Contents/Resources",
		// Linux: Sober (Player), Vinegar (Studio, Flatpak or not), and plain Wine / Grapejuice prefixes
		"~/.var/app/org.vinegarhq.Sober/data/sober", "~/.var/app/org.vinegarhq.Vinegar/data/vinegar", "~/.local/share/vinegar",
		"~/.wine/drive_c/users/*/AppData/Local/Roblox/Versions",
		"~/.local/share/grapejuice/prefixes/*/drive_c/users/*/AppData/Local/Roblox/Versions"};
	static final Path CONTENT = find();
	private static final Map<String, Optional<Identifier>> TEX = new HashMap<>();

	/** The newest Roblox content/ folder (one holding textures/) under robloxPath from the config, else under any known install; or null. */
	private static Path find() {
		String home = System.getProperty("user.home").replace('\\', '/'), local = System.getenv("LOCALAPPDATA");
		String manual = com.rocraft.RocraftConfig.INSTANCE.robloxPath.trim().replace('\\', '/').replaceFirst("^~", home);
		List<Path> found = new ArrayList<>();
		if (!manual.isEmpty()) contentUnder(Path.of(manual), found);
		if (found.isEmpty()) {
			if (!manual.isEmpty()) Rocraft.LOGGER.warn("robloxPath {} has no Roblox content folder; searching the usual places", manual);
			for (String pattern : INSTALLS) {
				if (pattern.startsWith("%LOCALAPPDATA%") && local == null) continue;
				for (Path root : expand(pattern.replace("%LOCALAPPDATA%", String.valueOf(local)).replaceFirst("^~", home))) contentUnder(root, found);
			}
		}
		Path best = found.stream().max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
		if (best == null) Rocraft.LOGGER.warn("No Roblox install found; using placeholder UI. Set robloxPath in config/rocraft.json to your Roblox install.");
		else Rocraft.LOGGER.info("Roblox content: {}", best);
		return best;
	}

	private static void contentUnder(Path root, List<Path> found) {
		try (var s = Files.find(root, 4, (p, a) -> a.isDirectory() && p.getFileName() != null
				&& p.getFileName().toString().equalsIgnoreCase("content") && Files.isDirectory(p.resolve("textures")))) {
			s.forEach(found::add);
		} catch (Exception ignored) {}
	}

	/** Existing folders matching a path whose "*" segments match any one folder. */
	private static List<Path> expand(String pattern) {
		List<Path> out = new ArrayList<>(List.of(Path.of(pattern.startsWith("/") ? "/" : pattern.substring(0, pattern.indexOf('/') + 1))));
		for (String seg : pattern.substring(pattern.indexOf('/') + 1).split("/")) {
			List<Path> next = new ArrayList<>();
			for (Path p : out)
				if (!seg.contains("*")) next.add(p.resolve(seg));
				else try (var s = Files.list(p)) {
					var name = java.util.regex.Pattern.compile("(?i)" + java.util.Arrays.stream(seg.split("\\*", -1))
						.map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining(".*")));
					s.filter(c -> Files.isDirectory(c) && name.matcher(c.getFileName().toString()).matches()).forEach(next::add);
				} catch (Exception ignored) {}
			out = next;
		}
		out.removeIf(p -> !Files.isDirectory(p));
		return out;
	}

	/** Classic files exported from the user's Roblox Studio (tools/export_classic_tools.luau). */
	static Path legacyDir() { // -Drocraft.legacy=... for offline checks
		String o = System.getProperty("rocraft.legacy");
		return o != null ? Path.of(o) : net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("rocraft/legacy");
	}

	/** rbxasset://fonts/x.mesh -> legacy/fonts/x.mesh (case-insensitive on Windows), or null. */
	static Path legacy(String uri) {
		String rel = uri.replaceFirst("^rbxasset://", "").replace('\\', '/').replaceAll("/+", "/");
		Path p = legacyDir().resolve(rel);
		return Files.exists(p) ? p : null;
	}

	static Path file(String rel) {
		Path p = CONTENT == null ? null : CONTENT.resolve(rel);
		return p != null && Files.exists(p) ? p : null;
	}

	private static final Map<String, int[]> SIZE = new HashMap<>();

	/** Pixel size of a texture under content/ ({1,1} if missing). */
	static int[] size(String rel) {
		return SIZE.computeIfAbsent(rel, r -> {
			try { var img = javax.imageio.ImageIO.read(file(r).toFile()); return new int[]{img.getWidth(), img.getHeight()}; }
			catch (Exception e) { return new int[]{1, 1}; }
		});
	}

	/** Texture under content/, or null if missing. Render thread only. */
	static Identifier tex(String rel) {
		return TEX.computeIfAbsent(rel, r -> {
			Path p = file(r);
			if (p == null) return Optional.empty();
			try (var in = Files.newInputStream(p)) {
				Identifier id = Rocraft.id("rbx/" + r.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "rocraft " + r, NativeImage.read(in)));
				return Optional.of(id);
			} catch (Exception e) {
				Rocraft.LOGGER.warn("Couldn't load Roblox texture {}: {}", r, e.toString());
				return Optional.empty();
			}
		}).orElse(null);
	}
}
