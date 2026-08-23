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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SBlockAction(BlockPos pos, int handOrdinal) implements CustomPacketPayload {

	public static final Type<C2SBlockAction> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Never_farm.MODID, "block_action"));

	public static final StreamCodec<ByteBuf, C2SBlockAction> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, C2SBlockAction::pos,
		ByteBufCodecs.VAR_INT, C2SBlockAction::handOrdinal,
		C2SBlockAction::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(C2SBlockAction payload, IPayloadContext context) {
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
				InteractionHand hand = payload.handOrdinal() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
				work.handleRightClick(sp, hand);
			}
		});
	}
}
