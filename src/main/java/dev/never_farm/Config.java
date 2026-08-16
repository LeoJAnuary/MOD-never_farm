package dev.never_farm;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



public class Config {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	

	private static final ModConfigSpec.IntValue MAX_SLOTS = BUILDER
		.comment("Max animal slots per work block (16/32/64, range 16~64)")
		.defineInRange("general.maxSlots", 32, 16, 64);

	private static final ModConfigSpec.ConfigValue<String> CHECK_INTERVAL = BUILDER
		.comment("Auto collect/release schedule. Only \"Sunrise_Sunset\" (once at sunrise and once at sunset per day) is supported")
		.define("general.checkInterval", "Sunrise_Sunset");

	private static final ModConfigSpec.BooleanValue ENABLE_AI_RESTRICT = BUILDER
		.comment("Whether animals released by the work block are restricted near the block (prevent them from wandering off)")
		.define("general.enableAIRestrict", true);

	private static final ModConfigSpec.IntValue AI_RESTRICT_RADIUS = BUILDER
		.comment("AI restriction radius (blocks); animals farther than this are pulled back")
		.defineInRange("general.aiRestrictRadius", 8, 1, 64);

	private static final ModConfigSpec.BooleanValue KEEP_LOADED = BUILDER
		.comment("Keep the work block chunk loaded so sunrise/sunset checks run even when the player is away (default off; OP can toggle at runtime via /neverfarm keepLoaded)")
		.define("general.keepLoaded", false);

	private static final ModConfigSpec.BooleanValue ENFORCE_BREEDING_CONTROL = BUILDER
		.comment("Enforce breeding control: babies and parents are absorbed immediately after breeding and can only be released at the next auto check")
		.define("general.enforceBreedingControl", true);

	

	private static final ModConfigSpec.BooleanValue DS_ENABLE = BUILDER
		.comment("Don't Starve survival DLC toggle, disabled by default")
		.define("donnotstarve.enable", false);

	private static final ModConfigSpec.IntValue DS_STARVE_DEATHS = BUILDER
		.comment("Animals starved per check when stored feed is insufficient")
		.defineInRange("donnotstarve.starveDeathsPerCheck", 2, 1, Integer.MAX_VALUE);

	private static final ModConfigSpec.DoubleValue DS_CONSUME_RATE = BUILDER
		.comment("Feed consumption rate (each stored animal consumes feed proportionally)")
		.defineInRange("donnotstarve.consumeRate", 1.0D, 0.01D, 100.0D);

	private static final ModConfigSpec.ConfigValue<List<? extends String>> DS_AFFECTED_MOBS = BUILDER
		.comment("Entity IDs affected by the feeding/wither rules")
		.defineListAllowEmpty("donnotstarve.affectedMobs",
			() -> List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig"),
			() -> "minecraft:cow", Config::validateEntityName);

	private static final ModConfigSpec.ConfigValue<String> DS_DEBUFF_EFFECT = BUILDER
		.comment("Debuff applied when not fed in time (default WITHER; registry name like \"minecraft:wither\" also works)")
		.define("donnotstarve.debuffEffect", "WITHER");

	private static final ModConfigSpec.IntValue DS_DEBUFF_DURATION = BUILDER
		.comment("Duration of the debuff (seconds)")
		.defineInRange("donnotstarve.debuffDuration", 300, 1, Integer.MAX_VALUE);

	static final ModConfigSpec SPEC = BUILDER.build();

	public static int maxSlots;
	public static String checkInterval;
	public static boolean enableAIRestrict;
	public static int aiRestrictRadius;
	public static boolean enforceBreedingControl;
	public static boolean keepLoaded;
	public static boolean dsEnable;
	public static int dsStarveDeaths;
	public static double dsConsumeRate;
	public static List<? extends String> dsAffectedMobs;
	public static Set<net.minecraft.world.entity.EntityType<?>> dsAffectedTypes = new HashSet<>();
	public static String dsDebuffEffect;
	public static int dsDebuffDuration;
	

	public static net.minecraft.world.effect.MobEffect dsDebuffEffectResolved;

	private static boolean validateEntityName(final Object obj) {
		return obj instanceof String s && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
			.containsKey(net.minecraft.resources.ResourceLocation.tryParse(s));
	}

	@SubscribeEvent
	public static void onLoad(final ModConfigEvent event) {
		maxSlots = MAX_SLOTS.get();
		checkInterval = CHECK_INTERVAL.get();
		enableAIRestrict = ENABLE_AI_RESTRICT.get();
		aiRestrictRadius = AI_RESTRICT_RADIUS.get();
		enforceBreedingControl = ENFORCE_BREEDING_CONTROL.get();
		keepLoaded = KEEP_LOADED.get();

		dsEnable = DS_ENABLE.get();
		dsStarveDeaths = DS_STARVE_DEATHS.get();
		dsConsumeRate = DS_CONSUME_RATE.get();
		dsAffectedMobs = DS_AFFECTED_MOBS.get();
		dsAffectedTypes = dsAffectedMobs.stream()
			.map(dev.never_farm.util.AnimalUtil::parseEntityType)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		dsDebuffEffect = DS_DEBUFF_EFFECT.get();
		dsDebuffDuration = DS_DEBUFF_DURATION.get();
		dsDebuffEffectResolved = resolveEffect(dsDebuffEffect);
	}

	private static net.minecraft.world.effect.MobEffect resolveEffect(String raw) {
		net.minecraft.world.effect.MobEffect effect =
			net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
				.get(net.minecraft.resources.ResourceLocation.tryParse(raw));
		if (effect == null) {
			for (net.minecraft.world.effect.MobEffect me : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT) {
				if (net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(me)
					.getPath().equalsIgnoreCase(raw)) {
					effect = me;
					break;
				}
			}
		}
		return effect;
	}
}
