package dev.never_farm.handler;

import dev.never_farm.Config;
import dev.never_farm.util.AnimalUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

public final class AIRestriction {

	private AIRestriction() {
	}

	public static void register(Mob mob) {
		if (!Config.enableAIRestrict) {
			return;
		}
		CompoundTag data = mob.getPersistentData();
		if (!data.contains(AnimalUtil.TAG_HOME_X)) {
			return;
		}
		BlockPos home = new BlockPos(
			data.getInt(AnimalUtil.TAG_HOME_X),
			data.getInt(AnimalUtil.TAG_HOME_Y),
			data.getInt(AnimalUtil.TAG_HOME_Z));
		mob.goalSelector.addGoal(2, new StayNearHomeGoal(mob, home, Config.aiRestrictRadius));
	}
}
