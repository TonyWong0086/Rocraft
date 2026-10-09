package com.rocraft.rbx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Roblox .mesh reader, versions 1.00-5.00 (LOD 0 only, no skinning). Positions in studs, UV with (0,0) top-left.
 * v6/v7 (chunked, often Draco) throw; callers skip the mesh.
 */
public final class RbxMesh {
	public final float[] pos, nrm, uv; // 3, 3, 2 floats per vertex
	public final int[] tris;           // 3 indices per triangle

	RbxMesh(float[] pos, float[] nrm, float[] uv, int[] tris) { this.pos = pos; this.nrm = nrm; this.uv = uv; this.tris = tris; }

	public int vertexCount() { return pos.length / 3; }

	/** {minX, minY, minZ, maxX, maxY, maxZ} */
	public float[] bounds() {
		float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int i = 0; i < pos.length; i++) { b[i % 3] = Math.min(b[i % 3], pos[i]); b[3 + i % 3] = Math.max(b[3 + i % 3], pos[i]); }
		return b;
	}

	public static RbxMesh read(byte[] data) {
		int nl = 0;
		while (nl < data.length && data[nl] != '\n') nl++;
		String ver = new String(data, 0, nl, StandardCharsets.US_ASCII).trim();
		return switch (ver) {
			case "version 1.00", "version 1.01" -> v1(new String(data, nl + 1, data.length - nl - 1, StandardCharsets.US_ASCII), ver.endsWith("00"));
			case "version 2.00", "version 3.00", "version 3.01", "version 4.00", "version 4.01", "version 5.00" -> binary(data, nl + 1, ver.charAt(8) - '0');
			default -> throw new IllegalArgumentException("unsupported mesh " + ver);
		};
	}

	private static final Pattern NUM = Pattern.compile("[-+0-9.eE]+");

	/** Text: face count, then per vertex [px,py,pz][nx,ny,nz][u,v,w]. 1.00 is double size; V is bottom-up. */
	private static RbxMesh v1(String s, boolean halve) {
		var m = NUM.matcher(s);
		m.find();
		int faces = (int) Double.parseDouble(m.group()), n = faces * 3;
		float[] p = new float[n * 3], nr = new float[n * 3], uv = new float[n * 2];
		float k = halve ? 0.5f : 1f;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 3; j++) { m.find(); p[i * 3 + j] = Float.parseFloat(m.group()) * k; }
			for (int j = 0; j < 3; j++) { m.find(); nr[i * 3 + j] = Float.parseFloat(m.group()); }
			m.find(); uv[i * 2] = Float.parseFloat(m.group());
			m.find(); uv[i * 2 + 1] = 1 - Float.parseFloat(m.group());
			m.find(); // w
		}
		int[] t = new int[n];
		for (int i = 0; i < n; i++) t[i] = i;
		return new RbxMesh(p, nr, uv, t);
	}

	/** v2-v5: little-endian header, vertices (pos, normal, uv, tangent/color), faces, then v3+ LOD offsets. */
	private static RbxMesh binary(byte[] data, int off, int major) {
		var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		b.position(off);
		int headerSize = b.getShort() & 0xFFFF, vSize, nV, nF, nLods = 0, nBones = 0;
		if (major <= 3) {
			vSize = b.get() & 0xFF;
			b.get(); // face size
			if (major == 3) { b.getShort(); nLods = b.getShort() & 0xFFFF; }
			nV = b.getInt();
			nF = b.getInt();
		} else {
			b.getShort(); // lod type
			nV = b.getInt();
			nF = b.getInt();
			nLods = b.getShort() & 0xFFFF;
			nBones = b.getShort() & 0xFFFF;
			vSize = 40;
		}
		b.position(off + headerSize);
		float[] p = new float[nV * 3], nr = new float[nV * 3], uv = new float[nV * 2];
		for (int i = 0; i < nV; i++) {
			int start = b.position();
			for (int j = 0; j < 3; j++) p[i * 3 + j] = b.getFloat();
			for (int j = 0; j < 3; j++) nr[i * 3 + j] = b.getFloat();
			uv[i * 2] = b.getFloat();
			uv[i * 2 + 1] = b.getFloat();
			b.position(start + vSize);
		}
		if (nBones > 0) b.position(b.position() + nV * 8); // skin envelopes
		int[] faces = new int[nF * 3];
		for (int i = 0; i < faces.length; i++) faces[i] = b.getInt();
		int end = nF;
		if (nLods >= 2) {
			b.getInt(); // lod 0 starts at face 0
			end = Math.min(nF, b.getInt());
		}
		int[] t = new int[end * 3];
		System.arraycopy(faces, 0, t, 0, t.length);
		return new RbxMesh(p, nr, uv, t);
	}
}
