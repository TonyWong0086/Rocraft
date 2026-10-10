package com.rocraft.tools;

import com.rocraft.RbxSounds;
import com.rocraft.Rocraft;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A famous Robloxian as a mob, wearing their real Roblox avatar (drawn client side from their account). Neutral like
 * a Roblox NPC: wanders and looks at players, and fights back with the Linked Sword when hit. 100 Roblox health
 * (20 Minecraft), WalkSpeed 16, the sword's slash damage (10), "oof" when knocked out.
 */
public final class Robloxian extends PathfinderMob {
	/** Roblox usernames: each gets its own mob type (rocraft:<lowercase>) and spawn egg. */
	public static final List<String> USERS = List.of("Shedletsky", "Stickmasterluke", "Roblox", "builderman", "Nikilis", "clockwork",
		"mrflimflam", "KreekCraft", "DenisDaily", "Linkmon99", "PGHLego1945");
	public static final Map<EntityType<Robloxian>, String> TYPES = new LinkedHashMap<>();
	public static final List<Item> EGGS = new java.util.ArrayList<>();

	public final String username;

	public Robloxian(EntityType<? extends Robloxian> type, Level level, String username) {
		super(type, level);
		this.username = username;
	}

	static void register() {
		for (String user : USERS) {
			String id = user.toLowerCase(java.util.Locale.ROOT);
			var key = ResourceKey.create(Registries.ENTITY_TYPE, Rocraft.id(id));
			EntityType<Robloxian> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<Robloxian>of((t, l) -> new Robloxian(t, l, user), MobCategory.CREATURE).sized(0.6f, 1.8f).clientTrackingRange(10).build(key));
			TYPES.put(type, user);
			FabricDefaultAttributeRegistry.register(type, attributes());
			SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
			BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, type, 1, 1, 1); // rare: one at a time
			var eggKey = ResourceKey.create(Registries.ITEM, Rocraft.id(id + "_spawn_egg"));
			EGGS.add(Registry.register(BuiltInRegistries.ITEM, eggKey, new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(type))));
		}
	}

	static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 100 * 0.2)
			.add(Attributes.MOVEMENT_SPEED, 0.32) // mobs move at about speed^2: 0.32 ~ WalkSpeed 16
			.add(Attributes.ATTACK_DAMAGE, 10 * 0.2) // Linked Sword slash
			.add(Attributes.FOLLOW_RANGE, 32);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Tools.LINKED_SWORD));
		setDropChance(EquipmentSlot.MAINHAND, 0);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		RbxSounds.play(this, RbxSounds.SLASH);
		return hit;
	}

	@Override protected SoundEvent getDeathSound() { return RbxSounds.OOF; }
	@Override protected SoundEvent getHurtSound(DamageSource src) { return null; } // Roblox characters are silent when hit
	@Override protected SoundEvent getAmbientSound() { return null; }
	@Override public boolean removeWhenFarAway(double distSqr) { return false; }
}
