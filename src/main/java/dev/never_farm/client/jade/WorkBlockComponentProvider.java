package dev.never_farm.client.jade;

import dev.never_farm.blockentity.WorkBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

@OnlyIn(Dist.CLIENT)
public enum WorkBlockComponentProvider implements IBlockComponentProvider {
	INSTANCE;

	@Override
	public ResourceLocation getUid() {
		return ResourceLocation.fromNamespaceAndPath("never_farm", "work_block");
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!(accessor.getBlockEntity() instanceof WorkBlockEntity be)) {
			return;
		}
		EntityType<?> type = be.boundType();
		if (type != null) {
			tooltip.add(Component.translatable("jade.never_farm.bound",
				Component.translatable(type.getDescriptionId())));
			if (be.isTypeLocked()) {
				tooltip.add(Component.translatable("jade.never_farm.type_locked"));
			}
		} else {
			tooltip.add(Component.translatable("jade.never_farm.unbound"));
		}
		tooltip.add(Component.translatable("jade.never_farm.stored", be.storedCount(), be.maxSlots()));
		int locked = be.countLocked();
		if (locked > 0) {
			tooltip.add(Component.translatable("jade.never_farm.locked", locked));
		}
		tooltip.add(Component.translatable("jade.never_farm.threshold", be.threshold()));
		if (be.feedCount() > 0) {
			tooltip.add(Component.translatable("jade.never_farm.feed", be.feedCount()));
		}
	}
}
