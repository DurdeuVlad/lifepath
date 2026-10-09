package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Material-gate check shared by every production seam (Overgeared anvil,
 * vanilla crafting result, vanilla smithing table). A stack is denied when it
 * matches a {@code material_gates} tag in any outcome rule and the player's
 * level in that rule's skill is below the gate — unless a blueprint licenses
 * the work (Overgeared anvil only).
 */
public final class MaterialGates {

	private MaterialGates() {
	}

	/** A failed gate: the skill the rule demands, and the level it requires. */
	public record Denial(ResourceLocation skill, int requiredLevel) {
	}

	/** The first material gate this stack fails, or {@code null} when the
	 * craft is allowed (untagged output, high enough level, or licensed). */
	@Nullable
	public static Denial denialFor(ServerPlayer player, ItemStack stack,
			boolean blueprintLicensed) {
		if (stack.isEmpty()) {
			return null;
		}
		for (var rule : LifepathContent.outcomeRules().all().values()) {
			if (rule.materialGates().isEmpty()) {
				continue;
			}
			for (var gate : rule.materialGates().entrySet()) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, gate.getKey());
				if (!stack.is(tag)) {
					continue;
				}
				if (blueprintLicensed) {
					return null;
				}
				var progress = SkillService.progress(
						CharacterManager.getCharacter(player), rule.skill());
				if ((progress == null ? 0 : progress.level()) >= gate.getValue()) {
					return null;
				}
				return new Denial(rule.skill(), gate.getValue());
			}
		}
		return null;
	}

	public static void notifyDenied(ServerPlayer player, Denial denial) {
		player.displayClientMessage(Component.translatable("lifepath.forge.gated",
				denial.skill().getPath(), denial.requiredLevel()), true);
	}
}
