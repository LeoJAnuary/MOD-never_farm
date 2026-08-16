package dev.never_farm.block;

import dev.never_farm.blockentity.WorkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import javax.annotation.Nullable;



public class WorkBlock extends Block implements EntityBlock {

	

	public static final IntegerProperty FULLNESS = IntegerProperty.create("fullness", 0, 3);

	public WorkBlock() {
		super(Properties.of()
			.mapColor(MapColor.COLOR_BROWN)
			.strength(3.0F, 12.0F)
			// requiresCorrectToolForDrops 默认即为 false：任何工具（包括空手）都可挖掘并正常掉落；
			// 因已加入 mineable/pickaxe 标签，镐子等级越高挖掘速度越快（原版挖掘逻辑：速度 = 工具挖掘速度 / 硬度）
			.sound(SoundType.STONE));
		registerDefaultState(this.stateDefinition.any().setValue(FULLNESS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FULLNESS);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WorkBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != dev.never_farm.Never_farm.WORK_BLOCK_ENTITY.get()) {
			return null;
		}
		return (l, p, s, be) -> WorkBlockEntity.tick(l, p, s, (WorkBlockEntity) be);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof WorkBlockEntity work)) {
			return ItemInteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			

			if (player instanceof ServerPlayer sp) {
				work.onShiftRightClick(sp);
			}
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof WorkBlockEntity work && !level.isClientSide) {
				// 数据全部由 getDrops 写入方块掉落物（动物/饲料/阈值/绑定），此处不再弹出
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
		// 掉落方块时把方块实体数据（收容生物/饲料/阈值/类型绑定）写进掉落物，
		// 重新放置后数据完整恢复
		BlockEntity blockEntity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof WorkBlockEntity work) {
			ServerLevel level = params.getLevel();
			ItemStack stack = new ItemStack(this);
			CompoundTag tag = work.saveWithFullMetadata(level.registryAccess());
			stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
			return List.of(stack);
		}
		return super.getDrops(state, params);
	}
}
