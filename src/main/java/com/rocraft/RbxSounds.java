package com.rocraft;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Roblox sounds. The .ogg files come from Roblox into the local "Rocraft Roblox Assets" pack, never the jar. */
public final class RbxSounds {
	public static final SoundEvent SLASH = reg("linked_sword.slash"), LUNGE = reg("linked_sword.lunge"),
		UNSHEATH = reg("linked_sword.unsheath"), OOF = reg("oof"),
		GRAVITY_COIL = reg("gravity_coil.coil"), SPEED_COIL = reg("speed_coil.coil"),
		COLA_OPEN = reg("bloxy_cola.open"), COLA_DRINK = reg("bloxy_cola.drink");

	/** Gear sounds downloaded from each tool (pack: sounds/<gear>/<name>.ogg), looked up by "<gear>.<name>". */
	static final String[] EXTRA = {"taco.opensound", "taco.eatsound", "burger.opensound", "burger.drinksound", "chicken.opensound",
		"chicken.drinksound", "pizza.opensound", "pizza.drinksound", "teddy.say1", "teddy.say2", "teddy.say3", "teddy.say4", "teddy.say5",
		// classic rbxasset sounds, from the copies Roblox keeps in its library
		"bomb.tick", "bomb.explode", "rocket_launcher.swoosh", "rocket_launcher.boom", "superball.boing", "slingshot.sling", "trowel.build", "paintball_gun.fire",
		"regen_coil.coil", "dual_gravity_coil.coil", "green_balloon.pop", "hoverboard.drop", "hoverboard.ollie", "hoverboard.land", "hoverboard.stop",
		"protest_sign.angrysound1", "protest_sign.angrysound2", "protest_sign.angrysound3"};
	private static final java.util.Map<String, SoundEvent> BY_NAME = new java.util.HashMap<>();
	static { for (String n : EXTRA) BY_NAME.put(n, reg(n)); }

	public static SoundEvent get(String name) { return BY_NAME.get(name); }

	private static SoundEvent reg(String name) {
		var id = Rocraft.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void play(Entity at, SoundEvent s) {
		if (s == null) return;
		at.level().playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundSource.PLAYERS, 1f, 1f);
	}

	static void init() {} // forces class load during mod init
}
