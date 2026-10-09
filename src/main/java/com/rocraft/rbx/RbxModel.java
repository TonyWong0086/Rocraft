package com.rocraft.rbx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Roblox model files (.rbxm binary and .rbxmx XML) as a flat list of instances with the few property types
 * Rocraft needs: strings/content, numbers, Vector3, CFrame. Pure Java; no Minecraft imports.
 */
public final class RbxModel {
	public static final class Inst {
		public final String className;
		public Inst parent;
		public final List<Inst> children = new ArrayList<>();
		public final Map<String, Object> props = new HashMap<>();
		Inst(String c) { className = c; }

		public String name() { return str("Name"); }
		public String str(String k) { Object o = props.get(k); return o instanceof String s ? s : null; }
		public float[] vec3(String k) { Object o = props.get(k); return o instanceof float[] f && f.length == 3 ? f : null; }
		/** 12 floats: x, y, z, then rotation rows R00..R22 */
		public float[] cframe(String k) { Object o = props.get(k); return o instanceof float[] f && f.length == 12 ? f : null; }
		/** Numeric asset id in a content/string property, or -1. */
		public long assetId(String k) { return RbxModel.assetId(str(k)); }

		public Inst child(String cls) { for (Inst c : children) if (c.className.equals(cls)) return c; return null; }
		public Inst descendant(String cls) {
			for (Inst c : children) { if (c.className.equals(cls)) return c; Inst d = c.descendant(cls); if (d != null) return d; }
			return null;
		}
		@Override public String toString() { return className + "(" + name() + ")"; }
	}

	public final List<Inst> all = new ArrayList<>();
	public final List<Inst> roots = new ArrayList<>();

	public Inst first(String cls) { for (Inst i : all) if (i.className.equals(cls)) return i; return null; }

	private static final Pattern ID = Pattern.compile("(?:[?&]id=|rbxassetid://|^)(\\d{3,})");
	public static long assetId(String s) {
		if (s == null) return -1;
		Matcher m = ID.matcher(s.trim());
		return m.find() ? Long.parseLong(m.group(1)) : -1;
	}

	public static RbxModel read(byte[] data) {
		String head = new String(data, 0, Math.min(8, data.length), StandardCharsets.US_ASCII);
		if (head.equals("<roblox!")) return binary(data);
		if (head.startsWith("<roblox")) return xml(new String(data, StandardCharsets.UTF_8));
		throw new IllegalArgumentException("not a Roblox model");
	}

	// ---------------------------------------------------------------- XML (.rbxmx)

	private static final Pattern TAG = Pattern.compile("<Item class=\"([^\"]+)\"[^>]*>|</Item>|<(string|Content|ProtectedString|float|double|int|bool|token|Vector3|CoordinateFrame|Color3uint8) name=\"([^\"]+)\"(?:\\s*/>|>(.*?)</\\2>)", Pattern.DOTALL);
	private static final Pattern XYZ = Pattern.compile("<(X|Y|Z|R\\d\\d)>([^<]*)</");

	static RbxModel xml(String s) {
		RbxModel m = new RbxModel();
		Deque<Inst> stack = new ArrayDeque<>();
		Matcher t = TAG.matcher(s);
		while (t.find()) {
			if (t.group(1) != null) {
				Inst i = new Inst(t.group(1));
				if (stack.isEmpty()) m.roots.add(i); else { i.parent = stack.peek(); stack.peek().children.add(i); }
				m.all.add(i);
				stack.push(i);
			} else if (t.group(0).equals("</Item>")) {
				if (!stack.isEmpty()) stack.pop();
			} else if (!stack.isEmpty()) {
				String type = t.group(2), name = t.group(3), body = t.group(4) == null ? "" : t.group(4);
				Object v = switch (type) {
					case "Vector3", "CoordinateFrame" -> {
						Map<String, Float> c = new HashMap<>();
						Matcher x = XYZ.matcher(body);
						while (x.find()) c.put(x.group(1), Float.parseFloat(x.group(2).trim()));
						if (type.equals("Vector3")) yield new float[]{c.getOrDefault("X", 0f), c.getOrDefault("Y", 0f), c.getOrDefault("Z", 0f)};
						yield new float[]{c.getOrDefault("X", 0f), c.getOrDefault("Y", 0f), c.getOrDefault("Z", 0f),
							c.getOrDefault("R00", 1f), c.getOrDefault("R01", 0f), c.getOrDefault("R02", 0f),
							c.getOrDefault("R10", 0f), c.getOrDefault("R11", 1f), c.getOrDefault("R12", 0f),
							c.getOrDefault("R20", 0f), c.getOrDefault("R21", 0f), c.getOrDefault("R22", 1f)};
					}
					case "Content" -> unescape(body.replaceAll("<[^>]+>", "").trim());
					default -> unescape(body.replace("<![CDATA[", "").replace("]]>", "").trim());
				};
				stack.peek().props.put(name, v);
			}
		}
		return m;
	}

	static String unescape(String s) {
		if (s.indexOf('&') < 0) return s;
		var m = Pattern.compile("&#(\\d+);").matcher(s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'"));
		var sb = new StringBuilder();
		while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
		return m.appendTail(sb).toString().replace("&amp;", "&");
	}

	// ---------------------------------------------------------------- binary (.rbxm)

	static RbxModel binary(byte[] data) {
		var in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		in.position(32);
		RbxModel m = new RbxModel();
		Map<Integer, List<Inst>> byClass = new HashMap<>();
		Map<Integer, Inst> byRef = new HashMap<>();
		List<String> shared = new ArrayList<>();
		while (in.remaining() >= 16) {
			byte[] nameB = new byte[4];
			in.get(nameB);
			String name = new String(nameB, StandardCharsets.US_ASCII).trim().replace("\0", "");
			int clen = in.getInt(), ulen = in.getInt();
			in.getInt();
			byte[] raw = new byte[clen == 0 ? ulen : clen];
			in.get(raw);
			byte[] chunk = clen == 0 ? raw : Lz4.decompress(raw, ulen);
			var c = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
			switch (name) {
				case "INST" -> {
					int classId = c.getInt();
					String cls = string(c);
					c.get(); // service flag
					int n = c.getInt();
					int[] refs = refs(c, n);
					List<Inst> list = new ArrayList<>();
					for (int r : refs) { Inst i = new Inst(cls); list.add(i); byRef.put(r, i); m.all.add(i); }
					byClass.put(classId, list);
				}
				case "SSTR" -> {
					c.getInt();
					int n = c.getInt();
					for (int i = 0; i < n; i++) { c.position(c.position() + 16); shared.add(string(c)); }
				}
				case "PROP" -> prop(c, byClass.get(c.getInt()), shared);
				case "PRNT" -> {
					c.get();
					int n = c.getInt();
					int[] kids = refs(c, n), parents = refs(c, n);
					for (int i = 0; i < n; i++) {
						Inst k = byRef.get(kids[i]), p = byRef.get(parents[i]);
						if (k == null) continue;
						if (p == null) m.roots.add(k); else { k.parent = p; p.children.add(k); }
					}
				}
				case "END" -> { return m; }
				default -> {}
			}
		}
		return m;
	}

	private static void prop(ByteBuffer c, List<Inst> insts, List<String> shared) {
		if (insts == null) return;
		String name = string(c);
		int type = c.get() & 0xFF, n = insts.size();
		switch (type) {
			case 0x01 -> { for (Inst i : insts) i.props.put(name, string(c)); }
			case 0x02 -> { for (Inst i : insts) i.props.put(name, c.get() != 0); }
			case 0x04 -> { float[] f = floats(c, n); for (int k = 0; k < n; k++) insts.get(k).props.put(name, f[k]); }
			case 0x0E -> {
				float[] x = floats(c, n), y = floats(c, n), z = floats(c, n);
				for (int k = 0; k < n; k++) insts.get(k).props.put(name, new float[]{x[k], y[k], z[k]});
			}
			case 0x10 -> {
				float[][] rot = new float[n][];
				for (int k = 0; k < n; k++) {
					int id = c.get() & 0xFF;
					if (id == 0) { rot[k] = new float[9]; for (int j = 0; j < 9; j++) rot[k][j] = c.getFloat(); }
					else rot[k] = specialRotation(id);
				}
				float[] x = floats(c, n), y = floats(c, n), z = floats(c, n);
				for (int k = 0; k < n; k++) {
					float[] cf = new float[12];
					cf[0] = x[k]; cf[1] = y[k]; cf[2] = z[k];
					System.arraycopy(rot[k], 0, cf, 3, 9);
					insts.get(k).props.put(name, cf);
				}
			}
			case 0x1D -> { int[] idx = ints(c, n); for (int k = 0; k < n; k++) if (idx[k] < shared.size()) insts.get(k).props.put(name, shared.get(idx[k])); }
			default -> {} // types Rocraft doesn't use
		}
	}

	/** Axis-aligned rotation ids: id-1 = 6*row0 + row1 over [+X,+Y,+Z,-X,-Y,-Z]; row2 = row0 x row1. */
	static float[] specialRotation(int id) {
		int n = id - 1;
		float[] r0 = axis(n / 6), r1 = axis(n % 6);
		float[] r2 = {r0[1] * r1[2] - r0[2] * r1[1], r0[2] * r1[0] - r0[0] * r1[2], r0[0] * r1[1] - r0[1] * r1[0]};
		return new float[]{r0[0], r0[1], r0[2], r1[0], r1[1], r1[2], r2[0], r2[1], r2[2]};
	}

	private static float[] axis(int i) {
		float s = i < 3 ? 1 : -1;
		float[] a = new float[3];
		a[i % 3] = s;
		return a;
	}

	private static String string(ByteBuffer c) {
		byte[] b = new byte[c.getInt()];
		c.get(b);
		return new String(b, StandardCharsets.UTF_8);
	}

	/** Byte-interleaved big-endian int32s. */
	private static int[] raw(ByteBuffer c, int n) {
		byte[] b = new byte[n * 4];
		c.get(b);
		int[] out = new int[n];
		for (int i = 0; i < n; i++)
			out[i] = (b[i] & 0xFF) << 24 | (b[n + i] & 0xFF) << 16 | (b[2 * n + i] & 0xFF) << 8 | (b[3 * n + i] & 0xFF);
		return out;
	}

	private static int[] ints(ByteBuffer c, int n) {
		int[] v = raw(c, n);
		for (int i = 0; i < n; i++) v[i] = (v[i] >>> 1) ^ -(v[i] & 1);
		return v;
	}

	private static int[] refs(ByteBuffer c, int n) {
		int[] v = ints(c, n);
		for (int i = 1; i < n; i++) v[i] += v[i - 1];
		return v;
	}

	private static float[] floats(ByteBuffer c, int n) {
		int[] v = raw(c, n);
		float[] f = new float[n];
		for (int i = 0; i < n; i++) f[i] = Float.intBitsToFloat(Integer.rotateRight(v[i], 1));
		return f;
	}
}
