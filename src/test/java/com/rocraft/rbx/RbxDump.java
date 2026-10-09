package com.rocraft.rbx;

import java.nio.file.*;

/** ./gradlew rbxDump --args="files...": prints model trees (key props) and mesh stats. Offline oracle for the readers. */
public final class RbxDump {
	public static void main(String[] a) throws Exception {
		for (String f : a) {
			byte[] b = Files.readAllBytes(Path.of(f));
			String head = new String(b, 0, Math.min(12, b.length), "US-ASCII");
			System.out.println("== " + Path.of(f).getFileName());
			if (head.startsWith("version")) {
				try {
					var m = RbxMesh.read(b);
					float[] bb = m.bounds();
					System.out.printf("  mesh %s: %d verts, %d tris, size %.2f x %.2f x %.2f, center %.2f %.2f %.2f%n", head.trim(), m.vertexCount(), m.tris.length / 3,
						bb[3] - bb[0], bb[4] - bb[1], bb[5] - bb[2], (bb[3] + bb[0]) / 2, (bb[4] + bb[1]) / 2, (bb[5] + bb[2]) / 2);
				} catch (Exception e) { System.out.println("  mesh " + head.trim() + ": " + e.getMessage()); }
				continue;
			}
			var m = RbxModel.read(b);
			for (var r : m.roots) print(r, "  ");
		}
	}

	static void print(RbxModel.Inst i, String ind) {
		StringBuilder s = new StringBuilder(ind + i);
		for (String k : new String[]{"MeshId", "MeshID", "TextureID", "TextureId", "MeshType", "Size", "InitialSize", "size", "Scale", "Offset", "CFrame", "AttachmentPoint", "Grip", "C0", "C1", "SoundId", "AnimationId", "Time", "Weight", "Loop", "Priority", "Value"}) {
			Object v = i.props.get(k);
			if (v == null) continue;
			s.append(" ").append(k).append("=").append(v instanceof float[] f ? java.util.Arrays.toString(f) : v);
		}
		System.out.println(s);
		for (var c : i.children) print(c, ind + "  ");
	}
}
