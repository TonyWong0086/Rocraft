package com.rocraft;

import com.rocraft.sim.McFrame;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rocraft implements ModInitializer {
	public static final String MOD_ID = "rocraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static Identifier id(String p) { return Identifier.fromNamespaceAndPath(MOD_ID, p); }

	@Override
	public void onInitialize() {
		// ponytail: reads this PC's config, so it applies in singleplayer/LAN host; a dedicated server would need a synced setting
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			var c = RocraftConfig.INSTANCE;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				mod(p, Attributes.GRAVITY, "roblox_gravity", c.robloxMovement, McFrame.GRAVITY - 0.08, Operation.ADD_VALUE);
				mod(p, Attributes.JUMP_STRENGTH, "roblox_jump", c.robloxMovement, McFrame.JUMP - 0.42, Operation.ADD_VALUE);
				mod(p, Attributes.MOVEMENT_SPEED, "roblox_walk", c.robloxMovement, McFrame.SPEED - 0.1, Operation.ADD_VALUE);
				mod(p, Attributes.FALL_DAMAGE_MULTIPLIER, "roblox_no_fall", !c.fallDamage, -1, Operation.ADD_MULTIPLIED_TOTAL);
				if (!c.hunger) p.getFoodData().setFoodLevel(20);
			}
		});
		RbxSounds.init();
		RbxParticles.init();
		com.rocraft.tools.Tools.register();
		LOGGER.info("Rocraft loaded");
	}

	static void mod(ServerPlayer p, Holder<Attribute> a, String name, boolean on, double v, Operation op) {
		var inst = p.getAttribute(a);
		if (inst == null) return;
		if (on) inst.addOrUpdateTransientModifier(new AttributeModifier(id(name), v, op));
		else inst.removeModifier(id(name));
	}
}
