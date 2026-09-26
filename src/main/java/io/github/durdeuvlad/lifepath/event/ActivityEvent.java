package io.github.durdeuvlad.lifepath.event;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Attribution for an XP-producing (or XP-adjacent) action. {@code type} names
 * the activity kind (e.g. {@code lifepath:block_broken}); {@code subject}
 * names what it acted on (e.g. {@code minecraft:stone}), nullable for
 * activities without a target. Producers are added by the M2-3..6 XP sources;
 * this record is the generic seam the XP service logs and modifiers read.
 */
public record ActivityEvent(Identifier type, @Nullable Identifier subject) {

	public static ActivityEvent of(Identifier type, @Nullable Identifier subject) {
		return new ActivityEvent(type, subject);
	}

	/** Source marker for non-activity mutations (admin set-level/set-xp). */
	public static ActivityEvent admin(Identifier commandId) {
		return new ActivityEvent(commandId, null);
	}
}
