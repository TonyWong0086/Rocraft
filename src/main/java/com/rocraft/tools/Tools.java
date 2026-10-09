package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.Rocraft;
import com.rocraft.RocraftConfig;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/**
 * Classic Roblox gear. GEAR maps each item to where its Roblox Tool lives: "asset:<id>" (downloaded with the Open
 * Cloud key) or "legacy:<Name>" (exported once from the user's own Roblox Studio place into config/rocraft/legacy).
 */
public final class Tools {
	public static final Map<String, String> GEAR = new LinkedHashMap<>();
	static {
		GEAR.put("linked_sword", "asset:125013769");
		GEAR.put("rocket_launcher", "legacy:RocketLauncher");
		GEAR.put("superball", "legacy:Superball");
		GEAR.put("slingshot", "legacy:Slingshot");
		GEAR.put("bomb", "legacy:Timebomb");
		GEAR.put("trowel", "legacy:Trowel");
		GEAR.put("gravity_coil", "asset:16688968");
		GEAR.put("speed_coil", "asset:99119158");
		GEAR.put("bloxy_cola", "asset:10472779");
		GEAR.put("taco", "legacy:Taco");
		GEAR.put("burger", "legacy:Burger");
		GEAR.put("chicken", "legacy:Chicken");
		GEAR.put("pizza", "legacy:Pizza");
		GEAR.put("teddy", "legacy:Teddy");
	}
	/** StarterPack (hotbar order); the rest are in the Rocraft creative tab. */
	static final List<String> STARTER = List.of("linked_sword", "rocket_launcher", "superball", "slingshot", "bomb", "trowel", "bloxy_cola");

	public static Item LINKED_SWORD, ROCKET_LAUNCHER, SUPERBALL, SLINGSHOT, BOMB, TROWEL, GRAVITY_COIL, SPEED_COIL,
		BLOXY_COLA, TACO, BURGER, CHICKEN, PIZZA, TEDDY;
	public static EntityType<BombEntity> BOMB_ENTITY;
	public static EntityType<Projectile> PROJECTILE;
	public static volatile long clientLungeAt; // local player's last lunge (client), drives the toollunge animation
	/** Local player's current "using" grip (eating, drinking, hugging), client side. */
	public static volatile Item clientUseItem;
	public static volatile long clientUseUntil;

	static void clientUse(Item item, long ms) { clientUseItem = item; clientUseUntil = System.currentTimeMillis() + ms; }

	/** Where each player's mouse points in the world (Roblox's MouseLoc), sent by the client. */
	static final Map<UUID, Vec3> MOUSE = new HashMap<>();
	private static final Map<UUID, Item> HELD = new HashMap<>();
	private static final Map<UUID, Long> LUNGED_AT = new HashMap<>();
	private static final List<Object[]> LATER = new ArrayList<>(); // {tick, Runnable}
	private static long tick;

	public static void register() {
		var swordAttrs = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 1.0, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND) // 1 + 1 = 2 (Roblox slash 10)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 16.0, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND) // no charge-up, Roblox clicks
			.build();
		LINKED_SWORD = item("linked_sword", p -> new LinkedSword(p.attributes(swordAttrs)));
		ROCKET_LAUNCHER = item("rocket_launcher", p -> new Launcher(p, Projectile.ROCKET));
		SUPERBALL = item("superball", p -> new Launcher(p, Projectile.SUPERBALL));
		SLINGSHOT = item("slingshot", p -> new Launcher(p, Projectile.PELLET));
		BOMB = item("bomb", BombItem::new);
		TROWEL = item("trowel", Trowel::new);
		GRAVITY_COIL = item("gravity_coil", Item::new);
		SPEED_COIL = item("speed_coil", Item::new);
		BLOXY_COLA = item("bloxy_cola", BloxyCola::new);
		TACO = item("taco", p -> new Food(p, "taco.eatsound"));
		BURGER = item("burger", p -> new Food(p, "burger.drinksound"));
		CHICKEN = item("chicken", p -> new Food(p, "chicken.drinksound"));
		PIZZA = item("pizza", p -> new Food(p, "pizza.drinksound"));
		TEDDY = item("teddy", Teddy::new);

		var bombKey = ResourceKey.create(Registries.ENTITY_TYPE, Rocraft.id("bomb"));
		BOMB_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, bombKey,
			EntityType.Builder.<BombEntity>of(BombEntity::new, MobCategory.MISC).sized(0.56f, 0.56f).clientTrackingRange(8).build(bombKey));
		var projKey = ResourceKey.create(Registries.ENTITY_TYPE, Rocraft.id("projectile"));
		PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, projKey,
			EntityType.Builder.<Projectile>of(Projectile::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(10).updateInterval(1).build(projKey));

		// Rocraft page in the creative inventory: every classic tool
		var tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Rocraft.id("gear"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricCreativeModeTab.builder()
			.title(Component.literal("Rocraft"))
			.icon(() -> new ItemStack(LINKED_SWORD))
			.displayItems((params, out) -> { for (String n : GEAR.keySet()) out.accept(BuiltInRegistries.ITEM.getValue(Rocraft.id(n))); })
			.build());

		MouseTarget.register();
		ServerPlayerEvents.JOIN.register(Tools::starterPack);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> starterPack(newP));
		ServerTickEvents.END_SERVER_TICK.register(Tools::tick);
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		var key = ResourceKey.create(Registries.ITEM, Rocraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key).stacksTo(1)));
	}

	/** Mouse hit position for a player, or a point 100 studs ahead of their eyes if the client hasn't said. */
	static Vec3 mouse(Player p) {
		Vec3 m = MOUSE.get(p.getUUID());
		return m != null ? m : p.getEyePosition().add(p.getLookAngle().scale(28));
	}

	/** Run something on the server thread `ticks` from now. */
	static void later(int ticks, Runnable r) { LATER.add(new Object[]{tick + ticks, r}); }

	private static void tick(MinecraftServer server) {
		tick++;
		for (var it = LATER.iterator(); it.hasNext(); ) {
			Object[] e = it.next();
			if ((long) e[0] <= tick) { it.remove(); ((Runnable) e[1]).run(); }
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Item now = p.getMainHandItem().getItem();
			if (HELD.get(p.getUUID()) != now) { // Tool.Equipped
				SoundEvent s = now == LINKED_SWORD ? RbxSounds.UNSHEATH : now == GRAVITY_COIL ? RbxSounds.GRAVITY_COIL
					: now == SPEED_COIL ? RbxSounds.SPEED_COIL : now == BLOXY_COLA ? RbxSounds.COLA_OPEN
					: now instanceof Food f ? RbxSounds.get(f.openSound()) : null;
				if (s != null) RbxSounds.play(p, s);
				HELD.put(p.getUUID(), now);
			}
			// Gravity Coil: BodyForce cancels 75% of gravity (JumpHeightPercentage 0.25). Speed Coil: WalkSpeed 16 -> 32.
			coil(p, Attributes.GRAVITY, "gravity_coil", now == GRAVITY_COIL, -0.75);
			coil(p, Attributes.MOVEMENT_SPEED, "speed_coil", now == SPEED_COIL, 1.0);
		}
	}

	private static void coil(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, String name, boolean on, double v) {
		var inst = p.getAttribute(a);
		if (inst == null) return;
		if (on) inst.addOrUpdateTransientModifier(new AttributeModifier(Rocraft.id(name), v, Operation.ADD_MULTIPLIED_TOTAL));
		else inst.removeModifier(Rocraft.id(name));
	}

	static void lunged(Player p) { LUNGED_AT.put(p.getUUID(), p.level().getGameTime()); }

	/** The right-click lunge also sends a swing; don't play Slash over Lunge. */
	public static boolean justLunged(Player p) { return p.level().getGameTime() - LUNGED_AT.getOrDefault(p.getUUID(), -100L) < 2; }

	static void starterPack(ServerPlayer p) {
		if (!RocraftConfig.INSTANCE.starterPack) return;
		for (String name : STARTER) {
			Item it = BuiltInRegistries.ITEM.getValue(Rocraft.id(name));
			if (!p.getInventory().contains(s -> s.is(it))) p.getInventory().add(new ItemStack(it));
		}
	}
}
