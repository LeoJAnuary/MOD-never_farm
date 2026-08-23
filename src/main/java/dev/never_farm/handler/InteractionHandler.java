package dev.never_farm.handler;

import dev.never_farm.Never_farm;
import dev.never_farm.blockentity.WorkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;



@EventBusSubscriber(modid = Never_farm.MODID)
public class InteractionHandler {

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		Player player = event.getEntity();
		if (player == null || !player.isShiftKeyDown()) {
			return;
		}
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		if (!level.getBlockState(pos).is(Never_farm.WORK_BLOCK)) {
			return;
		}
		

		event.setCanceled(true);
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof WorkBlockEntity work) {
			work.toggleTypeLock(player, player.getMainHandItem());
		}
	}
}
