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

	/**
	 * One panel at (x, y); returns its right edge. Rows are {header}, {label, value}, {label, value, load} (load = share
	 * of its budget, drawn as a meter bar after the values: green under 75%, yellow to 100%, red over) or {GRAPH}.
	 */
	private static int panel(GuiGraphicsExtractor g, List<String[]> rows, int x, int y, int px, int line) {
		String f = RbxFont.LEGACY;
		int col = 0, val = 0, w = 0, bar = px * 6, graphH = line * 3;
		boolean meters = false;
		for (String[] r : rows) if (r.length >= 2) { col = Math.max(col, RbxFont.width(r[0], px, f)); if (r.length == 3) { meters = true; val = Math.max(val, RbxFont.width(": " + r[1], px, f)); } }
		col += RbxFont.width(" ", px, f);
		for (String[] r : rows) w = Math.max(w, r.length >= 2 ? col + RbxFont.width(": " + r[1], px, f) : r[0] == GRAPH ? 0 : RbxFont.width(r[0], px, f));
		if (meters) w = Math.max(w, col + val + 8 + bar);
		int h = 0;
		for (String[] r : rows) h += r[0] == GRAPH ? graphH + 4 : line;
		int x1 = x + w + 2 * PAD;
		g.fill(x, y, x1, y + h + 2 * PAD, BG);
		int ty = y + PAD;
		for (String[] r : rows) {
			if (r[0] == GRAPH) { graph(g, x + PAD, ty + 2, w, graphH); ty += graphH + 4; continue; }
			text(g, r[0], x + PAD, ty, px);
			if (r.length >= 2) text(g, ": " + r[1], x + PAD + col, ty, px);
			if (r.length == 3) meter(g, x + PAD + col + val + 8, ty + line / 4, bar, Math.max(3, line / 2), Float.parseFloat(r[2]));
			ty += line;
		}
		return x1;
	}

	static final String GRAPH = "#graph";

	private static int loadColor(float load) { return load < 0.75f ? 0xFF1BFC6B : load <= 1 ? 0xFFFAEB00 : 0xFFFF1C00; }

	/** A meter: dark track, filled to load (capped at the track), in its load colour. */
	private static void meter(GuiGraphicsExtractor g, int x, int y, int w, int h, float load) {
		g.fill(x, y, x + w, y + h, 0x80000000);
		int fw = Math.round(w * Math.min(1, Math.max(0, load)));
		if (fw > 0) g.fill(x, y, x + fw, y + h, loadColor(load));
	}

	/**
	 * The last 240 frames' times as bars (taller = slower, coloured against the budget) with the frame budget as a white
	 * line. The top fits the slowest frames (at least twice the budget), so a slow stretch still shows its shape.
	 */
	private static void graph(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		float[] ms = FrameStats.history();
		float budget = (float) FrameStats.budgetMs(), top = (float) Math.max(budget * 2, FrameStats.p99 * 1.15);
		g.fill(x, y, x + w, y + h, 0x80000000);
		float step = (float) w / FrameStats.N;
		for (int i = 0; i < ms.length; i++) {
			int bh = Math.max(1, Math.round(h * Math.min(1, ms[i] / top)));
			int bx = x + w - Math.round((ms.length - i) * step);
			g.fill(bx, y + h - bh, Math.max(bx + 1, x + w - Math.round((ms.length - i - 1) * step)), y + h, loadColor(ms[i] / budget));
		}
		int by = y + h - Math.round(h * budget / top);
		g.fill(x, by, x + w, by + 1, 0xC0FFFFFF); // budget
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
			double tickBudget = 1000 / rate;
			meter(L, "Physics", f1(Math.min(rate, 1000 / Math.max(mspt, 1e-3))) + " ticks/s, " + f1(mspt) + " ms of " + f1(tickBudget) + " (" + pct(mspt / tickBudget) + ")", mspt / tickBudget);
			row(L, "Tick rate", f1(rate) + "/s" + (srv.tickRateManager().isFrozen() ? " (frozen)" : ""));
		} else row(L, "Physics", "on the server");
		meter(L, "Render", mc.getFps() + " FPS, " + f1(FrameStats.median) + " ms of " + f1(budget) + " (" + pct(FrameStats.median / budget) + ")", FrameStats.median / budget);
		L.add(new String[]{GRAPH});
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
		meter(R, "GPU load", f1(mc.getGpuUtilization()) + "%", mc.getGpuUtilization() / 100);
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
		meter(R, "Memory", used + "M / " + (rt.maxMemory() >> 20) + "M" + (FrameStats.memoryPressure() ? " (pressure)" : ""), used / (float) (rt.maxMemory() >> 20) / 0.85f);
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
	/** A row with a meter; load 1 = exactly its budget. */
	private static void meter(List<String[]> rows, String label, String value, double load) { rows.add(new String[]{label, value, String.valueOf((float) load)}); }
	private static String f1(double v) { return String.format("%.1f", v); }
	private static String f2(double v) { return String.format("%.2f", v); }
	private static String pct(double v) { return Math.round(v * 100) + "%"; }
}
