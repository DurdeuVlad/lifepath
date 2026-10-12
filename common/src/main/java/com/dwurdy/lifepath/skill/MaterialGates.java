package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

	/** The first material gate this stack matches, or {@code null} when the
	 * output is ungated. A match does not by itself deny — a sufficient level
	 * or a blueprint may still license the craft. */
	@Nullable
	private static Denial requirementFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (var rule : LifepathContent.outcomeRules().all().values()) {
			if (rule.materialGates().isEmpty()) {
				continue;
			}
			for (var gate : rule.materialGates().entrySet()) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, gate.getKey());
				if (stack.is(tag)) {
					return new Denial(rule.skill(), gate.getValue());
				}
			}
		}
		return null;
	}

	/** The first material gate this stack fails, or {@code null} when the
	 * craft is allowed (untagged output, high enough level, or licensed). */
	@Nullable
	public static Denial denialFor(ServerPlayer player, ItemStack stack,
			boolean blueprintLicensed) {
		Denial requirement = requirementFor(stack);
		if (requirement == null || blueprintLicensed) {
			return null;
		}
		var progress = SkillService.progress(
				CharacterManager.getCharacter(player), requirement.skill());
		return (progress == null ? 0 : progress.level()) >= requirement.requiredLevel()
				? null : requirement;
	}

	/** True when the stack is gated and not blueprint-licensed — the
	 * fail-closed check for seams where no player can be resolved to compare
	 * levels (e.g. an anvil whose forging-session owner went offline
	 * mid-craft). */
	public static boolean isGatedOutput(ItemStack stack, boolean blueprintLicensed) {
		return !blueprintLicensed && requirementFor(stack) != null;
	}

	private static final long DENY_MESSAGE_COOLDOWN_TICKS = 40;
	private static final Map<UUID, Long> denyMessageTicks = new HashMap<>();

	/** Rate-limited per player so result recomputation — crafting-grid slot
	 * changes, anvil retry ticks — can't spam the actionbar. */
	public static void notifyDenied(ServerPlayer player, Denial denial) {
		long now = player.level().getGameTime();
		Long last = denyMessageTicks.get(player.getUUID());
		if (last != null && now - last < DENY_MESSAGE_COOLDOWN_TICKS) {
			return;
		}
		if (denyMessageTicks.size() > 256) {
			denyMessageTicks.clear();
		}
		denyMessageTicks.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable("lifepath.forge.gated",
				denial.skill().getPath(), denial.requiredLevel()), true);
	}
}
