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
	/** Where Roblox Player/Studio keep their files on each OS; "*" matches any one folder. Must stay above CONTENT (static init order). */
	private static final String[] INSTALLS = {
		"%LOCALAPPDATA%/Roblox/Versions", "C:/Program Files (x86)/Roblox/Versions", "C:/Program Files/Roblox/Versions",
		"/Applications/Roblox.app/Contents/Resources", "/Applications/RobloxStudio.app/Contents/Resources",
		"~/Applications/Roblox.app/Contents/Resources", "~/Applications/RobloxStudio.app/Contents/Resources",
		// Linux: Sober (Player), Vinegar (Studio, Flatpak or not), and plain Wine / Grapejuice prefixes
		"~/.var/app/org.vinegarhq.Sober/data/sober", "~/.var/app/org.vinegarhq.Vinegar/data/vinegar", "~/.local/share/vinegar",
		"~/.wine/drive_c/users/*/AppData/Local/Roblox/Versions",
		"~/.local/share/grapejuice/prefixes/*/drive_c/users/*/AppData/Local/Roblox/Versions"};
	static final Path CONTENT = find();
	private static final Map<String, Optional<Identifier>> TEX = new HashMap<>();

	/** The newest Roblox content/ folder (one holding fonts/) under any known install, or null. */
	private static Path find() {
		String home = System.getProperty("user.home"), local = System.getenv("LOCALAPPDATA");
		List<Path> found = new ArrayList<>();
		for (String pattern : INSTALLS) {
			if (pattern.startsWith("%LOCALAPPDATA%") && local == null) continue;
			for (Path root : expand(pattern.replace("%LOCALAPPDATA%", String.valueOf(local)).replaceFirst("^~", home.replace('\\', '/'))))
				try (var s = Files.find(root, 4, (p, a) -> a.isDirectory() && p.getFileName().toString().equals("content") && Files.isDirectory(p.resolve("fonts")))) {
					s.forEach(found::add);
				} catch (Exception ignored) {}
		}
		Path best = found.stream().max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
		Rocraft.LOGGER.info(best == null ? "No Roblox install found; using placeholder UI" : "Roblox content: " + best);
		return best;
	}

	/** Existing folders matching a path whose "*" segments match any one folder. */
	private static List<Path> expand(String pattern) {
		List<Path> out = new ArrayList<>(List.of(Path.of(pattern.startsWith("/") ? "/" : pattern.substring(0, pattern.indexOf('/') + 1))));
		for (String seg : pattern.substring(pattern.indexOf('/') + 1).split("/")) {
			List<Path> next = new ArrayList<>();
			for (Path p : out)
				if (!seg.equals("*")) next.add(p.resolve(seg));
				else try (var s = Files.list(p)) { s.filter(Files::isDirectory).forEach(next::add); } catch (Exception ignored) {}
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
