package com.rocraft.client;

import com.rocraft.Rocraft;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Plays sound files straight from the user's Roblox install through Minecraft's sound engine (3D, volume sliders):
 * .mp3 decoded with JLayer, .ogg with Minecraft's own decoder, both kept as mono 16-bit PCM so OpenAL positions them.
 */
final class RbxAudio {
	record Pcm(byte[] data, int rate) {}
	private static final Map<String, Optional<Pcm>> CACHE = new HashMap<>();
	private static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(FabricSoundInstance.EMPTY_SOUND);

	/** PCM of content/<rel>, or null if the install lacks it. Cached; first call decodes (small files). */
	static Pcm pcm(String rel) {
		return CACHE.computeIfAbsent(rel, r -> {
			var f = RobloxAssets.file(r);
			if (f == null) return Optional.empty();
			try {
				byte[] bytes = Files.readAllBytes(f);
				return Optional.of(r.endsWith(".mp3") ? mp3(bytes) : ogg(bytes));
			} catch (Exception e) {
				Rocraft.LOGGER.warn("Roblox sound {} unreadable: {}", r, e.toString());
				return Optional.empty();
			}
		}).orElse(null);
	}

	private static Pcm mp3(byte[] bytes) throws Exception {
		var bs = new javazoom.jl.decoder.Bitstream(new ByteArrayInputStream(bytes));
		var dec = new javazoom.jl.decoder.Decoder();
		var out = new ByteArrayOutputStream();
		int rate = 44100;
		for (javazoom.jl.decoder.Header h; (h = bs.readFrame()) != null; bs.closeFrame()) {
			var sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs);
			short[] b = sb.getBuffer();
			int ch = Math.max(1, dec.getOutputChannels());
			rate = dec.getOutputFrequency();
			for (int i = 0; i + ch - 1 < sb.getBufferLength(); i += ch) {
				int sum = 0;
				for (int c = 0; c < ch; c++) sum += b[i + c];
				short m = (short) (sum / ch);
				out.write(m & 255);
				out.write(m >> 8 & 255);
			}
		}
		bs.close();
		return new Pcm(out.toByteArray(), rate);
	}

	private static Pcm ogg(byte[] bytes) throws Exception {
		try (var s = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
			AudioFormat fmt = s.getFormat();
			ByteBuffer all = s.readAll().order(ByteOrder.LITTLE_ENDIAN);
			int ch = fmt.getChannels();
			var out = new ByteArrayOutputStream();
			while (all.remaining() >= 2 * ch) {
				int sum = 0;
				for (int c = 0; c < ch; c++) sum += all.getShort();
				short m = (short) (sum / ch);
				out.write(m & 255);
				out.write(m >> 8 & 255);
			}
			return new Pcm(out.toByteArray(), (int) fmt.getSampleRate());
		}
	}

	/** A Roblox Sound attached to an entity (follows it). volume/pitch can change while it plays. */
	static final class Voice extends AbstractTickableSoundInstance implements FabricSoundInstance {
		private final Pcm pcm;
		private final Entity at;

		Voice(Pcm pcm, Entity at, boolean looped, float volume, float pitch) {
			super(EVENT, SoundSource.PLAYERS, RandomSource.create());
			this.pcm = pcm;
			this.at = at;
			this.looping = looped;
			this.volume = volume;
			this.pitch = pitch;
			x = at.getX(); y = at.getY(); z = at.getZ();
		}

		void volume(float v) { volume = v; }
		void end() { stop(); }

		@Override public boolean canStartSilent() { return true; } // FreeFalling starts at volume 0 and fades in

		@Override
		public void tick() {
			if (at.isRemoved()) { stop(); return; }
			x = at.getX(); y = at.getY(); z = at.getZ();
		}

		@Override
		public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
			return CompletableFuture.completedFuture(new Stream(pcm, looping));
		}
	}

	/** Plays a sound file once (or looped) on an entity; null if the file isn't in the install. */
	static Voice play(String rel, Entity at, boolean looped, float volume, float pitch) {
		Pcm p = pcm(rel);
		if (p == null) return null;
		var v = new Voice(p, at, looped, volume, pitch);
		Minecraft.getInstance().getSoundManager().play(v);
		return v;
	}

	private static final class Stream implements AudioStream {
		private final Pcm pcm;
		private final boolean loop;
		private int pos;

		Stream(Pcm pcm, boolean loop) { this.pcm = pcm; this.loop = loop; }

		@Override public AudioFormat getFormat() { return new AudioFormat(pcm.rate, 16, 1, true, false); }

		@Override
		public ByteBuffer read(int size) {
			var out = ByteBuffer.allocateDirect(size & ~1).order(ByteOrder.LITTLE_ENDIAN);
			byte[] d = pcm.data;
			while (out.hasRemaining() && d.length > 0) {
				if (pos >= d.length) { if (!loop) break; pos = 0; }
				int n = Math.min(out.remaining(), d.length - pos);
				out.put(d, pos, n);
				pos += n;
			}
			return out.flip();
		}

		@Override public void close() {}
	}
}
