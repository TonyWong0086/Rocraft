package com.rocraft.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.rocraft.RbxParticles;
import com.rocraft.Rocraft;
import com.rocraft.rbx.Dds;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Roblox's built-in effects (Fire, Smoke, Explosion) the way the Roblox client draws them: each one is a set of
 * billboards using the install's textures/particles/*_main texture, coloured and faded over its life by the matching
 * *_color / *_alpha ramp (x = age, y = a per-particle random row), fire and explosion glow blended additively
 * (LightEmission 1) and unlit, smoke alpha-blended. Sizes and speeds are in studs like the Instance properties.
 */
final class RbxParticle extends SingleQuadParticle {
	static final float STUD = 0.28f;
	private static final Map<SimpleParticleType, SpriteSet> SPRITES = new HashMap<>();
	private static final Map<String, int[][]> RAMPS = new HashMap<>();
	private static Layer additive, soft;

	float size0, size1, opacity = 1, spin, drag = 1;
	int tint = 0xFFFFFF;
	String colorRamp, alphaRamp;
	boolean add, bright, rampIsAlpha; // rampIsAlpha: the colour ramp's brightness is the alpha (Smoke)
	net.minecraft.world.entity.Entity follow; // moves with this entity (ForceField), offset fixed at spawn
	private double ox, oy, oz;
	private final float row;

	private RbxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SimpleParticleType type, int life) {
		super(level, x, y, z, vx, vy, vz, SPRITES.get(type).get(level.getRandom()));
		xd = vx;
		yd = vy;
		zd = vz;
		lifetime = Math.max(1, life);
		gravity = 0;
		friction = 1;
		hasPhysics = false;
		row = random.nextFloat();
		roll = oRoll = random.nextFloat() * 6.283f;
	}

	/** New particle; velocity in studs/s. Call add() once its style fields are set. */
	static RbxParticle make(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SimpleParticleType type, float lifeSeconds) {
		if (!SPRITES.containsKey(type)) return null;
		float k = STUD / 20;
		return new RbxParticle(l, x, y, z, vx * k, vy * k, vz * k, type, Math.round(lifeSeconds * 20));
	}

	RbxParticle sizes(float studs0, float studs1) { size0 = studs0 * STUD; size1 = studs1 * STUD; return this; }

	void add() {
		if (follow != null) { ox = x - follow.getX(); oy = y - follow.getY(); oz = z - follow.getZ(); }
		update();
		Minecraft.getInstance().particleEngine.add(this);
	}

	@Override public void tick() {
		super.tick();
		if (follow != null) setPos(follow.getX() + ox, follow.getY() + oy, follow.getZ() + oz);
		xd *= drag; yd *= drag; zd *= drag;
		oRoll = roll;
		roll += spin;
		update();
	}

	private void update() {
		float t = Math.min(1, age / (float) lifetime);
		quadSize = (size0 + (size1 - size0) * t) / 2; // quadSize is half the side
		int c = colorRamp == null ? 0xFFFFFFFF : ramp(colorRamp, t, row);
		float a = opacity;
		if (rampIsAlpha) { a *= (c >> 16 & 255) / 255f; c = 0xFFFFFFFF; }
		if (alphaRamp != null) a *= (ramp(alphaRamp, t, row) >> 16 & 255) / 255f; // alpha ramps are grey
		else if (colorRamp == null) a *= 1 - t;
		rCol = (tint >> 16 & 255) / 255f * (c >> 16 & 255) / 255f;
		gCol = (tint >> 8 & 255) / 255f * (c >> 8 & 255) / 255f;
		bCol = (tint & 255) / 255f * (c & 255) / 255f;
		alpha = Math.min(1, a);
	}

	@Override protected int getLightCoords(float partial) { return bright ? 0xF000F0 : super.getLightCoords(partial); }

	@Override protected Layer getLayer() {
		if (additive == null) {
			var atlas = Layer.TRANSLUCENT.textureAtlasLocation();
			var noDepthWrite = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false); // 26.x depth is reversed (DepthStencilState.DEFAULT)
			additive = new Layer(true, atlas, RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET).withLocation(Rocraft.id("pipeline/additive_particle"))
				.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).withDepthStencilState(noDepthWrite).build());
			soft = new Layer(true, atlas, RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET).withLocation(Rocraft.id("pipeline/soft_particle"))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(noDepthWrite).build());
		}
		return add ? additive : soft;
	}

	/** ARGB of a Roblox ramp at age t (x) and row r (y), 0..1; white if the install doesn't have it. */
	static int ramp(String name, float t, float r) {
		int[][] img = RAMPS.computeIfAbsent(name, n -> {
			try {
				var dds = RobloxAssets.file("textures/particles/" + n + ".dds");
				if (dds != null) {
					var d = Dds.read(Files.readAllBytes(dds));
					int[][] out = new int[d.height][d.width];
					for (int y = 0; y < d.height; y++) System.arraycopy(d.argb, y * d.width, out[y], 0, d.width);
					return out;
				}
				var png = javax.imageio.ImageIO.read(RobloxAssets.file("textures/particles/" + n + ".png").toFile());
				int[][] out = new int[png.getHeight()][png.getWidth()];
				for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) out[y][x] = png.getRGB(x, y);
				return out;
			} catch (Exception e) { return new int[][]{{0xFFFFFFFF}}; }
		});
		int[] line = img[Math.min(img.length - 1, (int) (r * img.length))];
		return line[Math.min(line.length - 1, (int) (t * (line.length - 1)))];
	}

	// ---- the Roblox effects -------------------------------------------------------------------------------------

	static double rnd(ClientLevel l) { return l.getRandom().nextDouble() * 2 - 1; }

	/** One tick of a Fire instance (Size, Heat, Color) at a point: rising flames plus a few sparks. */
	static void fire(ClientLevel l, double x, double y, double z, float size, float heat, int color) {
		for (int i = 0; i < 2; i++) {
			double r = size * 0.15 * STUD;
			var p = make(l, x + rnd(l) * r, y + rnd(l) * r * 0.5, z + rnd(l) * r, rnd(l) * 0.6, heat * (0.55 + 0.25 * l.getRandom().nextFloat()), rnd(l) * 0.6,
				RbxParticles.FIRE, 1.2f + 0.5f * l.getRandom().nextFloat());
			if (p == null) return;
			p.sizes(size * 0.85f, size * 0.3f);
			p.tint = color; p.colorRamp = "fire_color"; p.alphaRamp = "fire_alpha"; p.add = true; p.bright = true;
			p.spin = (float) rnd(l) * 0.05f;
			p.add();
		}
		if (l.getRandom().nextFloat() < 0.25f) {
			var s = make(l, x, y, z, rnd(l) * 1.5, heat * (0.9 + 0.4 * l.getRandom().nextFloat()), rnd(l) * 1.5, RbxParticles.SPARK, 1.2f);
			if (s == null) return;
			s.sizes(size * 0.12f, size * 0.06f);
			s.tint = color; s.colorRamp = "fire_sparks_color"; s.add = true; s.bright = true;
			s.add();
		}
	}

	/** One puff of a Smoke instance (Size, Opacity, RiseVelocity, Color). */
	static void smoke(ClientLevel l, double x, double y, double z, float size, float opacity, float rise, int color) {
		var p = make(l, x, y, z, rnd(l) * 0.4, rise, rnd(l) * 0.4, RbxParticles.SMOKE, 2.5f + l.getRandom().nextFloat());
		if (p == null) return;
		p.sizes(size * 0.3f, size);
		p.tint = color; p.opacity = opacity; p.colorRamp = "smoke_color"; p.rampIsAlpha = true;
		p.spin = (float) rnd(l) * 0.02f;
		p.add();
	}

	/** Explosion with BlastRadius r (studs): flash, shockwave ring, fireball core, flying sparks and lingering smoke. */
	static void explosion(ClientLevel l, double x, double y, double z, float r) {
		var im = make(l, x, y, z, 0, 0, 0, RbxParticles.IMPLOSION, 0.4f);
		if (im == null) return;
		im.sizes(r * 2.2f, r * 0.4f); im.tint = 0xFFE6B4; im.colorRamp = "explosion01_implosion_color"; im.add = true; im.bright = true;
		im.add();
		var sw = make(l, x, y, z, 0, 0, 0, RbxParticles.SHOCKWAVE, 0.6f);
		sw.sizes(r * 0.5f, r * 3.5f); sw.add = true; sw.bright = true;
		sw.add();
		for (int i = 0; i < 5; i++) {
			var c = make(l, x + rnd(l) * r * 0.2 * STUD, y + rnd(l) * r * 0.2 * STUD, z + rnd(l) * r * 0.2 * STUD,
				rnd(l) * r * 0.3, rnd(l) * r * 0.3 + r * 0.2, rnd(l) * r * 0.3, RbxParticles.EXPLOSION, 1.0f + 0.4f * l.getRandom().nextFloat());
			c.sizes(r * 0.7f, r * 1.7f); c.colorRamp = "explosion_color"; c.alphaRamp = "explosion_alpha"; c.add = true; c.bright = true;
			c.spin = (float) rnd(l) * 0.04f; c.drag = 0.9f;
			c.add();
		}
		for (int i = 0; i < 10; i++) {
			double dx = rnd(l), dy = Math.abs(rnd(l)), dz = rnd(l);
			var s = make(l, x + dx * r * 0.4 * STUD, y + dy * r * 0.3 * STUD, z + dz * r * 0.4 * STUD, dx * r * 0.6, dy * r * 0.4 + 2, dz * r * 0.6,
				RbxParticles.EXPLOSION_SMOKE, 3.0f + 1.5f * l.getRandom().nextFloat());
			s.sizes(r * 0.8f, r * 1.9f); s.colorRamp = "explosion01_smoke_color_new"; s.alphaRamp = "explosion01_smoke_alpha"; s.opacity = 2.5f;
			s.spin = (float) rnd(l) * 0.02f; s.drag = 0.92f;
			s.add();
		}
		for (int i = 0; i < 24; i++) {
			double dx = rnd(l), dy = rnd(l) * 0.5 + 0.5, dz = rnd(l), n = Math.sqrt(dx * dx + dy * dy + dz * dz), v = r * (2 + 2 * l.getRandom().nextFloat());
			var s = make(l, x, y, z, dx / n * v, dy / n * v, dz / n * v, RbxParticles.SPARK, 0.9f + 0.5f * l.getRandom().nextFloat());
			s.sizes(0.5f, 0.2f); s.colorRamp = "explosion_color"; s.add = true; s.bright = true; s.drag = 0.94f;
			s.gravity = 0.4f;
			s.add();
		}
	}

	/** ForceField sparkles: little white specks drifting up out of the bubble. */
	static void forceFieldSparkles(ClientLevel l, net.minecraft.world.entity.Entity e) {
		if (l.getRandom().nextFloat() > 0.45f) return;
		double a = l.getRandom().nextDouble() * Math.PI * 2, r = (0.5 + l.getRandom().nextDouble() * 2) * STUD;
		var s = make(l, e.getX() + Math.cos(a) * r, e.getY() + (2.5 + l.getRandom().nextDouble() * 3) * STUD, e.getZ() + Math.sin(a) * r,
			0, 2.5 + l.getRandom().nextDouble() * 1.5, 0, RbxParticles.SPARK, 1.2f + 0.6f * l.getRandom().nextFloat());
		if (s == null) return;
		s.sizes(0.3f, 0.1f); s.tint = 0xFFFFFF; s.add = true; s.bright = true;
		s.add();
	}

	static void register() {
		var reg = ParticleProviderRegistry.getInstance();
		for (var t : new SimpleParticleType[]{RbxParticles.SMOKE, RbxParticles.FIRE, RbxParticles.SPARK, RbxParticles.EXPLOSION_SMOKE, RbxParticles.SHOCKWAVE, RbxParticles.IMPLOSION,
				RbxParticles.FORCEFIELD_GLOW, RbxParticles.FORCEFIELD_VORTEX})
			reg.register(t, sprites -> { SPRITES.put(t, sprites); return (opt, level, x, y, z, vx, vy, vz, rand) -> null; });
		// the server sends one rbx_explosion with the BlastRadius (studs) as its x speed; the whole effect is built here
		reg.register(RbxParticles.EXPLOSION, sprites -> {
			SPRITES.put(RbxParticles.EXPLOSION, sprites);
			return (opt, level, x, y, z, vx, vy, vz, rand) -> { explosion(level, x, y, z, (float) Math.max(2, vx)); return null; };
		});
	}

	/** Particle textures for the private pack: Roblox *_main.dds -> PNG. */
	static final Map<String, String> TEXTURES = Map.of("rbx_smoke", "smoke_main", "rbx_fire", "fire_main", "rbx_spark", "fire_sparks_main",
		"rbx_explosion", "explosion01_core_main", "rbx_explosion_smoke", "explosion01_smoke_main",
		"rbx_shockwave", "explosion01_shockwave_main", "rbx_implosion", "explosion01_implosion_main",
		"rbx_forcefield_glow", "forcefield_glow_main", "rbx_forcefield_vortex", "forcefield_vortex_main");

	static void writeTextures(java.nio.file.Path assets) {
		for (var e : TEXTURES.entrySet()) try {
			var out = assets.resolve("textures/particle/" + e.getKey() + ".png");
			if (Files.exists(out)) continue;
			var d = Dds.read(Files.readAllBytes(RobloxAssets.file("textures/particles/" + e.getValue() + ".dds")));
			var img = new java.awt.image.BufferedImage(d.width, d.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			img.setRGB(0, 0, d.width, d.height, d.argb, 0, d.width);
			Files.createDirectories(out.getParent());
			javax.imageio.ImageIO.write(img, "png", out.toFile());
		} catch (Exception ex) { Rocraft.LOGGER.warn("Roblox particle {} unavailable: {}", e.getKey(), ex.toString()); }
	}
}
