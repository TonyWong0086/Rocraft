package com.rocraft.client;

import com.google.gson.JsonParser;
import com.rocraft.RocraftConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Roblox asset files through Open Cloud's Asset Delivery API (key scope legacy-assets:manage).
 * Raw files are cached in config/rocraft/cache so each asset downloads once. Callers stay off the render thread.
 */
final class RobloxApi {
	static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	static Path cache() { // -Drocraft.cache=... lets offline checks run without the game
		String o = System.getProperty("rocraft.cache");
		return o != null ? Path.of(o) : FabricLoader.getInstance().getConfigDir().resolve("rocraft/cache");
	}
	static final Pattern ASSET_ID = Pattern.compile("(?:[?&]id=|rbxassetid://)(\\d+)");

	static boolean hasKey() { return !RocraftConfig.INSTANCE.apiKey.isBlank(); }

	/** Raw asset bytes (PNG, OGG, mesh, rbxmx XML, ...). */
	static byte[] asset(long id) throws Exception {
		Path f = cache().resolve(Long.toString(id));
		if (Files.exists(f)) return Files.readAllBytes(f);
		if (!hasKey()) throw new IllegalStateException("no Open Cloud key");
		var loc = HTTP.send(HttpRequest.newBuilder(URI.create("https://apis.roblox.com/asset-delivery-api/v1/assetId/" + id))
			.header("x-api-key", RocraftConfig.INSTANCE.apiKey.trim()).timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
		if (loc.statusCode() != 200) throw new RuntimeException("asset " + id + ": HTTP " + loc.statusCode());
		String url = JsonParser.parseString(loc.body()).getAsJsonObject().get("location").getAsString();
		var res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofByteArray());
		byte[] b = res.body();
		if (res.headers().firstValue("Content-Encoding").orElse("").contains("gzip") || (b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B))
			try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(b))) { b = in.readAllBytes(); }
		Files.createDirectories(f.getParent());
		Files.write(f, b);
		return b;
	}

	/** First asset id inside an rbxmx property, e.g. prop("ShirtTemplate") or a Sound named "Slash". */
	static long idAfter(String xml, String marker) {
		int i = xml.indexOf(marker);
		if (i < 0) return -1;
		var m = ASSET_ID.matcher(xml);
		return m.find(i) ? Long.parseLong(m.group(1)) : -1;
	}
}
