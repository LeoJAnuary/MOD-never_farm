package dev.never_farm.handler;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.never_farm.Never_farm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Never_farm.MODID)
public class AutoBreedHandler {

	public static boolean enabled = true;

	@SubscribeEvent
	public static void registerCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal("neverfarm")
			.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal("autobreed")
				.requires(source -> source.hasPermission(2))
				.executes(context -> toggle(context.getSource()))
				.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("state", BoolArgumentType.bool())
					.executes(context -> set(context.getSource(), BoolArgumentType.getBool(context, "state"))))));
	}

	private static int toggle(CommandSourceStack source) {
		enabled = !enabled;
		report(source);
		return 1;
	}

	private static int set(CommandSourceStack source, boolean state) {
		enabled = state;
		report(source);
		return 1;
	}

	private static void report(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable(
			enabled ? "message.never_farm.auto_breed_on" : "message.never_farm.auto_breed_off"), true);
	}
}
