package io.github.durdeuvlad.lifepath.event;

import java.util.Map;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A normalized domain event describing one gameplay activity (TIMELINE §5,
 * M2-3). Everything the XP service and later anti-exploit systems need, and
 * nothing mod-specific: vanilla and compat producers both emit this shape.
 *
 * <p><b>Subtype choice:</b> activities are DATA-TAGGED INSTANCES, not Java
 * subclasses — a new activity kind is a new {@code type} id + attributes, not
 * a new class. Named shapes (mining/farming/smithing/fishing/crafting/combat)
 * are built by {@link ActivityEvents} factories, which fill the documented
 * attribute keys so producers stay consistent.
 *
 * <p><b>Anti-exploit metadata:</b> {@link #repetitionSignature()} is a stable
 * per-(type, sourceId) key — {@code mining|minecraft:stone} vs
 * {@code mining|minecraft:diamond_ore} — that M3-4 diminishing-returns windows
 * bucket on; {@link #cause()} distinguishes player-caused actions from
 * ambient/unknown sources; {@link #timestamp()} supports time windows.
 */
public record ActivityEvent(
		@Nullable ServerPlayerEntity player,
		Identifier type,
		Identifier sourceId,
		Set<Identifier> tags,
		Cause cause,
		long timestamp,
		Map<String, String> attributes) {

	public enum Cause {
		/** A specific player performed the action. Should carry a non-null {@link #player}. */
		PLAYER,
		/** A non-player source (explosion, water flow, mob, automation). */
		NON_PLAYER,
		/** The game does not expose who caused it — treat as untrusted. */
		UNKNOWN,
		/** Not gameplay at all: admin commands / direct setter paths. */
		SYSTEM
	}

	public ActivityEvent {
		tags = Set.copyOf(tags);
		attributes = Map.copyOf(attributes);
		java.util.Objects.requireNonNull(type, "type");
		java.util.Objects.requireNonNull(sourceId, "sourceId");
		java.util.Objects.requireNonNull(cause, "cause");
	}

	/** Stable repetition bucket: identical for identical (type, sourceId) events. */
	public String repetitionSignature() {
		return type + "|" + sourceId;
	}

	/** Convenience for data paths/tests where no player is attached. */
	public static ActivityEvent of(Identifier type, Identifier sourceId) {
		return new ActivityEvent(null, type, sourceId, Set.of(), Cause.UNKNOWN, 0L, Map.of());
	}

	/** Source marker for non-activity mutations (admin set-level/set-xp). */
	public static ActivityEvent admin(Identifier commandId) {
		return new ActivityEvent(null, commandId, commandId, Set.of(), Cause.SYSTEM,
				System.currentTimeMillis(), Map.of());
	}
}
