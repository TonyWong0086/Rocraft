package com.rocraft.client;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * Who the player is in Rocraft. No username -> Guest, dressed like Roblox's 2017 Guests: the look of the DefaultGuest
 * account Roblox copied onto them (ROBLOX 'R' Baseball Cap, ROBLOX Jacket, Black Jeans, classic head and face).
 * Public endpoints give id + body colors; with an Open Cloud key the worn Shirt/Pants templates are downloaded too.
 */
public final class RobloxProfile {
	// head, torso, leftArm, rightArm, leftLeg, rightLeg as 0xRRGGBB: DefaultGuest's body colours
	public static final int[] GUEST = {0xF2F3F3, 0x635F62, 0xF2F3F3, 0xF2F3F3, 0x6E99CA, 0x6E99CA};
	static final String GUEST_ACCOUNT = "DefaultGuest";

	public String name = "Guest 1337";
	public boolean guest = true;
	public long userId;
	public int[] colors = GUEST.clone();
	public BufferedImage shirt, pants; // 585x559 classic clothing templates, null if none
	public final java.util.List<Rig.Piece> accessories = new java.util.ArrayList<>(); // hats, hair, back, ... as Roblox meshes
	/** R6 body package meshes (CharacterMesh) per part, Rig order; null = Roblox's default block limb. */
	public final MeshDraw[] bodyParts = new MeshDraw[6];
	public MeshDraw head;        // DynamicHead mesh + face texture (Roblox shows it on R6 too), null = classic head
	public BufferedImage face;    // classic Face decal, null = default smile

	/** Users drawn in one of their saved outfits instead of what they wear now: username -> outfit id. */
	public static final java.util.Map<String, Long> OUTFITS = java.util.Map.of("Shedletsky", 34915L); // Classic Telamon
	/**
	 * Users drawn in a fixed look that isn't a saved outfit: the same JSON the avatar endpoint returns (asset ids and
	 * types, body colours), taken from the avatar model in Studio. The assets themselves still load from Roblox.
	 */
	public static final java.util.Map<String, String> LOOKS = java.util.Map.of("DenisDaily", """
		{"assets": [
		  {"id": 15938951781, "assetType": {"name": "DynamicHead"}},
		  {"id": 86500008, "assetType": {"name": "Torso"}}, {"id": 86500054, "assetType": {"name": "LeftArm"}},
		  {"id": 86500036, "assetType": {"name": "RightArm"}}, {"id": 86500064, "assetType": {"name": "LeftLeg"}},
		  {"id": 86500078, "assetType": {"name": "RightLeg"}},
		  {"id": 376548738, "assetType": {"name": "HairAccessory"}}, {"id": 121389389, "assetType": {"name": "ShoulderAccessory"}},
		  {"id": 441731130, "assetType": {"name": "Shirt"}}, {"id": 398633812, "assetType": {"name": "Pants"}}],
		 "bodyColor3s": {"headColor3": "EAB892", "torsoColor3": "EAB892", "leftArmColor3": "EAB892", "rightArmColor3": "EAB892",
		   "leftLegColor3": "EAB892", "rightLegColor3": "EAB892"}}"""); // Silly Fun head, Man package, Brown Charmer Hair, Business Cat

	static final HttpClient HTTP = RobloxApi.HTTP;
	static final Gson GSON = new Gson();

	/** username blank => Guest. Blocking; call off the render thread. */
	public static RobloxProfile load(String username, String apiKey) {
		RobloxProfile p = new RobloxProfile();
		boolean guest = username == null || username.isBlank();
		if (guest) username = GUEST_ACCOUNT;
		try {
			JsonObject body = new JsonObject();
			body.add("usernames", GSON.toJsonTree(new String[]{username}));
			body.addProperty("excludeBannedUsers", false); // banned accounts (e.g. PGHLego1945) still have their avatar
			JsonArray d = send(HttpRequest.newBuilder(URI.create("https://users.roblox.com/v1/usernames/users"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build())
				.getAsJsonObject().getAsJsonArray("data");
			if (d.isEmpty()) return p;
			p.userId = d.get(0).getAsJsonObject().get("id").getAsLong();
			if (!guest) {
				p.name = d.get(0).getAsJsonObject().get("name").getAsString();
				p.guest = false;
			}
			Long outfit = guest ? null : OUTFITS.get(username); // an outfit's details have the same assets / bodyColor3s shape
			String look = guest ? null : LOOKS.get(username);
			JsonObject a = look != null ? com.google.gson.JsonParser.parseString(look).getAsJsonObject() : send(HttpRequest.newBuilder(URI.create(outfit != null ? "https://avatar.roblox.com/v3/outfits/" + outfit + "/details"
				: "https://avatar.roblox.com/v2/avatar/users/" + p.userId + "/avatar")).build()).getAsJsonObject();
			JsonObject c = a.getAsJsonObject("bodyColor3s");
			String[] keys = {"headColor3", "torsoColor3", "leftArmColor3", "rightArmColor3", "leftLegColor3", "rightLegColor3"};
			for (int i = 0; i < 6; i++) if (c != null && c.has(keys[i])) p.colors[i] = Integer.parseInt(c.get(keys[i]).getAsString().replace("#", ""), 16);
			if (RobloxApi.hasKey()) for (var e : a.getAsJsonArray("assets")) {
				var o = e.getAsJsonObject();
				String type = o.getAsJsonObject("assetType").get("name").getAsString();
				if (type.equals("Torso") || type.endsWith("Arm") || type.endsWith("Leg")) try { characterMesh(p, o.get("id").getAsLong()); }
					catch (Exception ex) { com.rocraft.Rocraft.LOGGER.warn("body part {} skipped: {}", o.get("id"), ex.toString()); }
				if (type.equals("Shirt")) p.shirt = template(o.get("id").getAsLong(), "ShirtTemplate");
				if (type.equals("Pants")) p.pants = template(o.get("id").getAsLong(), "PantsTemplate");
				if (type.equals("DynamicHead") && !guest) try { p.head = dynamicHead(o.get("id").getAsLong(), p.colors[0]); }
					catch (Exception ex) { com.rocraft.Rocraft.LOGGER.warn("dynamic head skipped: {}", ex.toString()); }
				if (type.equals("Face")) try { p.face = faceDecal(o.get("id").getAsLong()); }
					catch (Exception ex) { com.rocraft.Rocraft.LOGGER.warn("face skipped: {}", ex.toString()); }
				if (type.equals("Hat") || type.endsWith("Accessory")) try {
					var piece = Rig.accessory(o.get("id").getAsLong());
					if (piece != null) p.accessories.add(piece);
				} catch (Exception ex) { com.rocraft.Rocraft.LOGGER.warn("accessory {} skipped: {}", o.get("id"), ex.toString()); }
			}
		} catch (Exception e) {
			com.rocraft.Rocraft.LOGGER.warn("Roblox avatar partly loaded ({}); using what we have for {}", e.toString(), p.name);
		}
		return p;
	}

	/**
	 * A body part asset (Man, Woman, Robloxian 2.0 ...): what Roblox puts on an R6 character is the CharacterMesh in
	 * its R6 folder. BodyPart: 1 Torso, 2 LeftArm, 3 RightArm, 4 LeftLeg, 5 RightLeg. Clothing is laid on it like on the
	 * default limbs. ponytail: BaseTextureId / OverlayTextureId (package skins) not drawn yet.
	 */
	static void characterMesh(RobloxProfile p, long assetId) throws Exception {
		int[] toRig = {-1, Rig.TORSO, Rig.LEFT_ARM, Rig.RIGHT_ARM, 5, 4};
		for (var cm : com.rocraft.rbx.RbxModel.read(RobloxApi.asset(assetId)).all) {
			if (!cm.className.equals("CharacterMesh")) continue;
			Object bp = cm.props.get("BodyPart");
			int part = bp instanceof Integer i ? i : bp instanceof String s ? Integer.parseInt(s.trim()) : -1;
			long mesh = cm.assetId("MeshId");
			if (part < 1 || part > 5 || mesh <= 0) continue;
			int i = toRig[part];
			p.bodyParts[i] = MeshDraw.planar(com.rocraft.rbx.RbxMesh.read(RobloxApi.asset(mesh)), R6Model.BOXES[i]);
		}
	}

	/** DynamicHead asset -> its head mesh with the face texture laid over the head colour (texture alpha = features). */
	static MeshDraw dynamicHead(long id, int headColor) throws Exception {
		var model = com.rocraft.rbx.RbxModel.read(RobloxApi.asset(id));
		var sm = model.first("SpecialMesh") != null ? model.first("SpecialMesh") : model.first("MeshPart");
		var mesh = com.rocraft.rbx.RbxMesh.read(Rig.content(sm.str("MeshId")));
		String texRef = sm.str("TextureId") != null ? sm.str("TextureId") : sm.str("TextureID");
		BufferedImage tex = ImageIO.read(new ByteArrayInputStream(Rig.content(texRef)));
		var out = new BufferedImage(tex.getWidth(), tex.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int hr = headColor >> 16 & 255, hg = headColor >> 8 & 255, hb = headColor & 255;
		for (int y = 0; y < tex.getHeight(); y++) for (int x = 0; x < tex.getWidth(); x++) {
			int c = tex.getRGB(x, y), a = c >>> 24;
			int r = ((c >> 16 & 255) * a + hr * (255 - a)) / 255, g = ((c >> 8 & 255) * a + hg * (255 - a)) / 255, b = ((c & 255) * a + hb * (255 - a)) / 255;
			out.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
		}
		return MeshDraw.of(mesh, new org.joml.Matrix4f(), out);
	}

	/** Face asset -> its Decal image. */
	static BufferedImage faceDecal(long id) throws Exception {
		var decal = com.rocraft.rbx.RbxModel.read(RobloxApi.asset(id)).first("Decal");
		return ImageIO.read(new ByteArrayInputStream(Rig.content(decal.str("Texture"))));
	}

	/** Clothing asset (rbxmx) -> its template image. */
	static BufferedImage template(long clothingId, String prop) throws Exception {
		String xml = new String(RobloxApi.asset(clothingId), StandardCharsets.UTF_8);
		long img = RobloxApi.idAfter(xml, "name=\"" + prop + "\"");
		return img < 0 ? null : ImageIO.read(new ByteArrayInputStream(RobloxApi.asset(img)));
	}

	static JsonElement send(HttpRequest r) throws Exception {
		HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode());
		return JsonParser.parseString(res.body());
	}
}
