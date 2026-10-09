package com.rocraft.rbx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Top mip of a DXT1/DXT3/DXT5 .dds (Roblox's particle textures) as ARGB pixels. */
public final class Dds {
	public final int width, height;
	public final int[] argb;

	private Dds(int w, int h) { width = w; height = h; argb = new int[w * h]; }

	public static Dds read(byte[] data) {
		var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (b.getInt(0) != 0x20534444) throw new IllegalArgumentException("not a DDS");
		int h = b.getInt(12), w = b.getInt(16);
		String fcc = new String(data, 84, 4, java.nio.charset.StandardCharsets.US_ASCII);
		var d = new Dds(w, h);
		int pos = 128, bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				int[] alpha = new int[16];
				java.util.Arrays.fill(alpha, 255);
				switch (fcc) {
					case "DXT1" -> { d.color(b, pos, bx, by, true, null); pos += 8; }
					case "DXT3" -> {
						long bits = b.getLong(pos);
						for (int i = 0; i < 16; i++) alpha[i] = (int) ((bits >>> (4 * i)) & 15) * 17;
						d.color(b, pos + 8, bx, by, false, alpha); pos += 16;
					}
					case "DXT5" -> {
						int a0 = data[pos] & 255, a1 = data[pos + 1] & 255;
						long bits = 0;
						for (int i = 0; i < 6; i++) bits |= (long) (data[pos + 2 + i] & 255) << (8 * i);
						int[] lut = new int[8];
						lut[0] = a0; lut[1] = a1;
						if (a0 > a1) for (int i = 1; i < 7; i++) lut[i + 1] = ((7 - i) * a0 + i * a1) / 7;
						else { for (int i = 1; i < 5; i++) lut[i + 1] = ((5 - i) * a0 + i * a1) / 5; lut[6] = 0; lut[7] = 255; }
						for (int i = 0; i < 16; i++) alpha[i] = lut[(int) ((bits >>> (3 * i)) & 7)];
						d.color(b, pos + 8, bx, by, false, alpha); pos += 16;
					}
					default -> throw new IllegalArgumentException("unsupported DDS " + fcc);
				}
			}
		return d;
	}

	private void color(ByteBuffer b, int p, int bx, int by, boolean dxt1, int[] alpha) {
		int c0 = b.getShort(p) & 0xFFFF, c1 = b.getShort(p + 2) & 0xFFFF, idx = b.getInt(p + 4);
		int[][] pal = new int[4][];
		pal[0] = rgb565(c0); pal[1] = rgb565(c1);
		boolean four = !dxt1 || c0 > c1;
		pal[2] = four ? mix(pal[0], pal[1], 2, 1, 3) : mix(pal[0], pal[1], 1, 1, 2);
		pal[3] = four ? mix(pal[0], pal[1], 1, 2, 3) : new int[]{0, 0, 0, 0};
		for (int i = 0; i < 16; i++) {
			int x = bx * 4 + i % 4, y = by * 4 + i / 4;
			if (x >= width || y >= height) continue;
			int[] c = pal[(idx >>> (2 * i)) & 3];
			int a = c.length == 4 ? 0 : alpha == null ? 255 : alpha[i];
			argb[y * width + x] = a << 24 | c[0] << 16 | c[1] << 8 | c[2];
		}
	}

	private static int[] rgb565(int c) { return new int[]{(c >> 11 & 31) * 255 / 31, (c >> 5 & 63) * 255 / 63, (c & 31) * 255 / 31}; }
	private static int[] mix(int[] a, int[] b, int wa, int wb, int div) {
		return new int[]{(a[0] * wa + b[0] * wb) / div, (a[1] * wa + b[1] * wb) / div, (a[2] * wa + b[2] * wb) / div};
	}
}
