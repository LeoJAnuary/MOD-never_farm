package dev.never_farm.handler;

import dev.never_farm.Never_farm;
import dev.never_farm.blockentity.WorkBlockEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;



@EventBusSubscriber(modid = Never_farm.MODID)
public class ScheduleHandler {

	

	private static final Map<ResourceKey<Level>, Long> LAST_DAY_TIME = new HashMap<>();

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		LevelAccessor accessor = event.getLevel();
		if (accessor instanceof Level level) {
			LAST_DAY_TIME.remove(level.dimension());
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		LevelAccessor accessor = event.getLevel();
		if (!(accessor instanceof Level level) || level.isClientSide || event.getChunk() == null) {
			return;
		}
		Set<WorkBlockEntity> set = WorkBlockEntity.active().get(level.dimension());
		if (set == null || set.isEmpty()) {
			return;
		}
		ChunkPos cp = event.getChunk().getPos();
		set.removeIf(be -> be.isRemoved() || new ChunkPos(be.getBlockPos()).equals(cp));
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		for (ServerLevel level : server.getAllLevels()) {
			long dayTime = level.getDayTime() % 24000L;
			Long prev = LAST_DAY_TIME.put(level.dimension(), dayTime);
			if (prev == null) {
				continue;
			}
			boolean sunrise = prev > dayTime;                  
			boolean sunset = prev < 12000L && dayTime >= 12000L; 
			if (sunrise || sunset) {
				dailyCheck(level, sunrise);
			}
		}
	}

	private static void dailyCheck(ServerLevel level, boolean sunrise) {
		Set<WorkBlockEntity> set = WorkBlockEntity.active().get(level.dimension());
		if (set == null || set.isEmpty()) {
			return;
		}
		for (WorkBlockEntity be : new ArrayList<>(set)) {
			if (be.isRemoved()) {
				continue;
			}
			if (sunrise) {
				be.releaseAtSunrise();
				be.tryAutoBreed();
			} else {
				be.collectAllAtSunset();
			}
			be.dailySettle(level);
		}
	}
}
