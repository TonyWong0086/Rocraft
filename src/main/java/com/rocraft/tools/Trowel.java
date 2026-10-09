package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Trowel (WallMaker): at the mouse, facing the look direction snapped to an axis, build a wall 12 studs wide and
 * 4 high, one brick every 0.04 s, all one random colour. In blocks that is 3 wide x 2 high (rounded up so it
 * still stops a character), built one block per tick from the bottom row up.
 */
public final class Trowel extends Item {
	static final String[] COLORS = {"red", "blue", "yellow", "lime", "orange", "purple", "white", "black", "green", "cyan"};

	public Trowel(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		var stack = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		Vec3 target = Tools.mouse(p), look = target.subtract(p.getEyePosition());
		boolean alongX = Math.abs(look.x) > Math.abs(look.z); // snap(): wall faces the dominant axis
		int width = (int) Math.round(12 * McFrame.STUD), height = (int) Math.ceil(4 * McFrame.STUD);
		var block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(COLORS[p.getRandom().nextInt(COLORS.length)] + "_concrete")).defaultBlockState();
		BlockPos base = BlockPos.containing(target);
		if (!sl.getBlockState(base).isAir()) base = base.above();
		p.getCooldowns().addCooldown(stack, width * height + 2);
		com.rocraft.RbxSounds.play(p, com.rocraft.RbxSounds.get("trowel.build")); // BuildSound = bass.wav
		int n = 0;
		for (int y = 0; y < height; y++)
			for (int i = 0; i < width; i++) {
				int off = i - width / 2;
				BlockPos pos = alongX ? base.offset(0, y, off) : base.offset(off, y, 0);
				Tools.later(++n, () -> { if (sl.getBlockState(pos).canBeReplaced()) sl.setBlockAndUpdate(pos, block); });
			}
		return InteractionResult.SUCCESS;
	}
}
