package com.rocraft.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

/**
 * Frame-time monitor (work time: the frame minus the frame-rate limiter's sleep) behind the adaptive optimisations and the stats panel (RobloxStats). Every frame's duration
 * (DebugScreenOverlay.logFrameDuration, called each frame by Minecraft) goes into a 240-frame ring; from it:
 * - load: how far the median frame is over its budget (the frame-rate limit, or 60 FPS when unlimited). The
 *   particle manager and animation throttling scale back when it goes over 1.
 * - spikes (microstutter): frames over twice the median and 8 ms, tagged by cause: a GC pause during the frame,
 *   chunk meshes queued (world loading), or other. The last few are kept for the panel.
 * - trend (spike predictor): median of the newest 30 frames vs the whole ring, plus heap nearly full; either one
 *   counts as "under pressure" ahead of an actual stutter.
 * - memory: heap use, GC time, and the render thread's allocation rate (hot-path allocation profiler).
 * - stable cap (adaptive FPS stabiliser, opt-in): the highest of the usual refresh steps the 90th-percentile frame
 *   can hold, so frames come evenly instead of racing then stalling.
 */
public final class FrameStats {
	static final int N = 240;
	private static final long[] RING = new long[N];
	private static int count, head;
	static long frames;
	/** Recent spikes: {ms, cause, millis when}. Cause: 0 other, 1 GC, 2 chunks. */
	static final ArrayDeque<long[]> SPIKES = new ArrayDeque<>();
	static final int[] SPIKE_CAUSES = new int[3];
	static final String[] CAUSE = {"other", "GC", "chunks"};
	private static final List<GarbageCollectorMXBean> GCS = ManagementFactory.getGarbageCollectorMXBeans();
	private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
	private static long gcCountLast, allocLast, allocAt = System.nanoTime();
	static double median = 16.7, p90 = 16.7, p99 = 16.7, max, recent = 16.7, load = 1, allocMBs;
	static long gcTotalMs, gcCount;
	static int stableCap = 0;
	private static long statsAt;

	private static long waited;

	/** Time FramerateLimiter slept this frame: not work, so it's left out (else a capped frame looks "slow"). */
	public static void waited(long nanos) { waited += nanos; }

	/** One finished frame of `total` nanoseconds, including any limiter sleep. */
	public static void frame(long total) {
		long nanos = Math.max(0, total - waited);
		waited = 0;
		frames++;
		Perf.newFrame();
		RING[head] = nanos;
		head = (head + 1) % N;
		count = Math.min(N, count + 1);
		long gcTime = 0, gcN = 0;
		for (var gc : GCS) { gcTime += Math.max(0, gc.getCollectionTime()); gcN += Math.max(0, gc.getCollectionCount()); }
		boolean gcThisFrame = gcN != gcCountLast;
		gcTotalMs = gcTime; gcCount = gcN; gcCountLast = gcN;
		double ms = nanos / 1e6;
		if (count > 30 && ms > Math.max(2 * median, median + 8)) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			int cause = gcThisFrame ? 1 : mc.levelRenderer != null && mc.level != null && mc.levelRenderer.sectionRenderDispatcher() != null
				&& mc.levelRenderer.sectionRenderDispatcher().getCompileQueueSize() > 8 ? 2 : 0;
			SPIKE_CAUSES[cause]++;
			SPIKES.addFirst(new long[]{Math.round(ms), cause, System.currentTimeMillis()});
			if (SPIKES.size() > 6) SPIKES.removeLast();
		}
		long now = System.nanoTime();
		if (now - statsAt > 250_000_000L) { statsAt = now; recompute(); } // a sort 4x a second, not every frame
	}

	/** The ring's frame times in ms, oldest first (for the stats graph). */
	static float[] history() {
		float[] out = new float[count];
		for (int i = 0; i < count; i++) out[i] = RING[Math.floorMod(head - count + i, N)] / 1e6f;
		return out;
	}

	private static void recompute() {
		if (count == 0) return;
		long[] s = new long[count];
		for (int i = 0; i < count; i++) s[i] = RING[i];
		long[] newest = new long[Math.min(30, count)];
		for (int i = 0; i < newest.length; i++) newest[i] = RING[Math.floorMod(head - 1 - i, N)];
		Arrays.sort(s);
		Arrays.sort(newest);
		median = s[count / 2] / 1e6;
		p90 = s[count * 9 / 10] / 1e6;
		p99 = s[Math.min(count - 1, count * 99 / 100)] / 1e6;
		max = s[count - 1] / 1e6;
		recent = newest[newest.length / 2] / 1e6;
		load = recent / budgetMs();
		long t = System.nanoTime(), alloc = THREADS.getCurrentThreadAllocatedBytes(); // render thread
		if (alloc > 0) allocMBs = (alloc - allocLast) / 1048576.0 / ((t - allocAt) / 1e9);
		allocLast = alloc; allocAt = t;
		// stabiliser: the highest step the slow frames can keep up with; only steps down/up with some headroom
		int[] steps = {30, 40, 45, 60, 72, 75, 90, 100, 120, 144, 165, 240};
		int fit = 30;
		for (int st : steps) if (1000.0 / st >= p90 * 1.05) fit = st;
		if (stableCap == 0 || fit < stableCap || 1000.0 / fit > p90 * 1.4) stableCap = fit;
		if (memoryPressure()) Perf.trimCaches();
	}

	/** Frame budget in ms: the frame-rate limit (60 FPS when unlimited), no faster than the monitor with VSync on. */
	static double budgetMs() {
		var o = net.minecraft.client.Minecraft.getInstance().options;
		int limit = o.framerateLimit().get();
		int fps = limit <= 0 || limit >= 260 ? 60 : Math.min(limit, 240);
		if (o.enableVsync().get()) fps = Math.min(fps, refreshRate());
		return 1000.0 / fps;
	}

	private static int refresh;

	/** The primary monitor's refresh rate (60 if unknown). */
	static int refreshRate() {
		if (refresh == 0) {
			var mode = org.lwjgl.glfw.GLFW.glfwGetVideoMode(org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor());
			refresh = mode == null || mode.refreshRate() <= 0 ? 60 : mode.refreshRate();
		}
		return refresh;
	}

	/** Spike predictor: frames trending up (newest 30 at 1.3x the long median) or the heap nearly full. */
	static boolean underPressure() { return recent > median * 1.3 + 1 || memoryPressure(); }

	static boolean memoryPressure() {
		var rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory() > rt.maxMemory() * 0.85;
	}

	/** Likely bottleneck from what's measured: GC/heap, chunk building, the GPU (busy while the CPU part is short), else the CPU. */
	static String bottleneck() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (load < 0.9 && !underPressure()) return "none (on budget)";
		if (memoryPressure() || SPIKE_CAUSES[1] > SPIKE_CAUSES[0] + SPIKE_CAUSES[2]) return "memory (GC)";
		if (mc.levelRenderer.sectionRenderDispatcher() != null && mc.levelRenderer.sectionRenderDispatcher().getCompileQueueSize() > 64) return "chunk building / I/O";
		double gpu = mc.getGpuUtilization();
		if (gpu > 90) return "GPU";
		return "CPU";
	}
}
