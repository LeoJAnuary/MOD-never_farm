package dev.never_farm.network;

import dev.never_farm.Never_farm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NeverFarmNetworking {

	public static final String CHANNEL = "never_farm";

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(CHANNEL).versioned("1").optional();
		registrar.playToServer(C2SBlockAction.TYPE, C2SBlockAction.STREAM_CODEC, C2SBlockAction::handle);
		registrar.playToServer(C2SSetThreshold.TYPE, C2SSetThreshold.STREAM_CODEC, C2SSetThreshold::handle);
		registrar.playToServer(C2SRequestData.TYPE, C2SRequestData.STREAM_CODEC, C2SRequestData::handle);
		registrar.playToClient(S2CBlockData.TYPE, S2CBlockData.STREAM_CODEC, S2CBlockData::handle);
	}
}
