package com.rocraft.tools;

import com.rocraft.sim.McFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The classic Roblox Bomb ("Timebomb" Tool, PlantBomb script): click to plant a bomb 3 studs above the handle,
 * the handle disappears, and no debounce (Rocraft asks for spammable tools).
 */
public final class BombItem extends Item {
	public BombItem(Properties p) { super(p); }

	@Override
	public InteractionResult use(Level level, Player p, InteractionHand hand) {
		if (level instanceof ServerLevel sl) {
			var b = new BombEntity(Tools.BOMB_ENTITY, sl);
			var look = p.getLookAngle().multiply(1, 0, 1).normalize();
			// handle sits in the right hand (~2 studs up, 1 stud forward); spawnPos.y + 3
			b.setPos(p.getX() + look.x * McFrame.STUD, p.getY() + 5 * McFrame.STUD, p.getZ() + look.z * McFrame.STUD);
			b.setOwner(p);
			sl.addFreshEntity(b);
		}
		return InteractionResult.CONSUME;
	}
}
