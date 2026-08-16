package dev.never_farm.client;

import dev.never_farm.network.C2SRequestData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;



@OnlyIn(Dist.CLIENT)
public final class ClientSlider {

	private ClientSlider() {
	}

	public static void open(Minecraft mc, BlockPos pos) {
		

		PacketDistributor.sendToServer(new C2SRequestData(pos));
		mc.setScreen(new ThresholdSliderScreen(pos));
	}
}
