package com.rocraft.client;

import com.rocraft.RocraftConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Built-in versions of two common client optimisation mods, each switched off when a mod that does the same job is
 * installed (that mod wins, so they never fight):
 * - Entity culling (like EntityCulling; Sodium also hides entities in sections it can't see): entities and block
 *   entities (chests, signs, banners...) hidden behind solid blocks aren't drawn. Roblox avatars are many meshes each,
 *   so this matters more here than in vanilla.
 * - Unfocused FPS cap (like Dynamic FPS): 30 FPS while the game window isn't focused. Vanilla already throttles a
 *   minimised or idle window.
 * Plus adaptive ones driven by FrameStats (Graphics > Adaptive Performance), which no other mod covers for
 * Rocraft's own work: Roblox particles thinned out and far-away avatars animated less often while frames run over
 * budget, and Rocraft's caches trimmed when the heap is nearly full. Opt-in: Stable Frame Pacing (FrameStats.stableCap).
 * The mixins behind these are optional (rocraft.perf.mixins.json): if one can't apply, the game still starts and
 * the stats panel shows it as inactive.
 */
public final class Perf {
	/** The mod that already does each job, or null if Rocraft does it. */
	static final String CULLING_BY = firstLoaded("entityculling", "sodium"), FPS_BY = firstLoaded("dynamic_fps");
	/** Sodium draws the chunks itself: vanilla's section lists and compile queue stay empty. */
	static final boolean SODIUM = firstLoaded("sodium") != null;
	static final int UNFOCUSED_FPS = 30, RECHECK_TICKS = 4, MAX_DIST = 96;
	/** entity id, or ~block pos for block entities -> {tick last checked, 1 visible / 0 hidden} */
	private static final Map<Long, int[]> SEEN = new HashMap<>();

	/** Known optimisation mods that are installed (for the stats panel). */
	static final List<String> OPT_MODS = new ArrayList<>();
	static {
		for (String id : new String[]{"sodium", "lithium", "ferritecore", "c2me", "immediatelyfast", "modernfix", "entityculling", "dynamic_fps", "moreculling", "iris", "krypton", "noisium", "vmp"})
			FabricLoader.getInstance().getModContainer(id).ifPresent(m -> OPT_MODS.add(m.getMetadata().getName()));
	}

	/** Set the first time each optional patch runs: proof it applied. */
	public static boolean cullPatch, bePatch, fpsPatch, framePatch;
	/** Last frame's counts: entities / block entities drawn and hidden, Roblox particles dropped, avatar poses reused. */
	static int entShown, entHidden, beShown, beHidden, partsDropped, posesReused, posesSolved, cacheTrims;
	private static int entShownN, entHiddenN, beShownN, beHiddenN, droppedN, reusedN, solvedN;

	static void newFrame() {
		entShown = entShownN; entHidden = entHiddenN; beShown = beShownN; beHidden = beHiddenN;
		partsDropped = droppedN; posesReused = reusedN; posesSolved = solvedN;
		entShownN = entHiddenN = beShownN = beHiddenN = droppedN = reusedN = solvedN = 0;
	}

	static boolean adaptive() { return RocraftConfig.INSTANCE.adaptivePerf; }

	/** Dynamic particle manager: whether to spawn one more cosmetic Roblox particle at the current load. */
	static boolean keepParticle(java.util.Random r) {
		if (!adaptive() || FrameStats.load <= 1.1 && !FrameStats.underPressure()) return true;
		double keep = Math.clamp(1 / FrameStats.load, 0.3, 1) * (FrameStats.underPressure() ? 0.7 : 1);
		if (r.nextDouble() < keep) return true;
		droppedN++;
		return false;
	}

	/**
	 * Animation throttling: an avatar's pose is worked out every this many frames (the frames in between reuse it):
	 * every frame within 32 blocks, then every 2nd / 3rd further out, twice as sparse while over budget.
	 */
	static int poseEvery(Entity e) {
		if (!adaptive() || e == null || FrameStats.frames == 0) return 1; // no frame counter: the patch didn't apply
		var mc = Minecraft.getInstance();
		double d2 = e.distanceToSqr(mc.gameRenderer.mainCamera().position());
		int n = d2 < 32 * 32 ? 1 : d2 < 64 * 64 ? 2 : 3;
		return n > 1 && FrameStats.load > 1.2 ? n * 2 : n;
	}

	static void poseCounted(boolean reused) { if (reused) reusedN++; else solvedN++; }

	/** Adaptive cache eviction: drop what Rocraft can rebuild when the heap is nearly full. */
	static void trimCaches() {
		if (!adaptive()) return;
		SEEN.clear();
		cacheTrims++;
	}

	private static String firstLoaded(String... ids) {
		for (String id : ids) {
			var mod = FabricLoader.getInstance().getModContainer(id);
			if (mod.isPresent()) return mod.get().getMetadata().getName();
		}
		return null;
	}

	static boolean culling() { return CULLING_BY == null && RocraftConfig.INSTANCE.entityCulling; }

	/** FramerateLimitTracker hook: the limit to use instead of `limit`. */
	public static int framerateLimit(int limit) {
		var mc = Minecraft.getInstance();
		fpsPatch = true;
		if (RocraftConfig.INSTANCE.stableFps && mc.level != null && FrameStats.stableCap > 0)
			limit = Math.min(limit <= 0 || limit >= 260 ? Integer.MAX_VALUE : limit, FrameStats.stableCap);
		if (FPS_BY != null || !RocraftConfig.INSTANCE.unfocusedFps || mc.level == null || mc.isWindowActive()) return limit;
		return Math.min(limit, UNFOCUSED_FPS);
	}

	/** EntityRenderDispatcher.shouldRender hook: false when the entity is walled off from the camera. */
	public static boolean visible(Entity e) {
		cullPatch = true;
		boolean v = visible0(e);
		if (v) entShownN++; else entHiddenN++;
		return v;
	}

	private static boolean visible0(Entity e) {
		if (!culling()) return true;
		var mc = Minecraft.getInstance();
		if (e == mc.getCameraEntity() || e == mc.player || e.isCurrentlyGlowing() || e.hasPassenger(mc.player) || mc.player != null && mc.player.isPassengerOfSameVehicle(e)) return true;
		if (e instanceof LivingEntity le && le.isDeadOrDying()) return true; // its fallen parts spread past its box
		AABB box = e.getBoundingBox();
		if (box.getXsize() > 4 || box.getYsize() > 4 || box.getZsize() > 4) return true; // big / multipart: not worth it
		return visible(e.getId(), box);
	}

	/** BlockEntityRenderDispatcher hook: false when the block entity is walled off from the camera. */
	public static boolean visible(BlockEntity be) {
		bePatch = true;
		boolean v = !culling() || visible(~be.getBlockPos().asLong(), new AABB(be.getBlockPos()));
		if (v) beShownN++; else beHiddenN++;
		return v;
	}

	private static boolean visible(long key, AABB box) {
		var mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		if (box.inflate(1.5).contains(cam) || box.getCenter().distanceToSqr(cam) > MAX_DIST * MAX_DIST) return true;
		int now = (int) mc.level.getGameTime();
		if (SEEN.size() > 4096) SEEN.clear(); // ponytail: crude cap instead of tracking entity removal
		int[] s = SEEN.computeIfAbsent(key, k -> new int[]{Integer.MIN_VALUE, 1});
		if (now - s[0] >= RECHECK_TICKS || now < s[0]) { s[0] = now; s[1] = seen(cam, box.inflate(0.1)) ? 1 : 0; }
		return s[1] == 1;
	}

	/** Can the camera see any corner or the middle of the box through non-solid blocks? */
	private static boolean seen(Vec3 cam, AABB b) {
		var level = Minecraft.getInstance().level;
		double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
		if (clear(level, cam, b.getCenter())) return true;
		for (double x : xs) for (double y : ys) for (double z : zs) if (clear(level, cam, new Vec3(x, y, z))) return true;
		return false;
	}

	/** Voxel walk (Amanatides-Woo) from a to b; false at the first block that fully blocks the view. */
	static boolean clear(net.minecraft.world.level.BlockGetter level, Vec3 a, Vec3 b) {
		int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y), z = (int) Math.floor(a.z);
		int ex = (int) Math.floor(b.x), ey = (int) Math.floor(b.y), ez = (int) Math.floor(b.z);
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
		double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy), tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double tx = dx == 0 ? Double.MAX_VALUE : (sx > 0 ? x + 1 - a.x : a.x - x) * tdx;
		double ty = dy == 0 ? Double.MAX_VALUE : (sy > 0 ? y + 1 - a.y : a.y - y) * tdy;
		double tz = dz == 0 ? Double.MAX_VALUE : (sz > 0 ? z + 1 - a.z : a.z - z) * tdz;
		var pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 512 && !(x == ex && y == ey && z == ez); i++) {
			if (tx < ty && tx < tz) { x += sx; tx += tdx; } else if (ty < tz) { y += sy; ty += tdy; } else { z += sz; tz += tdz; }
			if (x == ex && y == ey && z == ez) break; // the target's own cell doesn't hide it
			if (level.getBlockState(pos.set(x, y, z)).isSolidRender()) return false;
		}
		return true;
	}

	/** Self-check: a wall between hides, an open line and a glass-like gap don't. */
	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var wall = new java.util.HashSet<BlockPos>();
		for (int y = 0; y < 4; y++) for (int z = -3; z <= 3; z++) wall.add(new BlockPos(5, y, z));
		net.minecraft.world.level.BlockGetter level = new net.minecraft.world.level.BlockGetter() {
			public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p) {
				return wall.contains(p) ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			}
			public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
			public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p) { return null; }
			public int getHeight() { return 384; }
			public int getMinY() { return -64; }
		};
		if (clear(level, new Vec3(0.5, 1.5, 0.5), new Vec3(10.5, 1.5, 0.5))) throw new AssertionError("seen through the wall");
		if (!clear(level, new Vec3(0.5, 5.5, 0.5), new Vec3(10.5, 5.5, 0.5))) throw new AssertionError("hidden over the wall");
		if (!clear(level, new Vec3(0.5, 1.5, 0.5), new Vec3(4.5, 1.5, 3.5))) throw new AssertionError("hidden in front of the wall");
		if (clear(level, new Vec3(0.2, 1.7, -2.4), new Vec3(9.9, 0.3, 2.8))) throw new AssertionError("diagonal seen through the wall");
		System.out.println("ok");
	}
}
