package dev.never_farm.gametest;

import dev.never_farm.Never_farm;
import dev.never_farm.block.WorkBlock;
import dev.never_farm.blockentity.WorkBlockEntity;
import dev.never_farm.handler.AutoBreedHandler;
import dev.never_farm.util.AnimalUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Never_farm.MODID)
public class WorkBlockGameTests {

	private static final int POP_BUDGET = 64;

	private static WorkBlockEntity work(GameTestHelper helper, int x, int y, int z) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, y, z)));
		if (!(be instanceof WorkBlockEntity w)) {
			throw new IllegalStateException("no WorkBlockEntity at " + x + "," + y + "," + z);
		}
		return w;
	}

	private static Cow cow(GameTestHelper helper, int x, int y, int z, boolean fed) {
		Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(x, y, z));
		if (fed) {
			AnimalUtil.markFed(cow);
		}
		return cow;
	}

	private static List<Cow> cowsAround(GameTestHelper helper, WorkBlockEntity be) {
		return helper.getLevel().getEntitiesOfClass(Cow.class, be.scanArea());
	}

	private static void tickReleases(GameTestHelper helper, WorkBlockEntity work) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState state = helper.getLevel().getBlockState(pos);
		int used = 0;
		while (work.pendingCount() > 0 && used < POP_BUDGET) {
			WorkBlockEntity.tick(helper.getLevel(), pos, state, work);
			used++;
		}
	}

	@GameTest(template = "work_block_test")
	public static void collectBindsAndRespectsExemption(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);

		cow(helper, 0, 1, 1, true);

		cow(helper, 2, 1, 1, false);

		Cow baby = cow(helper, 1, 1, 0, true);
		baby.setAge(-24000);

		Cow named = cow(helper, 1, 2, 1, true);
		named.setCustomName(Component.literal("Bessy"));

		int collected = work.collectAll();
		if (collected != 1) {
			helper.fail("expected exactly 1 collected, got " + collected);
		}
		if (work.storedCount() != 1) {
			helper.fail("stored should be 1, got " + work.storedCount());
		}
		if (work.boundType() != EntityType.COW) {
			helper.fail("block should be bound to cow");
		}
		if (!work.isTypeLocked()) {
			helper.fail("first absorb should auto-lock the type");
		}

		Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(2, 2, 1));
		AnimalUtil.markFed(sheep);
		int c2 = work.collectAll();
		if (c2 != 0) {
			helper.fail("sheep must not be collected into a cow-bound block");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void manualCollectDoesNotRequireFed(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);

		cow(helper, 0, 1, 1, false);
		cow(helper, 2, 1, 1, false);

		int manual = work.collectAllManual();
		if (manual != 2) {
			helper.fail("manual collect should absorb unfed cows, got " + manual);
		}
		if (work.storedCount() != 2) {
			helper.fail("stored should be 2 after manual collect, got " + work.storedCount());
		}

		cow(helper, 0, 1, 2, false);
		cow(helper, 2, 1, 2, false);
		int auto = work.collectAll();
		if (auto != 0) {
			helper.fail("auto collect must NOT absorb unfed cows, got " + auto);
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void balanceByThreshold(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		work.setThreshold(2);
		for (int i = 0; i < 3; i++) {
			cow(helper, 0, 1, i, true);
		}
		work.collectAllAtSunset();
		if (work.storedCount() != 3) {
			helper.fail("sunset collect should absorb all 3, got " + work.storedCount());
		}

		work.releaseAtSunrise();
		tickReleases(helper, work);
		int outside = cowsAround(helper, work).size();
		if (outside != 2) {
			helper.fail("sunrise release should release 2 (threshold), got " + outside);
		}
		if (work.storedCount() != 1) {
			helper.fail("after sunrise release stored should be 1, got " + work.storedCount());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void releaseSpawnsAboveBlock(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		cow(helper, 0, 1, 1, true);
		cow(helper, 2, 1, 1, true);
		work.collectAll();
		if (work.storedCount() != 2) {
			helper.fail("collect failed");
		}
		int released = work.releaseSome(8);
		if (released != 2) {
			helper.fail("released should be 2, got " + released);
		}
		tickReleases(helper, work);
		BlockPos above = helper.absolutePos(new BlockPos(1, 2, 1));
		AABB box = new AABB(above).inflate(1.0D);
		for (Cow c : helper.getLevel().getEntitiesOfClass(Cow.class, box)) {
			double dx = Math.abs(c.getX() - (above.getX() + 0.5D));
			double dz = Math.abs(c.getZ() - (above.getZ() + 0.5D));
			if (dx < 0.5D && dz < 0.5D) {
				helper.succeed();
				return;
			}
		}
		helper.fail("no cow spawned directly above the block");
	}

	@GameTest(template = "work_block_test")
	public static void yAxisRestriction(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);

		cow(helper, 1, 0, 1, true);

		cow(helper, 1, 2, 1, true);
		int collected = work.collectAll();
		if (collected != 1) {
			StringBuilder sb = new StringBuilder();
			sb.append("only the animal at same/higher Y should be collected, got ").append(collected)
				.append("; block=").append(helper.absolutePos(new BlockPos(1, 1, 1)))
				.append("; area=").append(work.scanArea());
			for (Cow c : helper.getLevel().getEntitiesOfClass(Cow.class, work.scanArea())) {
				sb.append("; cow@").append(c.blockPosition());
			}
			helper.fail(sb.toString());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void shearedSheepExemption(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(0, 1, 1));
		AnimalUtil.markFed(sheep);
		sheep.setSheared(true);
		int c1 = work.collectAll();
		if (c1 != 0) {
			helper.fail("sheared sheep must not be collected, got " + c1);
		}
		sheep.setSheared(false);
		int c2 = work.collectAll();
		if (c2 != 1) {
			helper.fail("regrown sheep should be collected, got " + c2);
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void breedingControlLocks(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Cow a = cow(helper, 0, 1, 0, true);
		Cow b = cow(helper, 2, 1, 0, true);
		Cow c = cow(helper, 1, 2, 0, true);
		work.forceAbsorb(new WorkBlockEntity.AgeableMobLike(a, b, c));
		if (work.storedCount() != 3) {
			helper.fail("all three should be force-absorbed, got " + work.storedCount());
		}

		int released = work.releaseSome(8);
		if (released != 0) {
			helper.fail("locked animals must not be released manually, got " + released);
		}

		work.setThreshold(2);
		work.releaseAtSunrise();
		if (work.countLocked() != 0) {
			helper.fail("locked marks should be cleared at sunrise, locked=" + work.countLocked());
		}
		tickReleases(helper, work);
		if (work.storedCount() != 1) {
			helper.fail("after sunrise stored should be 1, got " + work.storedCount());
		}
		int released2 = work.releaseSome(8);
		if (released2 != 1) {
			helper.fail("animals should now be releasable manually, got " + released2);
		}
		tickReleases(helper, work);
		if (cowsAround(helper, work).size() != 3) {
			helper.fail("all 3 cows should be outside, got " + cowsAround(helper, work).size());
		}
		for (Entity e : new ArrayList<>(cowsAround(helper, work))) {
			e.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void feedDepositOnRightClick(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		if (!AnimalUtil.isAnimalFood(helper.getLevel(), new ItemStack(Items.WHEAT_SEEDS))) {
			helper.fail("wheat seeds should be recognized as animal food");
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 16));
		work.handleRightClick(player, InteractionHand.MAIN_HAND);
		if (work.feedCount() != 16) {
			helper.fail("wheat seeds should be deposited, feed=" + work.feedCount());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void starvationMarksAndDebuff(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		for (int i = 0; i < 2; i++) {
			cow(helper, 0, 1, i, true);
		}
		work.collectAllAtSunset();
		if (work.storedCount() != 2) {
			helper.fail("expected 2 stored, got " + work.storedCount());
		}
		work.dailySettle(helper.getLevel());
		if (work.storedCount() != 2) {
			helper.fail("starving animals must not be removed, got " + work.storedCount());
		}
		work.releaseAtSunrise();
		tickReleases(helper, work);
		List<Cow> around = cowsAround(helper, work);
		for (Cow c : around) {
			if (!c.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
				helper.fail("released starving animal should have wither");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void feedCapabilityWorks(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		IItemHandler cap = helper.getLevel().getCapability(
			net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
			pos, helper.getLevel().getBlockState(pos), helper.getLevel().getBlockEntity(pos), null);
		if (cap == null) {
			helper.fail("item handler capability should be present when DLC enabled");
		}
		ItemStack rest = ItemHandlerHelper.insertItem(cap, new ItemStack(Items.WHEAT_SEEDS, 8), false);
		if (!rest.isEmpty()) {
			helper.fail("feed insert via capability failed");
		}
		if (work(helper, 1, 1, 1).feedCount() != 8) {
			helper.fail("feed count should be 8 after capability insert");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void redstoneAutoBreed(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		for (int i = 0; i < 2; i++) {
			cow(helper, 0, 1, i, true);
		}
		work.collectAllAtSunset();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 16));
		work.handleRightClick(player, InteractionHand.MAIN_HAND);
		if (work.feedCount() != 16) {
			helper.fail("feed should be 16, got " + work.feedCount());
		}
		AutoBreedHandler.enabled = true;
		helper.setBlock(2, 1, 1, Blocks.REDSTONE_BLOCK);
		work.tryAutoBreed();
		int babies = 0;
		for (Cow c : cowsAround(helper, work)) {
			if (c.isBaby()) {
				babies++;
			}
		}
		if (babies < 4 || babies > 6) {
			helper.fail("redstone auto breed should spawn 4~6 babies, got " + babies);
		}
		if (work.feedCount() != 16 - babies) {
			helper.fail("feed should drop by babies count, got " + work.feedCount());
		}
		helper.setBlock(2, 1, 1, Blocks.AIR);
		work.tryAutoBreed();
		if (work.feedCount() != 16 - babies) {
			helper.fail("no redstone signal must not breed, feed=" + work.feedCount());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void vanillaHopperPath(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		net.minecraft.world.Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), pos);
		if (!(container instanceof WorkBlockEntity)) {
			helper.fail("vanilla hopper should see the work block as a container");
		}
		ItemStack rest = HopperBlockEntity.addItem(
			null, container, new ItemStack(Items.WHEAT_SEEDS, 4), Direction.DOWN);
		if (!rest.isEmpty()) {
			helper.fail("hopper insert should accept all feed, rest=" + rest.getCount());
		}
		if (work(helper, 1, 1, 1).feedCount() != 4) {
			helper.fail("feed count should be 4 after hopper insert");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void hopperWorksWithoutDlc(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		dev.never_farm.Config.dsEnable = false;
		try {
			net.minecraft.world.Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), pos);
			if (!(container instanceof WorkBlockEntity)) {
				helper.fail("hopper should see the work block as a container without DLC");
			}
			ItemStack rest = HopperBlockEntity.addItem(
				null, container, new ItemStack(Items.WHEAT_SEEDS, 3), Direction.DOWN);
			if (!rest.isEmpty()) {
				helper.fail("hopper insert should work without DLC");
			}
			if (work(helper, 1, 1, 1).feedCount() != 3) {
				helper.fail("feed should be 3 without DLC");
			}
			IItemHandler cap = helper.getLevel().getCapability(
				net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
				pos, helper.getLevel().getBlockState(pos), helper.getLevel().getBlockEntity(pos), null);
			if (cap == null) {
				helper.fail("item handler capability should be present without DLC");
			}
		} finally {
			dev.never_farm.Config.dsEnable = true;
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void nonFeedRejected(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		WorkBlockEntity work = work(helper, 1, 1, 1);
		net.minecraft.world.Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), pos);
		ItemStack dirt = new ItemStack(Items.DIRT, 4);
		ItemStack rest = HopperBlockEntity.addItem(
			null, container, dirt.copy(), Direction.DOWN);
		if (rest.getCount() != 4) {
			helper.fail("hopper must reject non-feed, rest=" + rest.getCount());
		}
		if (work.feedCount() != 0) {
			helper.fail("non-feed must not be stored, feed=" + work.feedCount());
		}
		IItemHandler cap = helper.getLevel().getCapability(
			net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
			pos, helper.getLevel().getBlockState(pos), helper.getLevel().getBlockEntity(pos), null);
		ItemStack rest2 = ItemHandlerHelper.insertItem(cap, dirt.copy(), false);
		if (rest2.getCount() != 4) {
			helper.fail("capability must reject non-feed, rest=" + rest2.getCount());
		}
		if (work.feedCount() != 0) {
			helper.fail("non-feed must not be stored via capability");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void unlockAutoMode(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COW_SPAWN_EGG));
		work.toggleTypeLock(p, p.getMainHandItem());
		if (!work.isTypeLocked() || work.boundType() != EntityType.COW) {
			helper.fail("spawn egg should bind and lock cow");
		}
		work.toggleTypeLock(p, ItemStack.EMPTY);
		if (work.isTypeLocked() || work.boundType() != null) {
			helper.fail("unlock should clear lock and bound type");
		}
		Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(0, 1, 1));
		AnimalUtil.markFed(sheep);
		Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
		AnimalUtil.markFed(pig);
		int c = work.collectAll();
		if (c != 2) {
			helper.fail("auto mode should collect both, got " + c);
		}
		if (work.boundType() != null || work.isTypeLocked()) {
			helper.fail("auto mode must stay unbound and unlocked");
		}
		Pig pig2 = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
		AnimalUtil.markFed(pig2);
		work.toggleTypeLock(p, ItemStack.EMPTY);
		if (work.boundType() != EntityType.PIG || !work.isTypeLocked()) {
			helper.fail("relock should bind nearest collectable (pig)");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void dropsPreserveData(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		cow(helper, 0, 1, 1, true);
		if (work.collectAll() != 1) {
			helper.fail("collect failed");
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState state = helper.getLevel().getBlockState(pos);
		List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
			state, (ServerLevel) helper.getLevel(), pos, work, null, ItemStack.EMPTY);
		if (drops.size() != 1) {
			helper.fail("expected 1 drop, got " + drops.size());
		}
		ItemStack stack = drops.get(0);
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
		if (data == null || data.isEmpty()) {
			helper.fail("drop must carry block entity data");
		}
		BlockEntity loaded = BlockEntity.loadStatic(
			pos, state, data.copyTag(), helper.getLevel().registryAccess());
		if (!(loaded instanceof WorkBlockEntity w) || w.storedCount() != 1) {
			helper.fail("placed block must restore stored animals");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void feedCappedAtOneStack(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);

		ItemStack first = new ItemStack(Items.WHEAT_SEEDS, 64);
		ItemStack rest1 = ItemHandlerHelper.insertItem(work.feedInventory(), first, false);
		if (!rest1.isEmpty()) {
			helper.fail("first stack should fully insert, rest=" + rest1.getCount());
		}
		if (work.feedCount() != 64) {
			helper.fail("feed should be 64, got " + work.feedCount());
		}

		ItemStack second = new ItemStack(Items.WHEAT_SEEDS, 64);
		ItemStack rest2 = ItemHandlerHelper.insertItem(work.feedInventory(), second, false);
		if (rest2.getCount() != 64) {
			helper.fail("second stack must be rejected entirely, rest=" + rest2.getCount());
		}
		if (work.feedCount() != 64) {
			helper.fail("feed must stay capped at 64, got " + work.feedCount());
		}

		ItemStack taken = work.feedInventory().extractItem(0, 4, false);
		if (taken.getCount() != 4) {
			helper.fail("extract 4 failed, got " + taken.getCount());
		}
		ItemStack third = new ItemStack(Items.WHEAT_SEEDS, 64);
		ItemStack rest3 = ItemHandlerHelper.insertItem(work.feedInventory(), third, false);
		if (rest3.getCount() != 60) {
			helper.fail("partial fill: expected 60 back, got " + rest3.getCount());
		}
		if (work.feedCount() != 64) {
			helper.fail("feed must be back to 64, got " + work.feedCount());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void fullnessRestoredOnPlace(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		cow(helper, 0, 1, 1, true);
		cow(helper, 2, 1, 1, true);
		if (work.collectAll() != 2) {
			helper.fail("collect failed");
		}
		int fullness = work.fullness();
		if (fullness < 1) {
			helper.fail("expected fullness >= 1, got " + fullness);
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		net.minecraft.world.item.component.CustomData data = net.minecraft.world.level.block.Block.getDrops(
			helper.getLevel().getBlockState(pos), (ServerLevel) helper.getLevel(),
			pos, work, null, ItemStack.EMPTY).get(0)
			.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
		if (data == null || data.isEmpty()) {
			helper.fail("drop must carry block entity data");
		}

		helper.setBlock(1, 1, 1, Blocks.AIR);
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity fresh = work(helper, 1, 1, 1);
		fresh.loadWithComponents(data.copyTag(), helper.getLevel().registryAccess());

		if (fresh.storedCount() != 2) {
			helper.fail("animals not restored, stored=" + fresh.storedCount());
		}
		int stateFullness = helper.getLevel().getBlockState(pos).getValue(WorkBlock.FULLNESS);
		if (stateFullness != fresh.fullness()) {
			helper.fail("FULLNESS not synced after reload: state=" + stateFullness + " expected=" + fresh.fullness());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void cannotUnlockWithStoredAnimals(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		cow(helper, 0, 1, 1, true);
		int c = work.collectAll();
		if (c < 1) {
			helper.fail("collect failed, got " + c);
		}
		if (work.storedCount() < 1) {
			helper.fail("no animals stored");
		}
		if (!work.isTypeLocked()) {
			helper.fail("should be locked after first absorb");
		}
		Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		work.toggleTypeLock(p, ItemStack.EMPTY);
		if (!work.isTypeLocked()) {
			helper.fail("unlock must be rejected while animals are stored");
		}
		if (work.boundType() != EntityType.COW) {
			helper.fail("bound type must be preserved");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void autoBreedStopsAtTwelveBabies(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Chicken chick = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(0, 1, 0));
		AnimalUtil.markFed(chick);
		if (work.collectAll() != 1) {
			helper.fail("bind chicken failed");
		}
		if (work.boundType() != EntityType.CHICKEN) {
			helper.fail("not bound to chicken");
		}
		Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 64));
		work.handleRightClick(p, InteractionHand.MAIN_HAND);
		if (work.feedCount() != 64) {
			helper.fail("feed not stored, got " + work.feedCount());
		}
		for (int i = 0; i < 13; i++) {
			Chicken b = helper.spawnWithNoFreeWill(
				EntityType.CHICKEN, new BlockPos(i % 3, 1, i / 3));
			b.setAge(-24000);
		}
		AutoBreedHandler.enabled = true;
		helper.setBlock(2, 1, 1, Blocks.REDSTONE_BLOCK);
		int before = 0;
		for (Chicken c : helper.getLevel().getEntitiesOfClass(
			Chicken.class, work.scanArea())) {
			if (c.isBaby()) {
				before++;
			}
		}
		work.tryAutoBreed();
		int after = 0;
		for (Chicken c : helper.getLevel().getEntitiesOfClass(
			Chicken.class, work.scanArea())) {
			if (c.isBaby()) {
				after++;
			}
		}
		if (after != before) {
			helper.fail("must not breed with >12 babies, before=" + before + " after=" + after);
		}
		if (work.feedCount() != 64) {
			helper.fail("feed must not be consumed, got " + work.feedCount());
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void breedingEventAbsorbsParentsAndChild(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Chicken a = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(0, 1, 0));
		Chicken b = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(2, 1, 0));
		Chicken child = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(1, 2, 0));
		AnimalUtil.markFed(a);
		AnimalUtil.markFed(b);
		NeoForge.EVENT_BUS.post(new BabyEntitySpawnEvent(a, b, child));
		if (work.storedCount() != 3) {
			helper.fail("breeding event must absorb 2 parents + 1 child, got " + work.storedCount());
		}
		if (work.boundType() != EntityType.CHICKEN || !work.isTypeLocked()) {
			helper.fail("block should be bound and locked to chicken after forced absorb");
		}
		helper.succeed();
	}

	@GameTest(template = "work_block_test")
	public static void releasedAnimalsLeaveTheHerd(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Never_farm.WORK_BLOCK.get());
		WorkBlockEntity work = work(helper, 1, 1, 1);
		Chicken a = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(0, 1, 0));
		Chicken b = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(2, 1, 0));
		Chicken child = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(1, 2, 0));
		AnimalUtil.markFed(a);
		AnimalUtil.markFed(b);
		NeoForge.EVENT_BUS.post(new BabyEntitySpawnEvent(a, b, child));
		if (work.storedCount() != 3) {
			helper.fail("breeding event must absorb 2 parents + 1 child, got " + work.storedCount());
		}

		work.setThreshold(2);
		work.releaseAtSunrise();
		if (work.countLocked() != 0) {
			helper.fail("locked marks must clear at sunrise, locked=" + work.countLocked());
		}
		tickReleases(helper, work);
		if (work.storedCount() != 1) {
			helper.fail("stored should drop to 1 after sunrise release, got " + work.storedCount());
		}
		int outside = helper.getLevel().getEntitiesOfClass(
			Chicken.class, work.scanArea(), c -> !c.isRemoved()).size();
		if (outside != 2) {
			helper.fail("expected 2 chickens outside after release, got " + outside);
		}

		work.collectAllAtSunset();
		if (work.storedCount() != 3) {
			helper.fail("sunset must re-absorb released animals (managed kept), got " + work.storedCount());
		}
		if (helper.getLevel().getEntitiesOfClass(
			Chicken.class, work.scanArea(), c -> !c.isRemoved()).size() != 0) {
			helper.fail("released animals must be back inside after sunset");
		}

		helper.succeed();
	}
}