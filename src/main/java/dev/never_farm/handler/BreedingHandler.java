package dev.never_farm.handler;

import dev.never_farm.Config;
import dev.never_farm.Never_farm;
import dev.never_farm.blockentity.WorkBlockEntity;
import dev.never_farm.util.AnimalUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;



@EventBusSubscriber(modid = Never_farm.MODID)
public class BreedingHandler {

	@SubscribeEvent
	public static void onBabySpawn(BabyEntitySpawnEvent event) {
		if (!Config.enforceBreedingControl || event.isCanceled()) {
			return;
		}
		Mob parentA = event.getParentA();
		Mob parentB = event.getParentB();
		if (parentA == null || parentB == null) {
			return;
		}
		if (parentA.getType() != parentB.getType()) {
			return;
		}
		Level level = parentA.level();
		if (level.isClientSide) {
			return;
		}
		EntityType<?> type = parentA.getType();
		WorkBlockEntity be = WorkBlockEntity.findNearestFor(level, parentA.blockPosition(), type);
		if (be == null) {
			return;
		}
		Animal child = event.getChild() instanceof Animal a ? a : null;
		be.forceAbsorb(new WorkBlockEntity.AgeableMobLike(parentA, parentB, child));
	}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getSide() != LogicalSide.SERVER || event.isCanceled()) {
			return;
		}
		Entity target = event.getTarget();
		if (!(target instanceof Animal animal)) {
			return;
		}
		Player player = event.getEntity();
		ItemStack held = player.getItemInHand(event.getHand());
		if (held.isEmpty() || !animal.isFood(held)) {
			return;
		}
		

		AnimalUtil.markFedAt(animal, animal.level().getDayTime());
	}
}
