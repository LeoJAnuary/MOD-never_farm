package dev.never_farm.client;

import dev.never_farm.Never_farm;
import dev.never_farm.network.C2SBlockAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;



@EventBusSubscriber(modid = Never_farm.MODID, value = Dist.CLIENT)
public class ClientInteractionHandler {

	private static final long LONG_PRESS_MS = 250L;

	private static BlockPos trackingPos;
	private static InteractionHand trackingHand;
	private static long pressTime;

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		if (player == null || player.isShiftKeyDown() || player.isSpectator()) {
			return;
		}
		if (event.getSide() != net.neoforged.fml.LogicalSide.CLIENT) {
			return;
		}
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		if (!level.getBlockState(pos).is(Never_farm.WORK_BLOCK)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (trackingPos != null && mc.options.keyUse.isDown()) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		trackingPos = pos.immutable();
		trackingHand = event.getHand();
		pressTime = System.currentTimeMillis();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (trackingPos == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			trackingPos = null;
			return;
		}
		long held = System.currentTimeMillis() - pressTime;
		if (held >= LONG_PRESS_MS) {
			BlockPos pos = trackingPos;
			trackingPos = null;
			

			ClientSlider.open(mc, pos);
		} else if (!mc.options.keyUse.isDown()) {
			

			BlockPos pos = trackingPos;
			InteractionHand hand = trackingHand;
			trackingPos = null;
			PacketDistributor.sendToServer(new C2SBlockAction(pos, hand.ordinal()));
		}
	}
}
