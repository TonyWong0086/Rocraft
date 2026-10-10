package com.rocraft.client;

import com.rocraft.rbx.R15;
import com.rocraft.rbx.RbxMesh;
import com.rocraft.rbx.RbxModel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;

/**
 * One character's R15 body, put together the way Roblox does: the default rig (the install's characterR15.rbxm) with
 * each worn body package's R15 parts swapped in (their meshes, sizes and rig attachments, so joints move with the
 * package), and clothing composited onto the R15 atlases by Roblox's own compositing meshes (template -> 388x272
 * torso atlas, 264x284 limb atlas). The head is drawn like R6 (classic head or DynamicHead).
 */
final class R15Body {
	static final String[] JOINT = {"Neck", "Waist", "Root", "RightShoulder", "RightElbow", "RightWrist", "LeftShoulder", "LeftElbow",
		"LeftWrist", "RightHip", "RightKnee", "RightAnkle", "LeftHip", "LeftKnee", "LeftAnkle"};
	/** Which body colour / atlas each part takes: 0 head, 1 torso, 2 left arm, 3 right arm, 4 left leg, 5 right leg (profile colour order). */
	static final int[] GROUP = {0, 1, 1, 3, 3, 3, 2, 2, 2, 5, 5, 5, 4, 4, 4};
	/** Body part asset type -> its R15 parts. */
	static final Map<String, List<String>> PACKAGE_PARTS = Map.of("Torso", List.of("UpperTorso", "LowerTorso"),
		"LeftArm", List.of("LeftUpperArm", "LeftLowerArm", "LeftHand"), "RightArm", List.of("RightUpperArm", "RightLowerArm", "RightHand"),
		"LeftLeg", List.of("LeftUpperLeg", "LeftLowerLeg", "LeftFoot"), "RightLeg", List.of("RightUpperLeg", "RightLowerLeg", "RightFoot"));

	final MeshDraw[] parts = new MeshDraw[15]; // [0] unused: the head is drawn like R6
	final float[][] c0 = new float[15][], c1 = new float[15][];
	/** Accessory attachment name -> {part, x, y, z} (part space, studs). */
	final Map<String, float[]> attach = new HashMap<>();
	/** HumanoidRootPart centre to the soles, studs (R6 is 3). */
	float rootToFeet = 2.35f;
	/** Head centre above the root at rest, studs (R6 is 1.5). */
	float headY = 2.15f;

	private static final class Part {
		String mesh, tex;
		float[] size;
		final Map<String, float[]> att = new HashMap<>();
	}

	/** bodyAssets: asset type name -> id of the worn body parts. Blocking (downloads). */
	static R15Body build(Map<String, Long> bodyAssets, int[] colors, BufferedImage shirt, BufferedImage pants) throws Exception {
		Map<String, Part> rig = new HashMap<>();
		read(RbxModel.read(Files.readAllBytes(RobloxAssets.file("avatar/characterR15.rbxm"))), null, rig);
		for (var e : bodyAssets.entrySet()) {
			if (!PACKAGE_PARTS.containsKey(e.getKey())) continue;
			var model = RbxModel.read(RobloxApi.asset(e.getValue()));
			Map<String, Part> pkg = new HashMap<>();
			for (String folder : new String[]{"R15ArtistIntent", "R15Fixed", "R15"}) if (pkg.isEmpty()) read(model, folder, pkg);
			for (String n : PACKAGE_PARTS.get(e.getKey())) if (pkg.containsKey(n)) rig.put(n, pkg.get(n));
		}
		var b = new R15Body();
		Part root = rig.get("HumanoidRootPart");
		for (int i = 0; i < 15; i++) {
			String j = JOINT[i] + "RigAttachment";
			Part p0 = R15.PARENT[i] < 0 ? root : rig.get(R15.PARTS[R15.PARENT[i]]), p1 = rig.get(R15.PARTS[i]);
			b.c0[i] = p0 != null && p0.att.containsKey(j) ? p0.att.get(j) : R15.C0[i];
			b.c1[i] = p1 != null && p1.att.containsKey(j) ? p1.att.get(j) : R15.C1[i];
		}
		for (int i = 0; i < 15; i++) {
			Part p = rig.get(R15.PARTS[i]);
			if (p == null) continue;
			for (var a : p.att.entrySet()) if (!a.getKey().endsWith("RigAttachment"))
				b.attach.put(a.getKey(), new float[]{i, a.getValue()[0], a.getValue()[1], a.getValue()[2]});
		}
		// the soles at rest set how high the root rides (Roblox's HipHeight)
		var rest = R15.solve(b.c0, b.c1, n -> null);
		float low = Float.MAX_VALUE;
		for (int f : new int[]{R15.RIGHT_FOOT, R15.LEFT_FOOT}) {
			Part p = rig.get(R15.PARTS[f]);
			if (p != null && p.size != null) low = Math.min(low, rest[f].m31() - p.size[1] / 2);
		}
		if (low < 0) b.rootToFeet = -low;
		b.headY = rest[R15.HEAD].m31();

		BufferedImage torso = atlas(388, 272, colors[1]);
		paint(torso, "R15CompositTorsoBase.mesh", pants);
		paint(torso, "R15CompositTorsoBase.mesh", shirt);
		BufferedImage[] limb = new BufferedImage[6];
		for (int g = 2; g < 6; g++) {
			limb[g] = atlas(264, 284, colors[g]);
			paint(limb[g], g % 2 == 0 ? "R15CompositLeftArmBase.mesh" : "R15CompositRightArmBase.mesh", g < 4 ? shirt : pants);
		}
		for (int i = 1; i < 15; i++) {
			Part p = rig.get(R15.PARTS[i]);
			if (p == null || p.mesh == null) continue;
			RbxMesh mesh = RbxMesh.read(Rig.content(p.mesh));
			float[] bb = mesh.bounds();
			var m = p.size == null ? new Matrix4f() : new Matrix4f().scale(p.size[0] / (bb[3] - bb[0]), p.size[1] / (bb[4] - bb[1]), p.size[2] / (bb[5] - bb[2]));
			BufferedImage img = copy(GROUP[i] == 1 ? torso : limb[GROUP[i]]);
			if (p.tex != null && !p.tex.isBlank()) { // a package's own skin, over the body colour and under the clothes
				var skin = ImageIO.read(new ByteArrayInputStream(Rig.content(p.tex)));
				var base = atlas(img.getWidth(), img.getHeight(), colors[GROUP[i]]);
				base.createGraphics().drawImage(skin, 0, 0, img.getWidth(), img.getHeight(), null);
				img = base;
				if (GROUP[i] == 1) { paint(img, "R15CompositTorsoBase.mesh", pants); paint(img, "R15CompositTorsoBase.mesh", shirt); }
				else paint(img, GROUP[i] % 2 == 0 ? "R15CompositLeftArmBase.mesh" : "R15CompositRightArmBase.mesh", GROUP[i] < 4 ? shirt : pants);
			}
			b.parts[i] = MeshDraw.of(mesh, m, img);
		}
		return b;
	}

	/** Parts (MeshParts / Parts named like R15 parts) and their attachments, from one folder of a model (null = anywhere). */
	private static void read(RbxModel model, String folder, Map<String, Part> out) {
		for (var i : model.all) {
			if (!(i.className.equals("MeshPart") || i.className.equals("Part"))) continue;
			if (folder != null && (i.parent == null || !folder.equals(i.parent.name()))) continue;
			var p = new Part();
			p.mesh = i.str("MeshId");
			p.tex = i.str("TextureID");
			p.size = i.vec3("size") != null ? i.vec3("size") : i.vec3("Size");
			for (var c : i.children) if (c.className.equals("Attachment") && c.cframe("CFrame") != null) {
				float[] cf = c.cframe("CFrame");
				p.att.put(c.name(), new float[]{cf[0], cf[1], cf[2]});
			}
			out.put(i.name(), p);
		}
	}

	static BufferedImage atlas(int w, int h, int rgb) {
		var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, 0xFF000000 | rgb);
		return img;
	}

	private static BufferedImage copy(BufferedImage src) {
		var img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		img.createGraphics().drawImage(src, 0, 0, null);
		return img;
	}

	private static final Map<String, RbxMesh> COMPOSIT = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Lays a clothing template onto an atlas through one of Roblox's compositing meshes (vertex x/y = atlas pixel,
	 * y up; UV = template position): every atlas pixel inside a triangle takes the template pixel it maps to, alpha over.
	 */
	static void paint(BufferedImage dst, String compMesh, BufferedImage template) throws Exception {
		if (template == null) return;
		RbxMesh m = COMPOSIT.get(compMesh);
		if (m == null) COMPOSIT.put(compMesh, m = RbxMesh.read(Files.readAllBytes(RobloxAssets.file("avatar/compositing/" + compMesh))));
		int W = dst.getWidth(), H = dst.getHeight(), tw = template.getWidth(), th = template.getHeight();
		for (int t = 0; t < m.tris.length; t += 3) {
			int a = m.tris[t], b = m.tris[t + 1], c = m.tris[t + 2];
			float ax = m.pos[a * 3], ay = H - m.pos[a * 3 + 1], bx = m.pos[b * 3], by = H - m.pos[b * 3 + 1], cx = m.pos[c * 3], cy = H - m.pos[c * 3 + 1];
			float den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
			if (Math.abs(den) < 1e-6f) continue;
			int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
			int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
			for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float l0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den, l1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den, l2 = 1 - l0 - l1;
				if (l0 < -1e-4f || l1 < -1e-4f || l2 < -1e-4f) continue;
				float u = l0 * m.uv[a * 2] + l1 * m.uv[b * 2] + l2 * m.uv[c * 2], v = l0 * m.uv[a * 2 + 1] + l1 * m.uv[b * 2 + 1] + l2 * m.uv[c * 2 + 1];
				int s = template.getRGB(Math.clamp((int) (u * tw), 0, tw - 1), Math.clamp((int) (v * th), 0, th - 1)), al = s >>> 24;
				if (al == 0) continue;
				int d = dst.getRGB(x, y);
				int r = ((s >> 16 & 255) * al + (d >> 16 & 255) * (255 - al)) / 255, g = ((s >> 8 & 255) * al + (d >> 8 & 255) * (255 - al)) / 255,
					bl = ((s & 255) * al + (d & 255) * (255 - al)) / 255;
				dst.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | bl);
			}
		}
	}
}
