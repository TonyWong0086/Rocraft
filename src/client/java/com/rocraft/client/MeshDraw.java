package com.rocraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rocraft.Rocraft;
import com.rocraft.rbx.RbxMesh;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A Roblox mesh ready to draw: triangles expanded per corner, in Roblox studs (part space), plus its texture. */
final class MeshDraw {
	final float[] pos, nrm, uv;
	final int count; // corners (3 per triangle)
	BufferedImage image; // until uploaded
	private Identifier tex;
	private static int seq;

	private MeshDraw(int corners, BufferedImage image) {
		count = corners;
		pos = new float[corners * 3];
		nrm = new float[corners * 3];
		uv = new float[corners * 2];
		this.image = image;
	}

	/** Mesh with its own UVs, vertices moved by m (Roblox CFrame * Offset * Scale). */
	static MeshDraw of(RbxMesh mesh, Matrix4f m, BufferedImage image) {
		var d = new MeshDraw(mesh.tris.length, image);
		var v = new Vector3f();
		for (int c = 0; c < mesh.tris.length; c++) {
			int i = mesh.tris[c];
			m.transformPosition(v.set(mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2]));
			d.pos[c * 3] = v.x; d.pos[c * 3 + 1] = v.y; d.pos[c * 3 + 2] = v.z;
			m.transformDirection(v.set(mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2])).normalize();
			d.nrm[c * 3] = v.x; d.nrm[c * 3 + 1] = v.y; d.nrm[c * 3 + 2] = v.z;
			d.uv[c * 2] = mesh.uv[i * 2];
			d.uv[c * 2 + 1] = mesh.uv[i * 2 + 1];
		}
		return d;
	}

	/**
	 * Body-part mesh textured like a Roblox R6 limb: each triangle takes the clothing face it points at
	 * (box[] = {u, v, w, h, d} of the folded avatar texture, 64x64 units, same unfold as AvatarSkin).
	 */
	static MeshDraw planar(RbxMesh mesh, int[] box) {
		var d = new MeshDraw(mesh.tris.length, null);
		float[] b = mesh.bounds();
		float hx = (b[3] - b[0]) / 2, hy = (b[4] - b[1]) / 2, hz = (b[5] - b[2]) / 2;
		int u = box[0], v = box[1], w = box[2], h = box[3], dp = box[4];
		for (int t = 0; t < mesh.tris.length; t += 3) {
			int a = mesh.tris[t], bb = mesh.tris[t + 1], c = mesh.tris[t + 2];
			// triangle normal (Roblox space) picks the face
			float ex = mesh.pos[bb * 3] - mesh.pos[a * 3], ey = mesh.pos[bb * 3 + 1] - mesh.pos[a * 3 + 1], ez = mesh.pos[bb * 3 + 2] - mesh.pos[a * 3 + 2];
			float fx = mesh.pos[c * 3] - mesh.pos[a * 3], fy = mesh.pos[c * 3 + 1] - mesh.pos[a * 3 + 1], fz = mesh.pos[c * 3 + 2] - mesh.pos[a * 3 + 2];
			float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
			float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
			// sign check against the vertex normal so winding order doesn't matter
			float vn = mesh.nrm[a * 3] * nx + mesh.nrm[a * 3 + 1] * ny + mesh.nrm[a * 3 + 2] * nz;
			if (vn < 0) { nx = -nx; ny = -ny; nz = -nz; }
			for (int k = 0; k < 3; k++) {
				int i = mesh.tris[t + k], o = t + k;
				float x = mesh.pos[i * 3], y = mesh.pos[i * 3 + 1], z = mesh.pos[i * 3 + 2];
				float s, tt;
				int[] r; // face rect {x, y, w, h} in units
				if (ax >= ay && ax >= az) {
					if (nx > 0) { r = new int[]{u, v + dp, dp, h}; s = (hz - z) / (2 * hz); }           // character's right
					else { r = new int[]{u + dp + w, v + dp, dp, h}; s = (z + hz) / (2 * hz); }        // left
					tt = (hy - y) / (2 * hy);
				} else if (az >= ay) {
					if (nz < 0) { r = new int[]{u + dp, v + dp, w, h}; s = (hx - x) / (2 * hx); }       // front (-Z)
					else { r = new int[]{u + 2 * dp + w, v + dp, w, h}; s = (x + hx) / (2 * hx); }     // back
					tt = (hy - y) / (2 * hy);
				} else {
					s = (hx - x) / (2 * hx);
					if (ny > 0) { r = new int[]{u + dp, v, w, dp}; tt = (hz - z) / (2 * hz); }          // top
					else { r = new int[]{u + dp + w, v, w, dp}; tt = (z + hz) / (2 * hz); }            // bottom
				}
				s = Math.clamp(s, 0.002f, 0.998f);
				tt = Math.clamp(tt, 0.002f, 0.998f);
				d.pos[o * 3] = x; d.pos[o * 3 + 1] = y; d.pos[o * 3 + 2] = z;
				d.nrm[o * 3] = mesh.nrm[i * 3]; d.nrm[o * 3 + 1] = mesh.nrm[i * 3 + 1]; d.nrm[o * 3 + 2] = mesh.nrm[i * 3 + 2];
				d.uv[o * 2] = (r[0] + s * r[2]) / 64f;
				d.uv[o * 2 + 1] = (r[1] + tt * r[3]) / 64f;
			}
		}
		return d;
	}

	/** A Roblox Ball part (Shape = Ball) of the given diameter in studs; white, tinted when drawn. */
	static MeshDraw ball(float size) {
		int seg = 16, ring = 12;
		var d = new MeshDraw(seg * ring * 6, null);
		int c = 0;
		for (int r = 0; r < ring; r++) for (int s = 0; s < seg; s++) {
			float[][] q = {pt(r, s, ring, seg), pt(r + 1, s, ring, seg), pt(r + 1, s + 1, ring, seg), pt(r, s + 1, ring, seg)};
			for (int k : new int[]{0, 1, 2, 0, 2, 3}) {
				for (int j = 0; j < 3; j++) { d.pos[c * 3 + j] = q[k][j] * size / 2; d.nrm[c * 3 + j] = q[k][j]; }
				d.uv[c * 2] = 0.5f;
				d.uv[c * 2 + 1] = 0.5f;
				c++;
			}
		}
		return d;
	}

	private static float[] pt(int r, int s, int ring, int seg) {
		double th = Math.PI * r / ring, ph = 2 * Math.PI * s / seg;
		return new float[]{(float) (Math.sin(th) * Math.cos(ph)), (float) Math.cos(th), (float) (Math.sin(th) * Math.sin(ph))};
	}

	/** Copy of src with vertices moved by m and a new texture. */
	static MeshDraw transformed(MeshDraw src, Matrix4f m, BufferedImage image) {
		var d = new MeshDraw(src.count, image);
		var v = new Vector3f();
		for (int c = 0; c < src.count; c++) {
			m.transformPosition(v.set(src.pos[c * 3], src.pos[c * 3 + 1], src.pos[c * 3 + 2]));
			d.pos[c * 3] = v.x; d.pos[c * 3 + 1] = v.y; d.pos[c * 3 + 2] = v.z;
			m.transformDirection(v.set(src.nrm[c * 3], src.nrm[c * 3 + 1], src.nrm[c * 3 + 2])).normalize();
			d.nrm[c * 3] = v.x; d.nrm[c * 3 + 1] = v.y; d.nrm[c * 3 + 2] = v.z;
		}
		System.arraycopy(src.uv, 0, d.uv, 0, src.uv.length);
		return d;
	}

	/** Submit as entity geometry (triangles as quads with the last corner repeated). */
	static void submit(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, int light, MeshDraw d, Identifier tex) {
		submit(ps, out, light, d, tex, -1);
	}

	static void submit(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector out, int light, MeshDraw d, Identifier tex, int argb) {
		out.submitCustomGeometry(ps, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(tex), (pose, vc) -> {
			float[] p = d.pos, n = d.nrm, uv = d.uv;
			for (int t = 0; t < d.count; t += 3)
				for (int k = 0; k < 4; k++) {
					int i = t + Math.min(k, 2);
					vc.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(argb).setUv(uv[i * 2], uv[i * 2 + 1])
						.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[i * 3], n[i * 3 + 1], n[i * 3 + 2]);
				}
		});
	}

	/** Own texture, uploaded on first use (render thread). White if the asset had none. */
	Identifier texture() {
		if (tex == null) {
			BufferedImage img = image != null ? image : new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) {{ setRGB(0, 0, -1); }};
			var ni = new NativeImage(img.getWidth(), img.getHeight(), true);
			for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
			tex = Rocraft.id("mesh/" + (seq++));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "rocraft mesh", ni));
			image = null;
		}
		return tex;
	}
}
