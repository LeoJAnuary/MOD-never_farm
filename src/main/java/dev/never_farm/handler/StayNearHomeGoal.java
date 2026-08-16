package dev.never_farm.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class StayNearHomeGoal extends Goal {

	private final Mob mob;
	private final BlockPos home;
	private final double radius;
	private final double radiusSq;

	public StayNearHomeGoal(Mob mob, BlockPos home, int radius) {
		this.mob = mob;
		this.home = home;
		this.radius = radius;
		this.radiusSq = (double) radius * radius;
	}

	@Override
	public boolean canUse() {
		return mob.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > radiusSq;
	}

	@Override
	public boolean canContinueToUse() {
		return mob.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > radiusSq * 0.25D
			&& !mob.getNavigation().isDone();
	}

	@Override
	public void start() {
		pickTarget();
	}

	@Override
	public void tick() {
		if (mob.getNavigation().isDone()) {
			pickTarget();
		}
	}

	private void pickTarget() {
		RandomSource random = mob.getRandom();
		double span = radius * 0.6D;
		double tx = home.getX() + 0.5D + (random.nextDouble() * 2.0D - 1.0D) * span;
		double tz = home.getZ() + 0.5D + (random.nextDouble() * 2.0D - 1.0D) * span;
		double ty = home.getY() + 0.5D;
		mob.getNavigation().moveTo(tx, ty, tz, 1.0D);
	}
}
