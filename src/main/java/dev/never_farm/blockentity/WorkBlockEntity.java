package dev.never_farm.blockentity;

import dev.never_farm.Config;
import dev.never_farm.Never_farm;
import dev.never_farm.block.WorkBlock;
import dev.never_farm.handler.AIRestriction;
import dev.never_farm.handler.AutoBreedHandler;
import dev.never_farm.handler.KeepLoadedHandler;
import dev.never_farm.util.AnimalUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WorkBlockEntity extends BlockEntity implements Container, WorldlyContainer {

	public static final int FULLNESS_LEVELS = 4;
	public static final int MAX_FEED_PER_BLOCK = 64;

	private static final int POP_INTERVAL = 10;
	private static final TicketType<ChunkPos> WORK_BLOCK_TICKET =
		TicketType.create("never_farm_work", (a, b) -> 0);
	private static final Map<ResourceKey<Level>, Set<WorkBlockEntity>> ACTIVE = new HashMap<>();

	private final List<CompoundTag> stored = new ArrayList<>();
	private final List<CompoundTag> pendingSpawns = new ArrayList<>();
	private final ItemStackHandler feedInventory = new FeedHandler(16);

	private EntityType<?> boundType;
	private boolean typeLocked;
	private boolean everBound;
	private int threshold = 4;
	private int popTimer;

	private long lastShiftTick = -1;
	private int shiftCount;

	private int clientStored;
	private int clientLocked;
	private int clientFeed;
	private int clientThreshold = 4;
	private boolean clientTypeLocked;
	private EntityType<?> clientBoundType;

	public WorkBlockEntity(BlockPos pos, BlockState state) {
		super(Never_farm.WORK_BLOCK_ENTITY.get(), pos, state);
	}

	public static Map<ResourceKey<Level>, Set<WorkBlockEntity>> active() {
		return ACTIVE;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WorkBlockEntity be) {
		if (be.level == null || be.level.isClientSide || be.pendingSpawns.isEmpty()) {
			return;
		}
		if (be.popTimer > 0) {
			be.popTimer--;
			return;
		}
		be.popTimer = POP_INTERVAL;
		be.spawnOne(be.pendingSpawns.remove(0));
		be.setChanged();
		be.syncVisuals();
	}

	public static WorkBlockEntity findNearestFor(Level level, BlockPos pos, EntityType<?> type) {
		Set<WorkBlockEntity> set = ACTIVE.get(level.dimension());
		if (set == null || set.isEmpty()) {
			return null;
		}
		WorkBlockEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorkBlockEntity be : set) {
			if (be.isRemoved() || be.level != level || !be.canAbsorbType(type)) {
				continue;
			}
			double d = be.getBlockPos().distSqr(pos);
			if (d < bestDist) {
				bestDist = d;
				best = be;
			}
		}
		return best;
	}

	public record AgeableMobLike(Mob parentA, Mob parentB, Animal child) {
	}

	public int maxSlots() {
		return Config.maxSlots;
	}

	public int storedCount() {
		return isMirroring() ? clientStored : stored.size();
	}

	public int pendingCount() {
		return pendingSpawns.size();
	}

	public int countLocked() {
		if (isMirroring()) {
			return clientLocked;
		}
		int n = 0;
		for (CompoundTag t : stored) {
			if (t.getBoolean(AnimalUtil.TAG_LOCKED)) {
				n++;
			}
		}
		return n;
	}

	public EntityType<?> boundType() {
		return isMirroring() ? clientBoundType : boundType;
	}

	public boolean isTypeLocked() {
		return isMirroring() ? clientTypeLocked : typeLocked;
	}

	public int threshold() {
		return isMirroring() ? clientThreshold : threshold;
	}

	public void setThreshold(int value) {
		int v = value <= 2 ? 2 : (value >= 6 ? 6 : 4);
		if (v != threshold) {
			threshold = v;
			setChanged();
			syncVisuals();
		}
	}

	public ItemStackHandler feedInventory() {
		return feedInventory;
	}

	public int feedCount() {
		if (isMirroring()) {
			return clientFeed;
		}
		int c = 0;
		for (int i = 0; i < feedInventory.getSlots(); i++) {
			c += feedInventory.getStackInSlot(i).getCount();
		}
		return c;
	}

	private boolean isMirroring() {
		return level != null && level.isClientSide;
	}

	public boolean canAbsorbType(EntityType<?> type) {
		if (typeLocked) {
			return boundType == type;
		}
		return boundType == null || boundType == type;
	}

	public AABB scanArea() {
		int maxY = level != null ? level.getMaxBuildHeight() : 320;
		return AnimalUtil.scanBox(worldPosition, maxY);
	}

	public List<Animal> detectedCollectable() {
		return detectedCollectable(true);
	}

	public List<Animal> detectedCollectable(boolean requireFed) {
		if (level == null || level.isClientSide) {
			return List.of();
		}
		return level.getEntitiesOfClass(Animal.class, scanArea(),
			a -> a.getY() >= worldPosition.getY() && AnimalUtil.isCollectable(a, requireFed));
	}

	public int collectAll() {
		return collectAll(true);
	}

	public int collectAllManual() {
		return collectAll(false);
	}

	private int collectAll(boolean requireFed) {
		if (level == null || level.isClientSide) {
			return 0;
		}
		int collected = 0;
		List<Animal> targets = detectedCollectable(requireFed);
		if (boundType == null) {
			targets.sort(Comparator.comparingDouble(a ->
				a.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D)));
		}
		for (Animal animal : targets) {
			if (stored.size() >= maxSlots()) {
				break;
			}
			if (tryCollectOne(animal, false, requireFed)) {
				collected++;
			}
		}
		if (collected > 0) {
			setChanged();
			syncVisuals();
		}
		return collected;
	}

	public boolean tryCollectOne(Animal animal, boolean force) {
		return tryCollectOne(animal, force, true);
	}

	private boolean tryCollectOne(Animal animal, boolean force, boolean requireFed) {
		if (level == null || level.isClientSide || animal == null) {
			return false;
		}
		if (!force && !AnimalUtil.isCollectable(animal, requireFed)) {
			return false;
		}
		if (boundType == null) {
			if (typeLocked) {
				return false;
			}
			if (!everBound) {
				boundType = animal.getType();
				typeLocked = true;
				everBound = true;
			}
		} else if (animal.getType() != boundType) {
			return false;
		}
		if (stored.size() >= maxSlots()) {
			return false;
		}
		stored.add(serializeAnimal(animal, false));
		animal.discard();
		return true;
	}

	private boolean tryCollectOneLocked(Animal animal) {
		if (level == null || level.isClientSide || animal == null) {
			return false;
		}
		if (animal.getType() != boundType) {
			return false;
		}
		if (stored.size() >= maxSlots()) {
			return false;
		}
		stored.add(serializeAnimal(animal, true));
		animal.discard();
		return true;
	}

	private CompoundTag serializeAnimal(Animal animal, boolean locked) {
		animal.resetLove();
		EntityType<?> serializedType = animal.getType();
		// fed = 要被收起的永久标记：第一次被方块收起（任何路径）就打上，
		// 之后日出放出、日落自动收回，标记不清除
		animal.getPersistentData().putBoolean(AnimalUtil.TAG_FED, true);
		CompoundTag tag = animal.saveWithoutId(new CompoundTag());
		tag.putString("id", EntityType.getKey(serializedType).toString());
		if (locked) {
			tag.putBoolean(AnimalUtil.TAG_LOCKED, true);
		}
		return tag;
	}

	public int releaseSome(int count) {
		if (level == null || level.isClientSide) {
			return 0;
		}
		return releaseFromTags(count, false);
	}

	public int releaseAllManual() {
		return releaseSome(stored.size());
	}

	public int releaseForBalance(int count) {
		if (count <= 0) {
			return 0;
		}
		int released = releaseFromTags(count, true);
		if (released < count) {
			released += releaseFromTags(count - released, false);
		}
		return released;
	}

	private int releaseFromTags(int count, boolean lockedOnly) {
		if (level == null || level.isClientSide) {
			return 0;
		}
		int released = 0;
		Iterator<CompoundTag> it = stored.iterator();
		while (it.hasNext() && released < count) {
			CompoundTag tag = it.next();
			boolean locked = tag.getBoolean(AnimalUtil.TAG_LOCKED);
			if (locked != lockedOnly) {
				continue;
			}
			it.remove();
			pendingSpawns.add(tag);
			released++;
		}
		if (released > 0) {
			setChanged();
			syncVisuals();
		}
		return released;
	}

	private void spawnOne(CompoundTag tag) {
		if (level == null || level.isClientSide) {
			return;
		}
		Optional<Entity> opt = EntityType.create(tag, level);
		if (opt.isEmpty()) {
			return;
		}
		Entity entity = opt.get();

		boolean starving = tag.getBoolean(AnimalUtil.TAG_STARVING);
		if (starving && entity instanceof Mob mob) {
			MobEffect effect = Config.dsDebuffEffectResolved;
			if (effect != null) {
				mob.addEffect(new MobEffectInstance(
					BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), Config.dsDebuffDuration * 20, 0));
			}
		}

		// 释放出去的动物：fed 标签是"要被收起"的永久标记，表示它归方块管理，
		// 日落时靠它自动收回（日出放、日落收）。只清挨饿状态，
		// 否则日出放出、日落收不回，等于只有放没有收。
		entity.getPersistentData().remove(AnimalUtil.TAG_STARVING);
		tag.remove(AnimalUtil.TAG_STARVING);
		if (entity instanceof Animal animal) {
			// 清除原版繁育状态（InLove 恋爱 tick），否则释放出来仍是恋爱中，
			// 立即再交配 → BabyEntitySpawnEvent 强收 → 日出释放 → 无限循环
			animal.resetLove();
			// 仅对"成年且无冷却"的补繁殖冷却（正值 = 冷却 tick），
			// 防止释放后立刻再次交配被收；幼年子代保持原样继续成长，
			// 已带冷却的保留原冷却
			if (animal.getAge() >= 0 && animal.getAge() < 6000) {
				animal.setAge(6000);
			}
		}
		BlockPos above = worldPosition.above();
		double x = above.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
		double y = above.getY();
		double z = above.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
		entity.setPos(x, y, z);
		entity.setDeltaMovement(
			(level.random.nextDouble() - 0.5D) * 0.3D,
			0.28D,
			(level.random.nextDouble() - 0.5D) * 0.3D);

		if (Config.enableAIRestrict && entity instanceof Mob mob) {
			CompoundTag data = mob.getPersistentData();
			data.putInt(AnimalUtil.TAG_HOME_X, worldPosition.getX());
			data.putInt(AnimalUtil.TAG_HOME_Y, worldPosition.getY());
			data.putInt(AnimalUtil.TAG_HOME_Z, worldPosition.getZ());
			AIRestriction.register(mob);
		}
		level.addFreshEntity(entity);

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
				ParticleTypes.SMOKE, x, y + 0.25D, z, 12, 0.35D, 0.15D, 0.35D, 0.04D);
		}
	}

	public void releaseAtSunrise() {
		if (level == null || level.isClientSide) {
			return;
		}
		boolean any = false;
		for (CompoundTag tag : stored) {
			if (tag.getBoolean(AnimalUtil.TAG_LOCKED)) {
				tag.remove(AnimalUtil.TAG_LOCKED);
				any = true;
			}
		}
		if (any) {
			setChanged();
			syncVisuals();
		}
		releaseForBalance(threshold);
	}

	public int collectAllAtSunset() {
		return collectAll();
	}

	public void forceAbsorb(AgeableMobLike parentsAndChild) {
		if (level == null || level.isClientSide) {
			return;
		}
		Mob parentA = parentsAndChild.parentA();
		Mob parentB = parentsAndChild.parentB();
		Animal child = parentsAndChild.child();
		if (parentA == null || parentB == null) {
			return;
		}
		EntityType<?> type = parentA.getType();
		if (boundType == null) {
			if (typeLocked) {
				return;
			}
			if (!everBound) {
				boundType = type;
				typeLocked = true;
				everBound = true;
			}
		} else if (boundType != type) {
			return;
		}
boolean absorbed = false;
		if (parentA instanceof Animal a) {
			absorbed |= tryCollectOneLocked(a);
		}
		if (parentB instanceof Animal b) {
			absorbed |= tryCollectOneLocked(b);
		}
		if (child != null) {
			absorbed |= tryCollectOneLocked(child);
		}
		if (absorbed) {
			setChanged();
			syncVisuals();
		}
	}

	public void onShiftRightClick(ServerPlayer player) {
		long tick = level.getGameTime();
		if (lastShiftTick >= 0 && tick - lastShiftTick <= 40L) {
			shiftCount++;
		} else {
			shiftCount = 1;
		}
		lastShiftTick = tick;

		int locked = countLocked();
		if (shiftCount >= 3) {
			shiftCount = 0;
			int released = releaseAllManual();
			player.displayClientMessage(Component.translatable("message.never_farm.cleared", released, locked), true);
		} else {
			int released = releaseSome(8);
			player.displayClientMessage(Component.translatable("message.never_farm.released", released, locked), true);
		}
	}

	public void handleRightClick(Player player, InteractionHand hand) {
		if (level == null || level.isClientSide) {
			return;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty()) {
			boolean isFood = (boundType != null && AnimalUtil.isFoodFor(level, boundType, held))
				|| AnimalUtil.isAnimalFood(level, held);
			if (isFood) {
				depositFeed(player, hand);
				return;
			}
		}
		int collected = collectAllManual();
		if (player instanceof ServerPlayer sp) {
			if (collected > 0) {
				sp.displayClientMessage(
					Component.translatable("message.never_farm.collected", collected), true);
			} else if (held.isEmpty()) {
				withdrawFeedToPlayer(player);
			} else {
				sp.displayClientMessage(
					Component.translatable("message.never_farm.collected_none", countLocked()), true);
			}
		}
	}

	public void depositFeed(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}
		ItemStack copy = held.copy();
		ItemStack remainder = ItemHandlerHelper.insertItem(feedInventory, copy, false);
		player.setItemInHand(hand, remainder);
		setChanged();
		syncVisuals();
		if (player instanceof ServerPlayer sp) {
			int deposited = copy.getCount() - remainder.getCount();
			sp.displayClientMessage(
				Component.translatable("message.never_farm.deposited", deposited), true);
		}
	}

	public void tryAutoBreed() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (!AutoBreedHandler.enabled || boundType == null || stored.isEmpty()) {
			return;
		}
		if (!level.hasNeighborSignal(worldPosition)) {
			return;
		}
		if (countBabiesAround() > 12) {
			return;
		}
		int feed = feedCount();
		if (feed <= 0) {
			return;
		}
		int capacity = maxSlots() - storedCount();
		if (capacity <= 0) {
			return;
		}
		int want = 4 + level.random.nextInt(3);
		int count = Math.min(want, Math.min(feed, capacity));
		if (count <= 0) {
			return;
		}
		for (int i = 0; i < count; i++) {
			Entity entity = boundType.create(level);
			if (entity instanceof AgeableMob baby) {
				baby.setAge(-24000);
				if (baby instanceof Animal a) {
					AnimalUtil.markFed(a);
				}
				if (Config.enableAIRestrict && baby instanceof Mob mob) {
					CompoundTag data = mob.getPersistentData();
					data.putInt(AnimalUtil.TAG_HOME_X, worldPosition.getX());
					data.putInt(AnimalUtil.TAG_HOME_Y, worldPosition.getY());
					data.putInt(AnimalUtil.TAG_HOME_Z, worldPosition.getZ());
					AIRestriction.register(mob);
				}
				baby.setPos(
					worldPosition.getX() + 0.5D + (level.random.nextDouble() * 2.0D - 1.0D),
					worldPosition.getY() + 1.0D,
					worldPosition.getZ() + 0.5D + (level.random.nextDouble() * 2.0D - 1.0D));
				level.addFreshEntity(baby);
			}
		}
		consumeFeed(count);
		setChanged();
		syncVisuals();
	}

	private int countBabiesAround() {
		if (level == null || boundType == null) {
			return 0;
		}
		int n = 0;
		AABB area = scanArea();
		for (Animal a : level.getEntitiesOfClass(Animal.class, area,
			a -> a.getType() == boundType && a.getY() >= worldPosition.getY())) {
			if (a.isBaby()) {
				n++;
			}
		}
		return n;
	}

	public void withdrawFeedToPlayer(Player player) {
		if (level == null || level.isClientSide) {
			return;
		}
		ItemStack taken = withdrawFeed(64);
		if (taken.isEmpty()) {
			if (player instanceof ServerPlayer sp) {
				sp.displayClientMessage(Component.translatable("message.never_farm.no_feed"), true);
			}
			return;
		}
		int total = taken.getCount();
		player.getInventory().add(taken);
		int dropped = taken.getCount();
		if (dropped > 0) {
			player.drop(taken, false);
		}
		setChanged();
		syncVisuals();
		if (player instanceof ServerPlayer sp) {
			if (dropped > 0) {
				sp.displayClientMessage(
					Component.translatable("message.never_farm.withdrew_dropped", total), true);
			} else {
				sp.displayClientMessage(
					Component.translatable("message.never_farm.withdrew", total), true);
			}
		}
	}

	private ItemStack withdrawFeed(int amount) {
		for (int i = 0; i < feedInventory.getSlots(); i++) {
			ItemStack stack = feedInventory.getStackInSlot(i);
			if (!stack.isEmpty()) {
				ItemStack out = stack.split(Math.min(amount, stack.getCount()));
				return out;
			}
		}
		return ItemStack.EMPTY;
	}

	public void dailySettle(ServerLevel serverLevel) {
		if (!Config.dsEnable) {
			return;
		}
		int consume = (int) Math.ceil(stored.size() * Config.dsConsumeRate);
		int available = feedCount();
		if (available >= consume) {
			consumeFeed(consume);
		} else {
			consumeFeed(available);
			markAllStoredStarving();
		}
		syncVisuals();
		applyWitherToUnfed(serverLevel);
	}

	private void markAllStoredStarving() {
		for (CompoundTag tag : stored) {
			tag.putBoolean(AnimalUtil.TAG_STARVING, true);
		}
		setChanged();
	}

	private void consumeFeed(int amount) {
		int remaining = amount;
		for (int i = 0; i < feedInventory.getSlots() && remaining > 0; i++) {
			ItemStack stack = feedInventory.getStackInSlot(i);
			if (stack.isEmpty() || !isFeed(stack)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
		}
	}

	private void applyWitherToUnfed(ServerLevel serverLevel) {
		if (Config.dsAffectedMobs.isEmpty() || level == null) {
			return;
		}
		MobEffect effect = Config.dsDebuffEffectResolved;
		if (effect == null) {
			return;
		}
		long now = serverLevel.getDayTime();
		long window = 24000L;

		AABB area = scanArea();
		for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, area,
			m -> !m.isBaby() && isAffectedMob(m))) {
			long lastFed = mob.getPersistentData().getLong(AnimalUtil.TAG_LAST_FED_TIME);
			if (lastFed == 0 || now - lastFed > window) {
				mob.addEffect(new MobEffectInstance(
					BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), Config.dsDebuffDuration * 20, 1));
			}
		}
	}

	private boolean isAffectedMob(Mob mob) {
		return Config.dsAffectedTypes.contains(mob.getType());
	}

	public void toggleTypeLock(Player player, ItemStack held) {
		if (boundType == null) {
			if (held.getItem() instanceof SpawnEggItem egg && level != null) {
				EntityType<?> type = egg.getType(held);
				try {
					Entity probe = type.create(level);
					if (probe instanceof Animal) {
						boundType = type;
						typeLocked = true;
						everBound = true;
						setChanged();
						syncVisuals();
						if (player instanceof ServerPlayer sp) {
							sp.displayClientMessage(Component.translatable(
								"message.never_farm.locked_with", EntityType.getKey(type).toString()), true);
						}
						return;
					}
				} catch (Exception ignored) {
				}
			}
			List<Animal> nearby = detectedCollectable();
			if (!nearby.isEmpty()) {
				Animal nearest = nearby.stream()
					.min(Comparator.comparingDouble(a -> a.distanceToSqr(
						worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D)))
					.orElse(null);
				if (nearest != null) {
					boundType = nearest.getType();
					typeLocked = true;
					everBound = true;
					setChanged();
					syncVisuals();
					if (player instanceof ServerPlayer sp) {
						sp.displayClientMessage(Component.translatable(
							"message.never_farm.locked_with", EntityType.getKey(boundType).toString()), true);
					}
					return;
				}
			}
			if (player instanceof ServerPlayer sp) {
				sp.displayClientMessage(Component.translatable("message.never_farm.not_bound"), true);
			}
			return;
		}
		typeLocked = !typeLocked;
		if (!typeLocked) {
			if (!stored.isEmpty()) {
				typeLocked = true;
				if (player instanceof ServerPlayer sp) {
					sp.displayClientMessage(Component.translatable("message.never_farm.cannot_unlock"), true);
				}
				return;
			}
			boundType = null;
		}
		setChanged();
		syncVisuals();
		if (player instanceof ServerPlayer sp) {
			if (typeLocked) {
				sp.displayClientMessage(Component.translatable(
					"message.never_farm.locked", EntityType.getKey(boundType).toString()), true);
			} else {
				sp.displayClientMessage(Component.translatable("message.never_farm.unlocked"), true);
			}
		}
	}

	public int fullness() {
		if (stored.isEmpty()) {
			return 0;
		}
		double ratio = stored.size() / (double) Math.max(1, maxSlots());
		return Math.min(FULLNESS_LEVELS - 1, (int) Math.ceil(ratio * (FULLNESS_LEVELS - 1)));
	}

	public void syncVisuals() {
		if (level == null || level.isClientSide) {
			return;
		}
		BlockState state = level.getBlockState(worldPosition);
		if (state.hasProperty(WorkBlock.FULLNESS)) {
			int f = fullness();
			if (state.getValue(WorkBlock.FULLNESS) != f) {
				level.setBlock(worldPosition, state.setValue(WorkBlock.FULLNESS, f), 3);
			}
		}
		setChanged();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	public void applyKeepLoaded(boolean add) {
		if ((!Config.keepLoaded && !KeepLoadedHandler.enabled) || level == null || level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ChunkPos center = new ChunkPos(worldPosition);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
				if (add) {
					serverLevel.getChunkSource().addRegionTicket(WORK_BLOCK_TICKET, cp, 1, cp);
				} else {
					serverLevel.getChunkSource().removeRegionTicket(WORK_BLOCK_TICKET, cp, 1, cp);
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (CompoundTag t : stored) {
			list.add(t);
		}
		tag.put("StoredAnimals", list);
		ListTag pending = new ListTag();
		for (CompoundTag t : pendingSpawns) {
			pending.add(t);
		}
		tag.put("PendingSpawns", pending);
		if (boundType != null) {
			tag.putString("BoundType", EntityType.getKey(boundType).toString());
		}
		tag.putBoolean("TypeLocked", typeLocked);
		tag.putBoolean("EverBound", everBound);
		tag.putInt("Threshold", threshold);
		tag.put("FeedInventory", feedInventory.serializeNBT(registries));
		writeClientData(tag);
	}

	private void writeClientData(CompoundTag tag) {
		tag.putInt("ClientStored", stored.size());
		tag.putInt("ClientLocked", countLocked());
		tag.putInt("ClientFeed", feedCount());
		tag.putInt("ClientThreshold", threshold);
		tag.putBoolean("ClientTypeLocked", typeLocked);
		if (boundType != null) {
			tag.putString("ClientBoundType", EntityType.getKey(boundType).toString());
		}
	}

	private void readClientData(CompoundTag tag) {
		clientStored = tag.getInt("ClientStored");
		clientLocked = tag.getInt("ClientLocked");
		clientFeed = tag.getInt("ClientFeed");
		clientThreshold = tag.getInt("ClientThreshold");
		clientTypeLocked = tag.getBoolean("ClientTypeLocked");
		clientBoundType = tag.contains("ClientBoundType")
			? AnimalUtil.parseEntityType(tag.getString("ClientBoundType")) : null;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (isMirroring()) {
			readClientData(tag);
			return;
		}
		stored.clear();
		ListTag list = tag.getList("StoredAnimals", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			stored.add(list.getCompound(i));
		}
		pendingSpawns.clear();
		ListTag pending = tag.getList("PendingSpawns", Tag.TAG_COMPOUND);
		for (int i = 0; i < pending.size(); i++) {
			pendingSpawns.add(pending.getCompound(i));
		}
		boundType = tag.contains("BoundType") ? AnimalUtil.parseEntityType(tag.getString("BoundType")) : null;
		typeLocked = tag.getBoolean("TypeLocked");
		everBound = tag.getBoolean("EverBound");
		threshold = tag.getInt("Threshold");
		if (tag.contains("FeedInventory")) {
			feedInventory.deserializeNBT(registries, tag.getCompound("FeedInventory"));
		}
		syncVisuals();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		writeClientData(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public int getContainerSize() {
		return feedInventory.getSlots();
	}

	@Override
	public boolean isEmpty() {
		return feedCount() == 0;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= feedInventory.getSlots()) {
			return ItemStack.EMPTY;
		}
		return feedInventory.getStackInSlot(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		if (slot < 0 || slot >= feedInventory.getSlots()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = feedInventory.extractItem(slot, amount, false);
		if (!out.isEmpty()) {
			setChanged();
			syncVisuals();
		}
		return out;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return removeItem(slot, feedInventory.getStackInSlot(slot).getCount());
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= feedInventory.getSlots()) {
			return;
		}
		feedInventory.setStackInSlot(slot, stack);
		setChanged();
		syncVisuals();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < feedInventory.getSlots(); i++) {
			feedInventory.setStackInSlot(i, ItemStack.EMPTY);
		}
		setChanged();
		syncVisuals();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		int[] slots = new int[feedInventory.getSlots()];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = i;
		}
		return slots;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return isFeed(stack);
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return isFeed(stack);
	}

	@Override
	public boolean canTakeItem(Container container, int slot, ItemStack stack) {
		return true;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return true;
	}

	private boolean isFeed(ItemStack stack) {
		if (stack.isEmpty() || level == null) {
			return true;
		}
		if (boundType != null && AnimalUtil.isFoodFor(level, boundType, stack)) {
			return true;
		}
		return AnimalUtil.isAnimalFood(level, stack);
	}

	private class FeedHandler extends ItemStackHandler {

		FeedHandler(int size) {
			super(size);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if (!isFeed(stack)) {
				return false;
			}
			return feedCount() < MAX_FEED_PER_BLOCK;
		}

		@Override
		public int getSlotLimit(int slot) {
			int existing = getStackInSlot(slot).getCount();
			int room = MAX_FEED_PER_BLOCK - (feedCount() - existing);
			return Math.max(0, Math.min(super.getSlotLimit(slot), room));
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null && !level.isClientSide) {
			ACTIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(this);
			applyKeepLoaded(true);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		applyKeepLoaded(false);
		removeFromActive();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (level != null && !level.isClientSide) {
			ACTIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(this);
		}
	}

	private void removeFromActive() {
		if (level != null) {
			Set<WorkBlockEntity> set = ACTIVE.get(level.dimension());
			if (set != null) {
				set.remove(this);
			}
		}
	}
}