package com.dwurdy.lifepath.compat;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Writes Lifepath quality onto the DEPENDENCY mod's own quality component, so
 * crafted output gets real mechanical teeth without a compile-time dep:
 * <ul>
 *   <li>Overgeared {@code overgeared:forging_quality} — POOR…MASTER applies its
 *       own durability/mining-speed multipliers;</li>
 *   <li>Kaleidoscope Cookery {@code kaleidoscope_cookery:quality} —
 *       POOR…SUPERB modifies food properties/effects.</li>
 * </ul>
 * Both are resolved reflectively (registry id + {@code Class.forName}) — absent
 * mods degrade to a no-op, no foreign class is ever loaded eagerly.
 */
public final class ExternalQualityBridge {
	private ExternalQualityBridge() {}

	/** One foreign quality component: how a lifepath tier maps onto it and
	 *  which stacks it applies to. */
	private record Target(ResourceLocation componentId, String enumClass,
			Map<String, String> tierMap,
			java.util.function.Predicate<ItemStack> applies) {}

	private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FORGED_OUTPUTS =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
					LifepathMod.id("forged_outputs"));

	private static final Target OVERGEARED = new Target(
			ResourceLocation.fromNamespaceAndPath("overgeared", "forging_quality"),
			"net.stirdrem.overgeared.ForgingQuality",
			Map.of(
					"crude", "POOR",
					"poor", "WELL",
					"standard", "EXPERT",
					"fine", "PERFECT",
					"masterwork", "MASTER"),
			// Forged items only — never stamp wheat with a forging grade.
			stack -> stack.getItem().builtInRegistryHolder().key().location()
					.getNamespace().equals("overgeared")
					|| stack.is(FORGED_OUTPUTS));

	private static final Target KCOOKERY = new Target(
			ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "quality"),
			"com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality",
			Map.of(
					"crude", "POOR",
					"poor", "POOR",
					"standard", "STANDARD",
					"fine", "EXCELLENT",
					"masterwork", "SUPERB"),
			// Food only — the component is meaningless on tools.
			stack -> stack.getItem().builtInRegistryHolder().key().location()
					.getNamespace().equals("kaleidoscope_cookery")
					|| stack.get(net.minecraft.core.component.DataComponents.FOOD) != null);

	private static final Target[] TARGETS = {OVERGEARED, KCOOKERY};

	/** Write the foreign quality component for every present dep that applies
	 *  to this stack. Safe no-op for {@code null}/unknown tiers and absent mods. */
	public static void apply(ItemStack stack, @Nullable String lifepathTier) {
		if (lifepathTier == null) {
			return;
		}
		for (Target t : TARGETS) {
			String foreign = t.tierMap().get(lifepathTier);
			if (foreign == null || !t.applies().test(stack)) {
				continue;
			}
			setComponent(stack, t, foreign);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setComponent(ItemStack stack, Target t, String enumName) {
		if (!Platform.get().isModLoaded(t.componentId().getNamespace())) {
			return;
		}
		try {
			DataComponentType type = BuiltInRegistries.DATA_COMPONENT_TYPE
					.get(t.componentId());
			if (type == null) {
				return;
			}
			Class<?> enumClass = Class.forName(t.enumClass());
			Object value = Enum.valueOf((Class<? extends Enum>) enumClass, enumName);
			stack.set(type, value);
		} catch (Throwable ex) {
			LifepathMod.LOGGER.debug("quality bridge {} -> {} failed: {}",
					t.componentId(), enumName, ex.toString());
		}
	}
}
