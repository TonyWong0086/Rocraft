package com.rocraft.client;

import com.rocraft.RocraftConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * F3 with the 2018 interface: Roblox's Shift+F stats panels (grey see-through boxes, white text with a black shadow,
 * "----- Section -----" headers, "Label : value" rows) under the top bar, filled with this game's numbers. Left
 * panel FPS / World / Kernel, right panel Graphics / Performance / Timing. The text is rebuilt 4 times a second
 * (Roblox's stats tick about that often), not every frame.
 */
public final class RobloxStats {
	static final int BG = 0x55202020, PAD = 6;
	private static List<String[]> left = List.of(), right = List.of();
	private static long builtAt;

	public static void draw(GuiGraphicsExtractor g) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		long now = System.currentTimeMillis();
		if (now - builtAt > 250) { builtAt = now; build(mc); }
		int s = mc.getWindow().getGuiScale(), H = g.guiHeight() * s;
		int px = H >= 900 ? 15 : 12, line = px + px / 4 + 1;
		var m = g.pose();
		m.pushMatrix();
		m.scale(1f / s, 1f / s); // screen pixels, like the rest of the 2018 HUD
		int x = 8, y = Hud2018.TOP + 8;
		x = panel(g, left, x, y, px, line) + 8;
		panel(g, right, x, y, px, line);
		m.popMatrix();
	}

	/** One panel at (x, y); returns its right edge. Rows are {header} or {label, value}. */
	private static int panel(GuiGraphicsExtractor g, List<String[]> rows, int x, int y, int px, int line) {
		String f = RbxFont.LEGACY;
		int col = 0, w = 0;
		for (String[] r : rows) if (r.length == 2) col = Math.max(col, RbxFont.width(r[0], px, f));
		col += RbxFont.width(" ", px, f);
		for (String[] r : rows) w = Math.max(w, r.length == 2 ? col + RbxFont.width(": " + r[1], px, f) : RbxFont.width(r[0], px, f));
		int x1 = x + w + 2 * PAD;
		g.fill(x, y, x1, y + rows.size() * line + 2 * PAD, BG);
		int ty = y + PAD;
		for (String[] r : rows) {
			text(g, r[0], x + PAD, ty, px);
			if (r.length == 2) text(g, ": " + r[1], x + PAD + col, ty, px);
			ty += line;
		}
		return x1;
	}

	private static void text(GuiGraphicsExtractor g, String s, int x, int y, int px) {
		RbxFont.draw(g, s, x + 1, y + 1, px, RbxFont.LEGACY, 0xFF000000);
		RbxFont.draw(g, s, x, y, px, RbxFont.LEGACY, 0xFFFFFFFF);
	}

	private static void build(Minecraft mc) {
		var L = new ArrayList<String[]>();
		var R = new ArrayList<String[]>();
		var level = mc.level;
		var p = mc.player;
		var srv = mc.getSingleplayerServer();
		var conn = mc.getConnection();
		double budget = FrameStats.budgetMs();

		head(L, "FPS");
		if (srv != null) {
			double mspt = srv.getAverageTickTimeNanos() / 1e6, rate = srv.tickRateManager().tickrate();
			row(L, "Physics", f1(Math.min(rate, 1000 / Math.max(mspt, 1e-3))) + "/s  " + f1(mspt) + " msec " + pct(mspt / (1000 / rate)));
			row(L, "PhysicsReal", f1(rate) + "/s  throttle@" + (srv.tickRateManager().isFrozen() ? "0%" : "100%"));
		} else row(L, "Physics", "? (server)");
		row(L, "Render", mc.getFps() + "/s  " + f1(FrameStats.median) + " msec " + pct(FrameStats.median / budget));
		row(L, "Heartbeat", "20.0/s");
		if (conn != null) {
			row(L, "Network send", f1(conn.getConnection().getAverageSentPackets()) + "/s");
			row(L, "Network recv", f1(conn.getConnection().getAverageReceivedPackets()) + "/s");
			var info = conn.getPlayerInfo(p.getUUID());
			row(L, "Ping", (info == null ? "?" : info.getLatency()) + " msec");
		}

		head(L, "World");
		int view = mc.options.getEffectiveRenderDistance(), sim = mc.options.simulationDistance().get();
		row(L, "Player Radius", view * 16 + " / " + sim * 16 + " (view / sim, blocks)");
		int all = 0, moving = 0, parts = 0, loose = 0, debris = 0;
		for (var e : level.entitiesForRendering()) {
			all++;
			if (e.getDeltaMovement().lengthSqr() > 1e-4) moving++;
			if (e instanceof com.rocraft.tools.RobloxPart rp) { parts++; if (!rp.anchored()) loose++; }
			if (e instanceof com.rocraft.tools.Debris) debris++;
		}
		row(L, "Entities", String.valueOf(all));
		row(L, "Moving entities", String.valueOf(moving));
		row(L, "Parts", parts + " (" + loose + " unanchored)");
		row(L, "Debris", String.valueOf(debris));
		row(L, "Chunks", level.getChunkSource().getLoadedChunksCount() + " loaded, " + (Perf.SODIUM ? "drawn by Sodium" : mc.levelRenderer.visibleSections().size() + " sections drawn"));
		var pos = p.blockPosition();
		row(L, "Position", String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
		row(L, "Facing", p.getDirection().getName() + " (" + f1(net.minecraft.util.Mth.wrapDegrees(p.getYRot())) + " / " + f1(p.getXRot()) + ")");
		row(L, "Biome", level.getBiome(pos).getRegisteredName());
		row(L, "Light", level.getRawBrightness(pos, 0) + " (sky " + level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos)
			+ ", block " + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) + ")");
		row(L, "Gravity", "196.199997");

		head(L, "Kernel");
		row(L, "Phys. SimRate", "Fixed20Hz (ragdolls 240Hz)");
		row(L, "Ragdolls", String.valueOf(Ragdoll.active()));
		row(L, "Animators", String.valueOf(Animator.active()));
		row(L, "Threads", Thread.activeCount() + " (" + Runtime.getRuntime().availableProcessors() + " cores)");
		row(L, "Java", System.getProperty("java.version"));

		head(R, "Graphics");
		row(R, "Resolution", mc.getWindow().getWidth() + " x " + mc.getWindow().getHeight());
		var dev = com.mojang.blaze3d.systems.RenderSystem.tryGetDevice();
		if (dev != null) {
			var d = dev.getDeviceInfo();
			row(R, "Technology", d.backendName());
			row(R, "GPU", d.name());
			row(R, "Driver", d.vendorName() + " " + d.driverInfo());
		}
		row(R, "GPU load", f1(mc.getGpuUtilization()) + "%");
		int limit = mc.options.framerateLimit().get();
		row(R, "Frame limit", (limit >= 260 ? "unlimited" : String.valueOf(limit)) + (mc.options.enableVsync().get() ? ", VSync" : ""));

		head(R, "Performance");
		var c = RocraftConfig.INSTANCE;
		row(R, "Entity culling", (Perf.CULLING_BY != null ? "by " + Perf.CULLING_BY : c.entityCulling ? "on" : "off")
			+ "  " + Perf.entHidden + " hidden / " + (Perf.entShown + Perf.entHidden));
		row(R, "Block entities", Perf.beHidden + " hidden / " + (Perf.beShown + Perf.beHidden));
		row(R, "Unfocused FPS", Perf.FPS_BY != null ? "by " + Perf.FPS_BY : c.unfocusedFps ? Perf.UNFOCUSED_FPS + " FPS" : "off");
		row(R, "Adaptive", c.adaptivePerf ? "on  load " + f2(FrameStats.load) : "off");
		row(R, "Particles", Perf.partsDropped + " dropped (" + mc.particleEngine.countParticles() + " alive)");
		row(R, "Animation", Perf.posesSolved + " posed, " + Perf.posesReused + " reused");
		row(R, "Frame pacing", c.stableFps ? "stable " + FrameStats.stableCap + " FPS" : "off");
		row(R, "Spike predictor", FrameStats.underPressure() ? "pressure" : "steady");
		row(R, "Bottleneck", FrameStats.bottleneck());
		row(R, "Cache trims", String.valueOf(Perf.cacheTrims));
		int on = (Perf.cullPatch ? 1 : 0) + (Perf.bePatch ? 1 : 0) + (Perf.fpsPatch ? 1 : 0) + (FrameStats.frames > 0 ? 1 : 0);
		row(R, "Patches", on + "/4 active");
		row(R, "Opt. mods", Perf.OPT_MODS.isEmpty() ? "none" : String.join(", ", Perf.OPT_MODS));

		head(R, "Timing");
		row(R, "Frame", f1(FrameStats.median) + " ms (p90 " + f1(FrameStats.p90) + ", p99 " + f1(FrameStats.p99) + ", max " + f1(FrameStats.max) + ")");
		row(R, "Budget", f1(budget) + " ms" + (mc.options.enableVsync().get() ? " (VSync " + FrameStats.refreshRate() + " Hz)" : ""));
		row(R, "Spikes", FrameStats.SPIKE_CAUSES[1] + " GC, " + FrameStats.SPIKE_CAUSES[2] + " chunks, " + FrameStats.SPIKE_CAUSES[0] + " other");
		var last = new StringBuilder();
		for (long[] sp : FrameStats.SPIKES) {
			if (last.length() > 0) last.append(", ");
			last.append(sp[0]).append("ms ").append(FrameStats.CAUSE[(int) sp[1]]).append(' ').append((System.currentTimeMillis() - sp[2]) / 1000).append("s ago");
			if (last.length() > 40) break;
		}
		row(R, "Last spikes", last.length() == 0 ? "none" : last.toString());
		var rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
		row(R, "Memory", used + "M / " + (rt.maxMemory() >> 20) + "M" + (FrameStats.memoryPressure() ? " (pressure)" : ""));
		row(R, "Alloc", f1(FrameStats.allocMBs) + " MB/s (render thread)");
		row(R, "GC", FrameStats.gcCount + " runs, " + FrameStats.gcTotalMs + " ms total");
		var sec = mc.levelRenderer.sectionRenderDispatcher();
		if (Perf.SODIUM) row(R, "Chunk builds", "by Sodium");
		else if (sec != null) row(R, "Chunk builds", sec.getCompileQueueSize() + " queued, " + sec.getFreeBufferCount() + " free buffers");
		left = L;
		right = R;
	}

	private static void head(List<String[]> rows, String name) { rows.add(new String[]{"----- " + name + " -----"}); }
	private static void row(List<String[]> rows, String label, String value) { rows.add(new String[]{label, value}); }
	private static String f1(double v) { return String.format("%.1f", v); }
	private static String f2(double v) { return String.format("%.2f", v); }
	private static String pct(double v) { return Math.round(v * 100) + "%"; }
}
