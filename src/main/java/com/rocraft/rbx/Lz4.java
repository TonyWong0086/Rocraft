package com.rocraft.rbx;

/** LZ4 block decompression (the format Roblox uses inside .rbxm chunks). */
final class Lz4 {
	static byte[] decompress(byte[] src, int outLen) {
		if (src.length >= 4 && (src[0] & 0xFF) == 0x28 && (src[1] & 0xFF) == 0xB5 && (src[2] & 0xFF) == 0x2F && (src[3] & 0xFF) == 0xFD)
			throw new IllegalArgumentException("zstd chunk not supported");
		byte[] out = new byte[outLen];
		int s = 0, d = 0;
		while (s < src.length) {
			int token = src[s++] & 0xFF, lit = token >>> 4;
			if (lit == 15) { int b; do { b = src[s++] & 0xFF; lit += b; } while (b == 255); }
			System.arraycopy(src, s, out, d, lit);
			s += lit;
			d += lit;
			if (s >= src.length) break; // last sequence has literals only
			int offset = (src[s] & 0xFF) | (src[s + 1] & 0xFF) << 8;
			s += 2;
			int len = token & 15;
			if (len == 15) { int b; do { b = src[s++] & 0xFF; len += b; } while (b == 255); }
			len += 4;
			for (int i = 0; i < len; i++, d++) out[d] = out[d - offset]; // may overlap
		}
		return out;
	}
}
