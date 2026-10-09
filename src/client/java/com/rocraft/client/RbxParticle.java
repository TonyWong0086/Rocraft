package com.rocraft.client;

import com.rocraft.RbxParticles;
import com.rocraft.Rocraft;
import com.rocraft.rbx.Dds;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Roblox particles: the effect's main texture, coloured and faded over its life by Roblox's own ramp textures
 * (textures/particles/*_color.dds, *_alpha.dds from the user's install), like Roblox's particle shader.
 */
final class RbxParticle extends SingleQuadParticle {
	/** size in blocks at birth/death, base tint, opacity, ramps, lifetime in ticks, buoyancy (blocks/tick^2, + = rises). */
	record Style(float size0, float size1, int tint, float opacity, String colorRamp, String alphaRamp, int life, float rise) {}
	static final Style SMOKE = new Style(0.14f, 0.4f, 0xFFFFFF, 0.25f, "smoke_color", null, 40, 0.0015f);   // Smoke: Size .5, Opacity .25, RiseVelocity 2
	static final Style FIRE = new Style(0.45f, 0.12f, 0xEC8B46, 1f, "fire_color", "fire_alpha", 16, 0.004f); // Fire: Color (236,139,70), Heat 5
	static final Style SPARK = new Style(0.1f, 0.05f, 0xFFFFFF, 1f, "fire_sparks_color", null, 12, -0.01f);
	static final Style EXPLOSION = new Style(0.6f, 1.6f, 0xFFFFFF, 1f, null, "explosion_alpha", 18, 0.001f);
	static final Style EXPLOSION_SMOKE = new Style(0.7f, 1.4f, 0xFFFFFF, 0.85f, null, "explosion01_smoke_alpha", 45, 0.002f);

	private static final Map<String, int[]> RAMPS = new HashMap<>();
	private final Style style;

	RbxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TextureAtlasSprite sprite, Style style) {
		super(level, x, y, z, vx, vy, vz, sprite);
		this.style = style;
		xd = vx;
		yd = vy;
		zd = vz;
		lifetime = style.life() + random.nextInt(style.life() / 3 + 1);
		gravity = -style.rise() * 20;
		friction = 0.96f;
		hasPhysics = false;
		roll = oRoll = random.nextFloat() * 6.283f;
		update();
	}

	@Override public void tick() {
		super.tick();
		oRoll = roll;
		roll += 0.02f;
		update();
	}

	private void update() {
		float t = Math.min(1, age / (float) lifetime);
		quadSize = style.size0() + (style.size1() - style.size0()) * t;
		int c = style.colorRamp() == null ? 0xFFFFFFFF : ramp(style.colorRamp(), t);
		float a = (c >>> 24) / 255f * style.opacity();
		if (style.alphaRamp() != null) a *= (ramp(style.alphaRamp(), t) & 0xFF) / 255f; // alpha ramps are grey
		else if (style.colorRamp() == null) a *= 1 - t;
		int tint = style.tint();
		rCol = (tint >> 16 & 255) / 255f * (c >> 16 & 255) / 255f;
		gCol = (tint >> 8 & 255) / 255f * (c >> 8 & 255) / 255f;
		bCol = (tint & 255) / 255f * (c & 255) / 255f;
		alpha = a;
	}

	@Override protected Layer getLayer() { return Layer.TRANSLUCENT; }

	/** ARGB at 0..1 along a 128x32 Roblox ramp (middle row); white if the install doesn't have it. */
	static int ramp(String name, float t) {
		int[] r = RAMPS.computeIfAbsent(name, n -> {
			try {
				var d = Dds.read(Files.readAllBytes(RobloxAssets.file("textures/particles/" + n + ".dds")));
				int[] row = new int[d.width];
				System.arraycopy(d.argb, (d.height / 2) * d.width, row, 0, d.width);
				return row;
			} catch (Exception e) { return new int[]{0xFFFFFFFF}; }
		});
		return r[Math.min(r.length - 1, (int) (t * (r.length - 1)))];
	}

	static void register() {
		reg(RbxParticles.SMOKE, SMOKE);
		reg(RbxParticles.FIRE, FIRE);
		reg(RbxParticles.SPARK, SPARK);
		reg(RbxParticles.EXPLOSION, EXPLOSION);
		reg(RbxParticles.EXPLOSION_SMOKE, EXPLOSION_SMOKE);
	}

	private static void reg(SimpleParticleType type, Style style) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (opt, level, x, y, z, vx, vy, vz, rand) ->
			new RbxParticle(level, x, y, z, vx, vy, vz, sprites.get(rand), style));
	}

	/** Particle textures for the private pack: Roblox *_main.dds -> PNG. */
	static final Map<String, String> TEXTURES = Map.of("rbx_smoke", "smoke_main", "rbx_fire", "fire_main", "rbx_spark", "fire_sparks_main",
		"rbx_explosion", "explosion01_core_main", "rbx_explosion_smoke", "explosion01_smoke_main");

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
