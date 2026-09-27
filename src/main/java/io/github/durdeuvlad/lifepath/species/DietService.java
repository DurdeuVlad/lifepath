package io.github.durdeuvlad.lifepath.species;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.DietDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Generic diet gate (M5-4): a species' optional {@code diet_rules} def decides
 * whether an item yields nutrition. Consulted once inside {@code
 * PlayerEntity.eatFood} at the {@code HungerManager.eat} call — a denied item
 * is still eaten (animation, stack consumption, food side-effects) but adds
 * zero hunger/saturation. No rules → everything nourishes.
 */
public final class DietService {
	private DietService() {
	}

	/** The species' diet def, or null when none is declared/loaded. */
	@Nullable
	public static DietDefinition dietOf(@Nullable PlayerCharacterData data) {
		if (data == null || data.speciesId() == null) {
			return null;
		}
		SpeciesDefinition species = LifepathContent.species().get(data.speciesId());
		if (species == null || species.dietRules().isEmpty()) {
			return null;
		}
		return LifepathContent.diets().get(species.dietRules().get());
	}

	/**
	 * M9-1: condition {@code diet_rules} compose with species — when any held
	 * condition declares a diet, the union of condition allowed-lists REPLACES
	 * the species diet (a vampire's blood diet overrides its species' menu,
	 * not adds to it). No condition diet → species diet applies.
	 */
	public static java.util.List<DietDefinition> effectiveDiets(
			@Nullable PlayerCharacterData data) {
		java.util.List<DietDefinition> conditionDiets = new java.util.ArrayList<>();
		if (data != null) {
			for (Identifier condId : data.conditions()) {
				var cond = LifepathContent.conditions().get(condId);
				if (cond != null && cond.dietRules().isPresent()) {
					var diet = LifepathContent.diets().get(cond.dietRules().get());
					if (diet != null) {
						conditionDiets.add(diet);
					}
				}
			}
		}
		if (!conditionDiets.isEmpty()) {
			return conditionDiets;
		}
		DietDefinition species = dietOf(data);
		return species == null ? List.of() : List.of(species);
	}

	/** May {@code stack} nourish this character? No diet def → always yes. */
	public static boolean allows(@Nullable PlayerCharacterData data, ItemStack stack) {
		var diets = effectiveDiets(data);
		if (diets.isEmpty() || stack.isEmpty()) {
			return true;
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		var entry = Registries.ITEM.getEntry(stack.getItem());
		for (DietDefinition def : diets) {
			for (var allowed : def.allowed()) {
				if (allowed.matches(itemId, entry, RegistryKeys.ITEM)) {
					return true;
				}
			}
		}
		return false;
	}
}
