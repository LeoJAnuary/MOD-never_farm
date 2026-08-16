package dev.never_farm.handler;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.never_farm.Never_farm;
import dev.never_farm.blockentity.WorkBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Never_farm.MODID)
public class KeepLoadedHandler {

	/** 指令控制的弱加载开关，默认关闭（需要 OP 通过 /neverfarm keepLoaded 开启） */
	public static boolean enabled = false;

	@SubscribeEvent
	public static void registerCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal("neverfarm")
			.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal("keepLoaded")
				.requires(source -> source.hasPermission(2))
				.executes(context -> toggle(context.getSource()))
				.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("state", BoolArgumentType.bool())
					.executes(context -> set(context.getSource(), BoolArgumentType.getBool(context, "state"))))));
	}

	private static int toggle(CommandSourceStack source) {
		enabled = !enabled;
		applyToAll(source.getLevel());
		report(source);
		return 1;
	}

	private static int set(CommandSourceStack source, boolean state) {
		enabled = state;
		applyToAll(source.getLevel());
		report(source);
		return 1;
	}

	/** 把开关状态应用到当前服务器所有已加载的工作方块（添加/移除 3×3 区块 ticket） */
	private static void applyToAll(ServerLevel level) {
		var set = WorkBlockEntity.active().get(level.dimension());
		if (set == null) {
			return;
		}
		for (WorkBlockEntity be : set) {
			if (!be.isRemoved()) {
				be.applyKeepLoaded(enabled);
			}
		}
	}

	private static void report(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable(
			enabled ? "message.never_farm.keep_loaded_on" : "message.never_farm.keep_loaded_off"), true);
	}
}
