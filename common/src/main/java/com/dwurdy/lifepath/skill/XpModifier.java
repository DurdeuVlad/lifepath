package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.event.ActivityEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * One stage in the XP modifier pipeline (TIMELINE §5, M2-2). Registered under
 * an id via {@code SkillXpService.registerModifier}; applied in registration
 * order to every award. Implementations (aptitude scaling, specialization
 * tables, diminishing returns) land in M3 — this is the seam.
 *
 * <p>Contract: return the adjusted amount ({@code amount} replaced or scaled).
 * Return {@code <= 0} to suppress the XP gain entirely (the action still counts
 * as meaningful use — practice happened, it just yielded nothing).
 * Implementations must be deterministic, side-effect-free, and must not read
 * content identity beyond the fields of {@link XpContext}. Built-in modifiers
 * may perform bookkeeping writes through {@code XpContext.data()} (the
 * diminishing-returns signature ledger); custom modifiers should stay
 * side-effect-free.
 */
@FunctionalInterface
public interface XpModifier {
	double apply(XpContext context, double amount);

	/**
	 * View of the award being processed. {@code player} is null on the
	 * data-only path; {@code data} is the live character model — read-only by
	 * convention EXCEPT for sanctioned bookkeeping writes (the diminishing-
	 * returns signature ledger records itself through it).
	 */
	record XpContext(@Nullable ServerPlayer player, ResourceLocation skillId,
			SkillProgress progress, ActivityEvent source,
			@Nullable ResourceLocation speciesId, @Nullable ResourceLocation specializationId,
			@Nullable com.dwurdy.lifepath.character.PlayerCharacterData data) {
	}
}
