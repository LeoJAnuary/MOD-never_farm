package dev.never_farm;

import com.mojang.logging.LogUtils;
import dev.never_farm.block.WorkBlock;
import dev.never_farm.blockentity.WorkBlockEntity;
import dev.never_farm.handler.BreedingHandler;
import dev.never_farm.handler.InteractionHandler;
import dev.never_farm.handler.ScheduleHandler;
import dev.never_farm.network.NeverFarmNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;



@Mod(Never_farm.MODID)
public class Never_farm {
	

	public static final String MODID = "never_farm";
	

	public static final Logger LOGGER = LogUtils.getLogger();

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	

	public static final DeferredBlock<WorkBlock> WORK_BLOCK =
		BLOCKS.register("work_block", WorkBlock::new);
	public static final DeferredItem<BlockItem> WORK_BLOCK_ITEM =
		ITEMS.registerSimpleBlockItem("work_block", WORK_BLOCK);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkBlockEntity>> WORK_BLOCK_ENTITY =
		BLOCK_ENTITIES.register("work_block",
			() -> BlockEntityType.Builder.of(WorkBlockEntity::new, WORK_BLOCK.get()).build(null));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEVER_FARM_TAB =
		CREATIVE_MODE_TABS.register("never_farm_tab", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.never_farm"))
			.withTabsBefore(CreativeModeTabs.COMBAT)
			.icon(() -> WORK_BLOCK_ITEM.get().getDefaultInstance())
			.displayItems((parameters, output) -> output.accept(WORK_BLOCK_ITEM.get()))
			.build());

	public Never_farm(IEventBus modEventBus, ModContainer modContainer) {
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		BLOCK_ENTITIES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);

		modEventBus.register(Config.class);
		modEventBus.register(NeverFarmNetworking.class);
		modEventBus.addListener(NeverFarmModEvents::registerCapabilities);
		modEventBus.addListener(NeverFarmModEvents::registerGameTests);

		NeoForge.EVENT_BUS.register(ScheduleHandler.class);
		NeoForge.EVENT_BUS.register(BreedingHandler.class);
		NeoForge.EVENT_BUS.register(InteractionHandler.class);

		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
	}

	public static final class NeverFarmModEvents {
		private NeverFarmModEvents() {
		}

		public static void registerCapabilities(RegisterCapabilitiesEvent event) {
			event.registerBlockEntity(
				net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
				WORK_BLOCK_ENTITY.get(),
				(WorkBlockEntity be, net.minecraft.core.Direction side) -> (IItemHandler) be.feedInventory());
		}

		public static void registerGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
			event.register(dev.never_farm.gametest.WorkBlockGameTests.class);
		}
	}
}
