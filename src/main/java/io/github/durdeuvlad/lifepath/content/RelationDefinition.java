package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A data-defined mob-disposition table (M5-4, GAMEDESIGN §15): how mobs treat
 * a character. Referenced by {@code species.mob_dispositions}; absent on a
 * species means vanilla targeting. Rules are evaluated in order — first match
 * wins — so narrow exceptions can precede broad tags.
 * Content lives in {@code data/<ns>/relation/<name>.json}.
 */
public record RelationDefinition(Identifier id, List<Rule> rules) {

	/** Mob stance toward the character. */
	public enum Disposition {
		/** Never target the character. */
		NEUTRAL,
		/** At least as non-hostile as neutral — targeting is suppressed. */
		FRIENDLY,
		/** Explicitly hostile — vanilla targeting applies (documents intent). */
		HOSTILE;

		public static final Codec<Disposition> CODEC = Codec.STRING.xmap(
				s -> Disposition.valueOf(s.toUpperCase(Locale.ROOT)),
				d -> d.name().toLowerCase(Locale.ROOT));

		/** Does this stance suppress the mob's targeting of the character? */
		public boolean suppressesTargeting() {
			return this != HOSTILE;
		}
	}

	/** {@code entity} (id or {@code #tag}) → disposition. */
	public record Rule(IdTagRef entity, Disposition disposition) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				IdTagRef.CODEC.fieldOf("entity").forGetter(Rule::entity),
				Disposition.CODEC.optionalFieldOf("disposition", Disposition.NEUTRAL)
						.forGetter(Rule::disposition))
				.apply(i, Rule::new));

		public boolean matches(Identifier typeId,
				@Nullable RegistryEntry<EntityType<?>> entry) {
			return entity.matches(typeId, entry, RegistryKeys.ENTITY_TYPE);
		}
	}

	public static RelationDefinition fromFile(Identifier id, RelationFile file) {
		if (file.rules().isEmpty()) {
			throw new IllegalArgumentException(
					"relation " + id + " must declare at least one rule");
		}
		return new RelationDefinition(id, file.rules());
	}

	/** First matching rule's disposition, or null when none apply. */
	@Nullable
	public Disposition dispositionFor(Identifier typeId,
			@Nullable RegistryEntry<EntityType<?>> entry) {
		for (Rule rule : rules) {
			if (rule.matches(typeId, entry)) {
				return rule.disposition();
			}
		}
		return null;
	}

	/** {@code {"rules": [{"entity": "#lifepath:undead", "disposition": "neutral"}]}} */
	public record RelationFile(List<Rule> rules) {
		public static final Codec<RelationFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				Rule.CODEC.listOf().fieldOf("rules").forGetter(RelationFile::rules)
		).apply(i, RelationFile::new));
	}
}
