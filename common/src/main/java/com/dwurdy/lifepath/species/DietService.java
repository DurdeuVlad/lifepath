package com.dwurdy.lifepath.species;

import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.DietDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Generic diet gate (M5-4): a species' optional {@code diet_rules} def decides
 * whether an item nourishes at all. Consulted at the use-start gates
 * ({@code ItemMixin}/{@code ItemUtilsMixin} — a denied food can't be
 * consumed, per Beta-8 decision D5) and once inside {@code Player.eat} as a
 * zero-nutrition backstop for direct {@code eat} calls that bypass the use
 * path. No rules → everything nourishes.
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
			for (ResourceLocation condId : data.conditions()) {
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

	/**
	 * Denial feedback for the D5 hard-block (M15-2/M15-5): used when a use
	 * attempt is canceled before it can start ({@code ItemMixin}/
	 * {@code ItemUtilsMixin}), and when a direct {@code Player.eat} call slips
	 * a denied stack past the use gates.
	 */
	public static void denyFood(ServerPlayer player) {
		// A diet may name its own refusal line ("only iron nourishes you") —
		// the stock message stays the fallback.
		for (DietDefinition def : effectiveDiets(
				com.dwurdy.lifepath.character.CharacterManager
						.getCharacter(player))) {
			if (def.denyMessage().isPresent()) {
				player.displayClientMessage(net.minecraft.network.chat.Component
						.literal(def.denyMessage().get()), true);
				return;
			}
		}
		player.displayClientMessage(net.minecraft.network.chat.Component
				.translatable("lifepath.diet.denied"), true);
	}

	/** May {@code stack} nourish this character? No diet def → always yes. */
	public static boolean allows(@Nullable PlayerCharacterData data, ItemStack stack) {
		var diets = effectiveDiets(data);
		if (diets.isEmpty() || stack.isEmpty()) {
			return true;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		var entry = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
		for (DietDefinition def : diets) {
			for (var allowed : def.allowed()) {
				if (allowed.matches(itemId, entry, Registries.ITEM)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Beta-10 ferrovore path: right-clicking an allowed item that isn't
	 * vanilla food consumes one and feeds directly — vanilla never starts an
	 * eat on items without a FOOD component, so {@code automaton_foods} were
	 * dead clicks. Requires a diet with {@code nutrition > 0} that actually
	 * allows the stack; returns true when a consume happened.
	 */
	public static boolean tryEatDietItem(ServerPlayer player, ItemStack stack) {
		var data = com.dwurdy.lifepath.character.CharacterManager
				.getCharacter(player);
		var diets = effectiveDiets(data);
		if (diets.isEmpty() || stack.isEmpty()
				|| stack.has(net.minecraft.core.component.DataComponents.FOOD)
				|| player.getFoodData().getFoodLevel() >= 20) {
			return false;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		var entry = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
		for (DietDefinition def : diets) {
			for (var allowed : def.allowed()) {
				if (!allowed.matches(itemId, entry, Registries.ITEM)) {
					continue;
				}
				if (def.nutrition() <= 0) {
					return false;
				}
				stack.shrink(1);
				player.getFoodData().eat(def.nutrition(),
						def.saturationModifier());
				player.level().playSound(null, player,
						net.minecraft.sounds.SoundEvents.GENERIC_EAT,
						net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
				return true;
			}
		}
		return false;
	}
}
