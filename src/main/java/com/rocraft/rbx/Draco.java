package com.rocraft.rbx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal decoder for Google Draco 2.2 triangle meshes with sequential encoding, the compression Roblox's v6/v7 mesh
 * COREMESH chunk uses. Follows the reference decoder (draco/compression/...): connectivity (raw or delta + rANS
 * indices), then per attribute: rANS-coded symbols, difference prediction with the wrap or octahedron transform, and
 * quantisation. Edgebreaker-encoded and metadata-carrying files throw.
 */
final class Draco {
	static final int POSITION = 0, NORMAL = 1, COLOR = 2, TEX_COORD = 3, GENERIC = 4;

	/** One decoded attribute: its type and per-point values (components per point). */
	record Attr(int type, int components, float[] values) {}
	record Mesh(int[] faces, int points, List<Attr> attrs) {
		Attr get(int type) { for (Attr a : attrs) if (a.type == type) return a; return null; }
	}

	private final byte[] b;
	private int p;

	private Draco(byte[] b, int p) { this.b = b; this.p = p; }

	static Mesh decode(byte[] data, int off) { return new Draco(data, off).mesh(); }

	// ---- buffer ---------------------------------------------------------------------------------------------------

	private int u8() { return b[p++] & 0xFF; }
	private int u16() { int v = (b[p] & 0xFF) | (b[p + 1] & 0xFF) << 8; p += 2; return v; }
	private int i32() { int v = (b[p] & 0xFF) | (b[p + 1] & 0xFF) << 8 | (b[p + 2] & 0xFF) << 16 | (b[p + 3] & 0xFF) << 24; p += 4; return v; }
	private float f32() { return Float.intBitsToFloat(i32()); }

	private long varint() {
		long v = 0;
		for (int shift = 0; ; shift += 7) {
			int c = u8();
			v |= (long) (c & 0x7F) << shift;
			if ((c & 0x80) == 0) return v;
		}
	}

	// ---- mesh -----------------------------------------------------------------------------------------------------

	private Mesh mesh() {
		if (!new String(b, p, 5, StandardCharsets.US_ASCII).equals("DRACO")) throw new IllegalArgumentException("not a Draco stream");
		p += 5;
		int major = u8(), minor = u8(), type = u8(), method = u8(), flags = u16();
		if (major != 2 || minor != 2) throw new IllegalArgumentException("Draco " + major + "." + minor + " unsupported");
		if (type != 1) throw new IllegalArgumentException("Draco point clouds unsupported");
		if (method != 0) throw new IllegalArgumentException("Draco edgebreaker meshes unsupported");
		if ((flags & 0x8000) != 0) throw new IllegalArgumentException("Draco metadata unsupported");

		int numFaces = (int) varint(), numPoints = (int) varint();
		int[] faces = new int[numFaces * 3];
		if (u8() == 1) { // uncompressed indices, sized by the point count
			for (int i = 0; i < faces.length; i++)
				faces[i] = numPoints < 256 ? u8() : numPoints < (1 << 16) ? u16() : numPoints < (1 << 21) ? (int) varint() : i32();
		} else { // delta-coded, zigzag, rANS
			int[] s = symbols(faces.length, 1);
			int last = 0;
			for (int i = 0; i < faces.length; i++) {
				int d = s[i] >>> 1;
				if ((s[i] & 1) != 0) d = -d;
				faces[i] = last += d;
			}
		}

		List<Attr> out = new ArrayList<>();
		int decoders = u8();
		List<int[][]> specs = new ArrayList<>(); // per decoder: {type, dataType, components, decoderType}
		for (int d = 0; d < decoders; d++) {
			int n = (int) varint();
			int[][] atts = new int[n][4];
			for (int a = 0; a < n; a++) {
				atts[a][0] = u8(); atts[a][1] = u8(); atts[a][2] = u8();
				u8();     // normalized
				varint(); // unique id
			}
			for (int a = 0; a < n; a++) atts[a][3] = u8();
			specs.add(atts);
		}
		for (int[][] atts : specs) {
			int[][] portable = new int[atts.length][];
			for (int a = 0; a < atts.length; a++) portable[a] = portableValues(atts[a], numPoints);
			for (int a = 0; a < atts.length; a++) out.add(toOriginal(atts[a], portable[a], numPoints));
		}
		return new Mesh(faces, numPoints, out);
	}

	/** Components the decoder stores per point: octahedral normals are 2, everything else its own count. */
	private static int portableComponents(int[] att) { return att[3] == 3 ? 2 : att[2]; }

	/** SequentialIntegerAttributeDecoder::DecodeValues (generic attributes are raw bytes). */
	private int[] portableValues(int[] att, int numPoints) {
		int comps = portableComponents(att), n = numPoints * comps;
		if (att[3] == 0) { // generic: raw values of the attribute's own data type
			int size = typeSize(att[1]);
			int[] v = new int[n];
			for (int i = 0; i < n; i++) { int x = 0; for (int k = 0; k < size; k++) x |= u8() << (8 * k); v[i] = x; }
			return v;
		}
		int predMethod = (byte) u8(), transform = -1;
		if (predMethod != -2) transform = (byte) u8();
		int[] v;
		if (u8() > 0) v = symbols(n, comps);
		else {
			int bytes = u8();
			v = new int[n];
			for (int i = 0; i < n; i++) { int x = 0; for (int k = 0; k < bytes; k++) x |= u8() << (8 * k); v[i] = x; }
		}
		boolean positiveCorrections = transform == 2 || transform == 3; // octahedron transforms keep corrections unsigned
		if (n > 0 && (predMethod == -2 || !positiveCorrections))
			for (int i = 0; i < n; i++) v[i] = (v[i] >>> 1) ^ -(v[i] & 1);
		if (predMethod != -2) {
			if (predMethod != 0) throw new IllegalArgumentException("Draco prediction " + predMethod + " unsupported");
			if (transform == 1) { // wrap: corrections relative to the previous point, clamped and wrapped into [min, max]
				int min = i32(), max = i32(), dif = 1 + max - min;
				for (int i = 0; i < n; i++) {
					int pred = i < comps ? 0 : v[i - comps];
					pred = Math.max(min, Math.min(max, pred));
					int val = pred + v[i];
					if (val > max) val -= dif; else if (val < min) val += dif;
					v[i] = val;
				}
			} else if (transform == 2 || transform == 3) {
				i32(); // max quantized value (normals are rebuilt from the faces, so the octahedral values aren't needed)
			} else {
				for (int i = comps; i < n; i++) v[i] += v[i - comps]; // plain difference
			}
		}
		return v;
	}

	/** Quantised floats back to their range; normals' octahedral data skipped (computed from the faces later). */
	private Attr toOriginal(int[] att, int[] v, int numPoints) {
		int comps = att[2];
		float[] f = new float[numPoints * comps];
		switch (att[3]) {
			case 2 -> { // quantisation: min per component, range, bits
				float[] min = new float[comps];
				for (int c = 0; c < comps; c++) min[c] = f32();
				float range = f32();
				int bits = u8();
				float delta = range / ((1 << bits) - 1);
				for (int i = 0; i < f.length; i++) f[i] = v[i] * delta + min[i % comps];
			}
			case 3 -> u8(); // normals: quantisation bits
			default -> { // generic float32 is raw bits; integer types are their value
				for (int i = 0; i < f.length; i++) f[i] = att[1] == 9 ? Float.intBitsToFloat(v[i]) : v[i];
			}
		}
		return new Attr(att[0], comps, f);
	}

	private static int typeSize(int dt) {
		return switch (dt) { case 1, 2, 11 -> 1; case 3, 4 -> 2; case 7, 8, 10 -> 8; default -> 4; };
	}

	// ---- symbols (rANS) -------------------------------------------------------------------------------------------

	private int[] symbols(int n, int comps) {
		int[] out = new int[n];
		if (n == 0) return out;
		int scheme = u8();
		if (scheme == 0) { // tagged: rANS bit lengths, then raw bits
			var tags = new Rans(12);
			tags.create();
			tags.start();
			int bitPos = 0, start = p;
			for (int i = 0; i < n; i += comps) {
				int len = tags.read();
				for (int j = 0; j < comps; j++) {
					int x = 0;
					for (int k = 0; k < len; k++, bitPos++) x |= ((b[start + (bitPos >> 3)] >> (bitPos & 7)) & 1) << k;
					out[i + j] = x;
				}
			}
			p = start + (bitPos + 7) / 8;
		} else { // raw
			int bitLength = u8();
			var r = new Rans(Math.max(12, Math.min(20, 3 * bitLength / 2)));
			r.create();
			r.start();
			for (int i = 0; i < n; i++) out[i] = r.read();
		}
		return out;
	}

	/** RAnsSymbolDecoder: probability table, then a reverse-read rANS state over its byte block. */
	private final class Rans {
		final int precision, lBase;
		int[] prob, cum, lut;
		long state;
		int buf, off;

		Rans(int bits) { precision = 1 << bits; lBase = precision * 4; }

		void create() {
			int num = (int) varint();
			prob = new int[num];
			for (int i = 0; i < num; i++) {
				int d = u8(), token = d & 3;
				if (token == 3) { i += d >> 2; continue; } // a run of (d >> 2) + 1 zero probabilities
				int pr = d >> 2;
				for (int k = 0; k < token; k++) pr |= u8() << (8 * (k + 1) - 2);
				prob[i] = pr;
			}
			cum = new int[num];
			lut = new int[precision];
			int c = 0;
			for (int i = 0; i < num; i++) {
				cum[i] = c;
				for (int j = c; j < c + prob[i] && j < precision; j++) lut[j] = i;
				c += prob[i];
			}
			if (c != precision) throw new IllegalArgumentException("bad rANS table");
		}

		void start() {
			int bytes = (int) varint();
			buf = p;
			p += bytes;
			int x = (b[buf + bytes - 1] & 0xFF) >> 6;
			off = bytes - x - 1;
			long s = 0;
			for (int k = 0; k <= x; k++) s |= (long) (b[buf + off + k] & 0xFF) << (8 * k);
			state = (s & ((1L << (6 + 8 * x)) - 1)) + lBase;
		}

		int read() {
			while (state < lBase && off > 0) state = state * 256 + (b[buf + --off] & 0xFF);
			int quo = (int) (state / precision), rem = (int) (state % precision), sym = lut[rem];
			state = (long) quo * prob[sym] + rem - cum[sym];
			return sym;
		}
	}
}
