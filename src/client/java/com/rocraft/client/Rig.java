package com.rocraft.client;

import com.rocraft.Rocraft;
import com.rocraft.rbx.RbxMesh;
import com.rocraft.rbx.RbxModel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;

/**
 * Roblox R6 rig glued onto Minecraft's player model parts. Part order everywhere:
 * 0 Head, 1 Torso, 2 Right Arm, 3 Left Arm, 4 Right Leg, 5 Left Leg.
 * 1 stud = 6 model units (R6Model); Roblox (x, y, z) -> model (-x, -y, z).
 */
final class Rig {
	static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3;
	static final String[] BODY_MESH = {"avatar/heads/head.mesh", "avatar/meshes/torso.mesh", "avatar/meshes/rightarm.mesh",
		"avatar/meshes/leftarm.mesh", "avatar/meshes/rightleg.mesh", "avatar/meshes/leftleg.mesh"};
	/** Roblox part centre relative to the MC part pivot, model units (y down). Head sits 0.5 stud above the neck. */
	static final float[][] CENTER = {{0, -3, 0}, {0, 6, 0}, {0, 4, 0}, {0, 4, 0}, {0, 6, 0}, {0, 6, 0}};

	record Attach(int part, float x, float y, float z) {}
	/** R6 character attachments (part space, studs), as Roblox's R6 rig defines them. */
	static final Map<String, Attach> ATTACH = Map.ofEntries(
		Map.entry("HatAttachment", new Attach(HEAD, 0, 0.6f, 0)), Map.entry("HairAttachment", new Attach(HEAD, 0, 0.6f, 0)),
		Map.entry("FaceFrontAttachment", new Attach(HEAD, 0, 0, -0.6f)), Map.entry("FaceCenterAttachment", new Attach(HEAD, 0, 0, 0)),
		Map.entry("NeckAttachment", new Attach(TORSO, 0, 1, 0)), Map.entry("BodyFrontAttachment", new Attach(TORSO, 0, 0, -0.5f)),
		Map.entry("BodyBackAttachment", new Attach(TORSO, 0, 0, 0.5f)), Map.entry("LeftCollarAttachment", new Attach(TORSO, -1, 1, 0)),
		Map.entry("RightCollarAttachment", new Attach(TORSO, 1, 1, 0)), Map.entry("WaistFrontAttachment", new Attach(TORSO, 0, -1, -0.5f)),
		Map.entry("WaistCenterAttachment", new Attach(TORSO, 0, -1, 0)), Map.entry("WaistBackAttachment", new Attach(TORSO, 0, -1, 0.5f)),
		Map.entry("LeftShoulderAttachment", new Attach(LEFT_ARM, 0, 1, 0)), Map.entry("RightShoulderAttachment", new Attach(RIGHT_ARM, 0, 1, 0)),
		Map.entry("LeftGripAttachment", new Attach(LEFT_ARM, 0, -1, 0)), Map.entry("RightGripAttachment", new Attach(RIGHT_ARM, 0, -1, 0)),
		Map.entry("LeftFootAttachment", new Attach(5, 0, -1, 0)), Map.entry("RightFootAttachment", new Attach(4, 0, -1, 0)));

	/** Something drawn on one body part. */
	record Piece(int part, MeshDraw draw) {}

	private static MeshDraw[] body;
	/** Held gear on the right arm (grip applied), and the bare Handle mesh (for dropped/thrown copies like the Time Bomb). */
	static final Map<net.minecraft.world.item.Item, Piece> GEAR = new java.util.concurrent.ConcurrentHashMap<>();
	static final Map<String, MeshDraw> HANDLES = new java.util.concurrent.ConcurrentHashMap<>();

	static final Map<net.minecraft.world.item.Item, Piece> GEAR_ALT = new java.util.concurrent.ConcurrentHashMap<>(); // e.g. Bloxy Cola while drinking
	static Piece gear(net.minecraft.world.item.ItemStack s) { return s.isEmpty() ? null : GEAR.get(s.getItem()); }

	/** Roblox's own R6 body meshes from the user's install, or null without one. */
	static MeshDraw[] body() {
		if (body == null && RobloxAssets.CONTENT != null) {
			var b = new MeshDraw[6];
			try {
				for (int i = 0; i < 6; i++) b[i] = MeshDraw.planar(RbxMesh.read(Files.readAllBytes(RobloxAssets.file(BODY_MESH[i]))), R6Model.BOXES[i]);
				body = b;
			} catch (Exception e) {
				Rocraft.LOGGER.warn("R6 body meshes unavailable: {}", e.toString());
				body = new MeshDraw[0];
			}
		}
		return body == null || body.length == 0 ? null : body;
	}

	static Matrix4f cframe(float[] c) { return com.rocraft.rbx.R6.cframe(c); }

	/** Bytes of a mesh/texture referenced by a model: rbxasset:// from the legacy export, otherwise an asset id. */
	static byte[] content(String ref) throws Exception {
		if (ref != null && ref.startsWith("rbxasset://")) {
			var f = RobloxAssets.legacy(ref);
			if (f == null) throw new java.io.FileNotFoundException(ref + " (export it from Studio, see tools/export_classic_tools.luau)");
			return Files.readAllBytes(f);
		}
		long id = RbxModel.assetId(ref);
		if (id < 0) throw new java.io.FileNotFoundException("no asset in '" + ref + "'");
		return RobloxApi.asset(id);
	}

	/** Mesh + texture of an accessory or tool Handle (SpecialMesh, MeshPart, or a plain ball Part), Offset/Scale baked in after `place`. */
	static MeshDraw handleMesh(RbxModel.Inst handle, Matrix4f place) throws Exception {
		var sm = handle.child("SpecialMesh");
		float[] size = handle.vec3("size") != null ? handle.vec3("size") : handle.vec3("Size");
		String meshRef = sm != null ? sm.str("MeshId") : handle.str("MeshId");
		String texRef = sm != null ? sm.str("TextureId") : handle.str("TextureID");
		if (meshRef == null || meshRef.isBlank()) { // a plain Part handle (Superball): a ball of the part's colour
			var ball = MeshDraw.ball(size == null ? 1 : Math.min(size[0], Math.min(size[1], size[2])));
			var col = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			String c = handle.str("Color3uint8");
			col.setRGB(0, 0, 0xFF000000 | (c == null ? 0xC4281C : (int) Double.parseDouble(c)));
			return MeshDraw.transformed(ball, place, col);
		}
		RbxMesh mesh = RbxMesh.read(content(meshRef));
		float[] scale = {1, 1, 1}, offset = {0, 0, 0};
		if (sm != null) {
			if (sm.vec3("Scale") != null) scale = sm.vec3("Scale");
			if (sm.vec3("Offset") != null) offset = sm.vec3("Offset");
		} else if (size != null) { // MeshPart: mesh is stretched to the part's Size
			float[] bb = mesh.bounds();
			scale = new float[]{size[0] / (bb[3] - bb[0]), size[1] / (bb[4] - bb[1]), size[2] / (bb[5] - bb[2])};
		}
		BufferedImage img = texRef == null || texRef.isBlank() ? null : ImageIO.read(new ByteArrayInputStream(content(texRef)));
		var m = new Matrix4f(place).translate(offset[0], offset[1], offset[2]).scale(scale[0], scale[1], scale[2]);
		return MeshDraw.of(mesh, m, img);
	}

	/** Accessory -> piece on the right body part: charAttachment * handleAttachment^-1 (legacy hats: head top * AttachmentPoint^-1). */
	static Piece accessory(long assetId) throws Exception {
		var model = RbxModel.read(RobloxApi.asset(assetId));
		var acc = model.first("Accessory") != null ? model.first("Accessory") : model.first("Hat");
		if (acc == null) return null;
		RbxModel.Inst handle = null;
		for (var c : acc.children) if ("Handle".equals(c.name())) handle = c;
		if (handle == null) return null;
		Attach at = null;
		RbxModel.Inst att = null;
		for (var c : handle.children) if (c.className.equals("Attachment") && ATTACH.containsKey(c.name())) { att = c; at = ATTACH.get(c.name()); }
		Matrix4f place = at != null
			? new Matrix4f().translate(at.x(), at.y(), at.z()).mul(cframe(att.cframe("CFrame")).invert())
			: new Matrix4f().translate(0, 0.5f, 0).mul(cframe(acc.cframe("AttachmentPoint")).invert());
		return new Piece(at != null ? at.part() : HEAD, handleMesh(handle, place));
	}

	/** Grip CFrame from Tool.GripPos/Forward/Right/Up (columns Right, Up, Right x Up; LookVector = GripForward). */
	static float[] grip(float px, float py, float pz, float rx, float ry, float rz, float ux, float uy, float uz) {
		float zx = ry * uz - rz * uy, zy = rz * ux - rx * uz, zz = rx * uy - ry * ux;
		return new float[]{px, py, pz, rx, ux, zx, ry, uy, zy, rz, uz, zz};
	}

	/** "Using" grips the scripts switch to while eating / drinking / hugging. */
	static final Map<String, float[]> USE_GRIP = Map.of(
		"bloxy_cola", grip(1.5f, -0.5f, 0.3f, 1, 0, 0, 0, 0.651f, -0.759f),
		"taco", grip(-0.2f, 0, -1.23f, 0.197f, 0.581f, -0.79f, -0.141f, 0.814f, 0.563f),
		"burger", grip(-0.5f, -0.6f, -1.5f, 0, 0, -1, 0.196f, 0.981f, 0),
		"chicken", grip(0.4f, -0.59f, 1.1f, 0.212f, -0.212f, 0.954f, 0.707f, 0.707f, 0),
		"pizza", grip(-1.5f, -0.9f, 0.5f, -1, 0, 0, 0, 1, 0),
		"teddy", grip(0.5f, -1.5f, -1.56f, 0, -0.707f, -0.707f, 0, -0.707f, 0.707f));

	/** Tool model bytes for a GEAR source: "asset:<id>" or "legacy:<Name>" (config/rocraft/legacy/tools/<Name>.rbxmx). */
	static byte[] toolModel(String source) throws Exception {
		if (source.startsWith("legacy:")) {
			var f = RobloxAssets.legacyDir().resolve("tools/" + source.substring(7) + ".rbxmx");
			if (!Files.exists(f)) throw new java.io.FileNotFoundException(f + " (export it from Studio, see tools/export_classic_tools.luau)");
			return Files.readAllBytes(f);
		}
		return RobloxApi.asset(Long.parseLong(source.substring(6)));
	}

	/** Gear in the right hand: Right Arm * RightGrip.C0 * Tool.Grip^-1 (classic R6 grip). */
	static void loadGear(String name, String source) {
		try {
			var tool = RbxModel.read(toolModel(source)).first("Tool");
			RbxModel.Inst handle = null;
			for (var c : tool.children) if ("Handle".equals(c.name())) handle = c;
			var c0 = cframe(new float[]{0, -1, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0});
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Rocraft.id(name));
			GEAR.put(item, new Piece(RIGHT_ARM, handleMesh(handle, new Matrix4f(c0).mul(cframe(tool.cframe("Grip")).invert()))));
			HANDLES.put(name, handleMesh(handle, new Matrix4f()));
			if (USE_GRIP.containsKey(name))
				GEAR_ALT.put(item, new Piece(RIGHT_ARM, handleMesh(handle, new Matrix4f(c0).mul(cframe(USE_GRIP.get(name)).invert()))));
		} catch (Exception e) {
			Rocraft.LOGGER.warn("{} mesh unavailable: {}", name, e.toString());
		}
	}
}
