package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Data-defined mapping from an {@code ActivityEvent} to an XP award
 * (TIMELINE §5, M2-3). The XP service never sees raw gameplay — source files
 * turn normalized events into awards:
 *
 * <pre>{@code
 * {
 *   "activity": "lifepath:mining",
 *   "skill": "lifepath:mining",
 *   "player_caused_only": true,
 *   "base_xp": 0.5,
 *   "per_subject": { "minecraft:diamond_ore": 4.0 },
 *   "required_tags": [],
 *   "excluded_subjects": []
 * }
 * }</pre>
 *
 * <p>Matching: event {@code type} must equal {@code activity}; when
 * {@code playerCausedOnly} the event's cause must be {@code PLAYER};
 * {@code requiredTags} must all be present on the event; an
 * {@code excludedSubjects} hit vetoes the award. Amount = per-subject
 * override else {@code baseXp}; {@code <= 0} means no award.
 */
public record XpSourceDefinition(
		Identifier id,
		Identifier activity,
		Identifier skill,
		boolean playerCausedOnly,
		double baseXp,
		Map<Identifier, Double> perSubject,
		Set<Identifier> requiredTags,
		Set<Identifier> excludedSubjects) {

	public static XpSourceDefinition fromFile(Identifier id, XpSourceFile file) {
		return new XpSourceDefinition(id, file.activity(), file.skill(),
				file.playerCausedOnly(), file.baseXp(),
				Map.copyOf(file.perSubject()), Set.copyOf(file.requiredTags()),
				Set.copyOf(file.excludedSubjects()));
	}

	/** Whether this source matches the event (type, cause, tags, exclusions). */
	public boolean matches(io.github.durdeuvlad.lifepath.event.ActivityEvent event) {
		if (!activity.equals(event.type())) {
			return false;
		}
		if (playerCausedOnly && event.cause() != io.github.durdeuvlad.lifepath.event.ActivityEvent.Cause.PLAYER) {
			return false;
		}
		if (excludedSubjects.contains(event.sourceId())) {
			return false;
		}
		return event.tags().containsAll(requiredTags);
	}

	/** The unmodified XP amount this source yields for {@code sourceId}. */
	public double amountFor(Identifier sourceId) {
		return perSubject.getOrDefault(sourceId, baseXp);
	}

	public record XpSourceFile(
			Identifier activity,
			Identifier skill,
			boolean playerCausedOnly,
			double baseXp,
			Map<Identifier, Double> perSubject,
			List<Identifier> requiredTags,
			List<Identifier> excludedSubjects) {

		public static final Codec<XpSourceFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("activity").forGetter(XpSourceFile::activity),
				Identifier.CODEC.fieldOf("skill").forGetter(XpSourceFile::skill),
				Codec.BOOL.optionalFieldOf("player_caused_only", false).forGetter(XpSourceFile::playerCausedOnly),
				Codec.doubleRange(0.0, 1e15).optionalFieldOf("base_xp", 0.0).forGetter(XpSourceFile::baseXp),
				Codec.unboundedMap(Identifier.CODEC, Codec.doubleRange(0.0, 1e15))
						.optionalFieldOf("per_subject", Map.of()).forGetter(XpSourceFile::perSubject),
				Identifier.CODEC.listOf().optionalFieldOf("required_tags", List.of())
						.forGetter(XpSourceFile::requiredTags),
				Identifier.CODEC.listOf().optionalFieldOf("excluded_subjects", List.of())
						.forGetter(XpSourceFile::excludedSubjects)
		).apply(instance, XpSourceFile::new));
	}
}
