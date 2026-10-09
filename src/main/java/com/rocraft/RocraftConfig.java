package com.rocraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/rocraft.json. The API key stays on this PC and is never logged. */
public final class RocraftConfig {
	public String username = "", apiKey = "";
	public boolean robloxMovement = true, fallDamage = false, hunger = false; // gameplay
	public boolean hud2018 = true, robloxFont = true, robloxCamera = true;     // graphics

	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rocraft.json");
	public static final boolean FIRST_RUN = !Files.exists(FILE);
	public static RocraftConfig INSTANCE = load();

	static RocraftConfig load() {
		try { if (!FIRST_RUN) { var c = GSON.fromJson(Files.readString(FILE), RocraftConfig.class); if (c != null) return c; } }
		catch (Exception e) { Rocraft.LOGGER.warn("bad rocraft.json, using defaults: {}", e.toString()); }
		return new RocraftConfig();
	}

	public void save() {
		try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception e) { Rocraft.LOGGER.warn("config save failed: {}", e.toString()); }
	}
}
