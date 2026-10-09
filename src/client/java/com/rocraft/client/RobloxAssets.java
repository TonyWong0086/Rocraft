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
	static final Path CONTENT = find();
	private static final Map<String, Optional<Identifier>> TEX = new HashMap<>();

	private static Path find() {
		try (var s = Files.list(Path.of(System.getenv("LOCALAPPDATA"), "Roblox", "Versions"))) {
			return s.map(p -> p.resolve("content")).filter(Files::isDirectory)
				.max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
		} catch (Exception e) {
			Rocraft.LOGGER.info("No Roblox install found; using placeholder UI");
			return null;
		}
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
