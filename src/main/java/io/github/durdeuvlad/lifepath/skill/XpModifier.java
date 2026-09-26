package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
 * content identity beyond the fields of {@link XpContext}.
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
	record XpContext(@Nullable ServerPlayerEntity player, Identifier skillId,
			SkillProgress progress, ActivityEvent source,
			@Nullable Identifier speciesId, @Nullable Identifier specializationId,
			@Nullable io.github.durdeuvlad.lifepath.character.PlayerCharacterData data) {
	}
}
