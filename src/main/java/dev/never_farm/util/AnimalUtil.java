package dev.never_farm.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AnimalUtil {
public static final String TAG_FED = "never_farm:fed";
	public static final String TAG_LAST_FED_TIME = "never_farm:last_fed_time";
	public static final String TAG_HOME_X = "never_farm:home_x";
	public static final String TAG_HOME_Y = "never_farm:home_y";
	public static final String TAG_HOME_Z = "never_farm:home_z";
	public static final String TAG_LOCKED = "never_farm:locked";
	public static final String TAG_STARVING = "never_farm:starving";

	private AnimalUtil() {
	}
	

	public static boolean isCollectable(Animal animal) {
		return isCollectable(animal, true);
	}

	public static boolean isCollectable(Animal animal, boolean requireFed) {
		if (animal.isBaby() || animal.hasCustomName()) {
			return false;
		}
		if (requireFed && !isFed(animal)) {
			return false;
		}
		if (animal instanceof net.minecraft.world.entity.animal.Sheep sheep && sheep.isSheared()) {
			return false;
		}
		return true;
	}

	public static boolean isFed(Animal animal) {
		return animal.getPersistentData().getBoolean(TAG_FED);
	}

	public static void markFed(Animal animal) {
		animal.getPersistentData().putBoolean(TAG_FED, true);
	}

	public static void markFedAt(Animal animal, long dayTime) {
		CompoundTag data = animal.getPersistentData();
		data.putBoolean(TAG_FED, true);
		data.putLong(TAG_LAST_FED_TIME, dayTime);
	}

	public static AABB scanBox(net.minecraft.core.BlockPos pos, int maxY) {
		var chunkPos = new net.minecraft.world.level.ChunkPos(pos);
		int minX = (chunkPos.x - 1) << 4;
		int minZ = (chunkPos.z - 1) << 4;
		int maxX = ((chunkPos.x + 1) << 4) + 15;
		int maxZ = ((chunkPos.z + 1) << 4) + 15;
		int top = Math.max(maxY, pos.getY() + 1);
		return new AABB(minX, pos.getY(), minZ, maxX + 1.0D, top, maxZ + 1.0D);
	}

	private static Map<EntityType<?>, Set<Item>> foodsByType = new HashMap<>();
	private static Set<Item> allFoods;

	private static Set<Item> foodsFor(Level level, EntityType<?> type) {
		return foodsByType.computeIfAbsent(type, t -> {
			Set<Item> foods = new HashSet<>();
			try {
				Entity probe = t.create(level);
				if (!(probe instanceof Animal animal)) {
					return foods;
				}
				for (Item item : BuiltInRegistries.ITEM) {
					ItemStack stack = new ItemStack(item);
					if (animal.isFood(stack)) {
						foods.add(item);
					}
				}
				probe.discard();
			} catch (Exception ignored) {
			}
			return foods;
		});
	}

	private static void ensureAllFoods(Level level) {
		if (allFoods != null) {
			return;
		}
		Set<Item> all = new HashSet<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			all.addAll(foodsFor(level, type));
		}
		allFoods = all;
	}

	public static boolean isAnimalFood(Level level, ItemStack stack) {
		ensureAllFoods(level);
		return allFoods.contains(stack.getItem());
	}

	public static boolean isFoodFor(Level level, EntityType<?> type, ItemStack stack) {
		Set<Item> foods = foodsFor(level, type);
		return foods.contains(stack.getItem());
	}

	public static EntityType<?> parseEntityType(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		if (rl == null) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.get(rl);
	}
}
