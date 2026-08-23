package dev.never_farm.network;

import dev.never_farm.Never_farm;
import dev.never_farm.client.ThresholdSliderScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record S2CBlockData(int threshold, int maxSlots, Optional<String> boundType,
	int storedCount, boolean typeLocked, int feedCount) implements CustomPacketPayload {

	public static final Type<S2CBlockData> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Never_farm.MODID, "block_data"));

	public static final StreamCodec<ByteBuf, S2CBlockData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, S2CBlockData::threshold,
		ByteBufCodecs.VAR_INT, S2CBlockData::maxSlots,
		ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), S2CBlockData::boundType,
		ByteBufCodecs.VAR_INT, S2CBlockData::storedCount,
		ByteBufCodecs.BOOL, S2CBlockData::typeLocked,
		ByteBufCodecs.VAR_INT, S2CBlockData::feedCount,
		S2CBlockData::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(S2CBlockData payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!context.flow().isServerbound()) {
				applyClient(payload);
			}
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static void applyClient(S2CBlockData payload) {
		if (Minecraft.getInstance().screen instanceof ThresholdSliderScreen screen) {
			screen.updateData(payload);
		}
	}
}
