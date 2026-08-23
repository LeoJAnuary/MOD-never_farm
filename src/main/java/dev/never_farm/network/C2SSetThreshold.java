package dev.never_farm.network;

import dev.never_farm.Never_farm;
import dev.never_farm.blockentity.WorkBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SSetThreshold(BlockPos pos, int value) implements CustomPacketPayload {

	public static final Type<C2SSetThreshold> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Never_farm.MODID, "set_threshold"));

	public static final StreamCodec<ByteBuf, C2SSetThreshold> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, C2SSetThreshold::pos,
		ByteBufCodecs.VAR_INT, C2SSetThreshold::value,
		C2SSetThreshold::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(C2SSetThreshold payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer sp)) {
				return;
			}
			Level level = sp.serverLevel();
			BlockPos pos = payload.pos();
			if (!level.isLoaded(pos) || !sp.canInteractWithBlock(pos, 6.0D)) {
				return;
			}
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof WorkBlockEntity work) {
				work.setThreshold(payload.value());
			}
		});
	}
}
