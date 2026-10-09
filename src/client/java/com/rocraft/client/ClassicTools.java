package com.rocraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The classic StarterPack tools without a Studio export: their parameters (grip, handle, mesh scale, content refs)
 * from the mod's classic_tools.json, and the asset ids Roblox itself maps the old built-in rbxasset:// files to, so
 * every mesh, texture and sound downloads through Open Cloud like any other asset.
 */
final class ClassicTools {
	private static JsonObject data;

	private static JsonObject data() {
		if (data == null) try (var in = ClassicTools.class.getResourceAsStream("/assets/rocraft/classic_tools.json")) {
			data = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) { throw new IllegalStateException("classic_tools.json missing", e); }
		return data;
	}

	private static String norm(String ref) { return ref.toLowerCase(Locale.ROOT).replace("rbxasset://", "").replaceAll("/+", "/"); }

	/** Website asset id for a built-in rbxasset:// path (Roblox's own mapping), or -1. */
	static long assetId(String rbxasset) {
		String want = norm(rbxasset);
		for (var e : data().getAsJsonObject("rbxasset_ids").entrySet())
			if (norm(e.getKey()).equals(want)) return e.getValue().getAsLong();
		return -1;
	}

	/** The tool as a minimal .rbxmx (what the Studio export used to write), or null if unknown. */
	static byte[] rbxmx(String name) {
		var t = data().getAsJsonObject("tools").getAsJsonObject(name);
		if (t == null) return null;
		var sb = new StringBuilder("<roblox version=\"4\">");
		item(sb, t);
		return sb.append("</roblox>").toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void item(StringBuilder sb, JsonObject it) {
		sb.append("<Item class=\"").append(it.get("class").getAsString()).append("\"><Properties>");
		for (var p : it.getAsJsonObject("props").entrySet()) prop(sb, p.getKey(), p.getValue());
		sb.append("</Properties>");
		if (it.has("children")) for (var c : it.getAsJsonArray("children")) item(sb, c.getAsJsonObject());
		sb.append("</Item>");
	}

	private static void prop(StringBuilder sb, String name, JsonElement v) {
		if (v.isJsonArray() && v.getAsJsonArray().size() == 12) {
			String[] k = {"X", "Y", "Z", "R00", "R01", "R02", "R10", "R11", "R12", "R20", "R21", "R22"};
			sb.append("<CoordinateFrame name=\"").append(name).append("\">");
			for (int i = 0; i < 12; i++) sb.append('<').append(k[i]).append('>').append(v.getAsJsonArray().get(i).getAsFloat()).append("</").append(k[i]).append('>');
			sb.append("</CoordinateFrame>");
		} else if (v.isJsonArray()) {
			sb.append("<Vector3 name=\"").append(name).append("\">");
			for (int i = 0; i < 3; i++) sb.append('<').append("XYZ".charAt(i)).append('>').append(v.getAsJsonArray().get(i).getAsFloat()).append("</").append("XYZ".charAt(i)).append('>');
			sb.append("</Vector3>");
		} else {
			String s = v.getAsString().replace("&", "&amp;").replace("<", "&lt;");
			String tag = name.equals("Color3uint8") ? "Color3uint8" : name.equals("MeshType") ? "token"
				: s.startsWith("rbxasset://") || s.startsWith("http") ? "Content" : "string";
			if (tag.equals("Content")) sb.append("<Content name=\"").append(name).append("\"><url>").append(s).append("</url></Content>");
			else sb.append('<').append(tag).append(" name=\"").append(name).append("\">").append(s).append("</").append(tag).append('>');
		}
	}
}
