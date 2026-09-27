package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Data definition of an acquired condition (M9-1) — a stateful layer grafted
 * onto an existing character (species/spec/skills untouched). Conditions are
 * acquired in play or by admin command, progress through ordered stages, and
 * can be cured — they are never creation-time picks.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code abilities} — always-on ability refs while held. Incoming-damage
 *       "vulnerabilities" are ordinary {@code damage_taken} abilities listed
 *       here — no separate mechanic.
 *   <li>{@code diet_rules} — optional diet-domain override (composes per
 *       {@code DietService}).
 *   <li>{@code resources} — resource ids materialized while held.
 *   <li>{@code stages} — ordered; each stage's {@code abilities} graft
 *       cumulatively. Advancement: {@code advance_events[]} activity-type ids
 *       counted up to {@code advance_count} (default 1), OR
 *       {@code advance_after_seconds} elapsed in-stage — whichever fires
 *       first. The LAST stage's advance fields are ignored.
 *   <li>{@code acquisition} — {@code {type:attack, entity:<id|#tag>,
 *       chance}} | {@code {type:item, item:<id>}} | {@code {type:admin}}.
 *   <li>{@code cures} — {@code {type:item, item:<id>}} | {@code {type:admin}}.
 *       Admin cure is always allowed via command even when unlisted.
 *   <li>{@code transformations} — no bespoke field: a "form" is a stage whose
 *       abilities self-gate on ordinary conditions ({@code night}, …).
 * </ul>
 */
public record ConditionDefinition(
		Identifier id,
		String displayName,
		Optional<String> description,
		List<Identifier> abilities,
		Optional<Identifier> dietRules,
		List<Identifier> resources,
		List<Stage> stages,
		List<AcquisitionRule> acquisition,
		List<CureRule> cures) {

	public ConditionDefinition {
		abilities = List.copyOf(abilities);
		resources = List.copyOf(resources);
		stages = List.copyOf(stages);
		acquisition = List.copyOf(acquisition);
		cures = List.copyOf(cures);
	}

	/** All ability ids grafted at the given stage index (base + cumulative). */
	public List<Identifier> abilitiesAt(int stageIndex) {
		java.util.ArrayList<Identifier> out = new java.util.ArrayList<>(abilities);
		for (int i = 0; i <= stageIndex && i < stages.size(); i++) {
			out.addAll(stages.get(i).abilities());
		}
		return out;
	}

	public int stageCount() {
		return stages.size();
	}

	/**
	 * One progression step. {@code advanceEvents} are activity-type ids;
	 * {@code advanceCount} matching events (or {@code advanceAfterSeconds}
	 * elapsed) moves to the next stage.
	 */
	public record Stage(String id, List<Identifier> abilities,
			List<Identifier> advanceEvents, int advanceCount,
			Optional<Long> advanceAfterSeconds) {

		public Stage {
			abilities = List.copyOf(abilities);
			advanceEvents = List.copyOf(advanceEvents);
		}

		public static final Codec<Stage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("id").forGetter(Stage::id),
				Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Stage::abilities),
				Identifier.CODEC.listOf().optionalFieldOf("advance_events", List.of()).forGetter(Stage::advanceEvents),
				Codec.intRange(1, 100000).optionalFieldOf("advance_count", 1).forGetter(Stage::advanceCount),
				Codec.LONG.optionalFieldOf("advance_after_seconds").forGetter(Stage::advanceAfterSeconds)
		).apply(instance, Stage::new));
	}

	/**
	 * How the condition is gained. {@code type}: {@code attack} (entity id or
	 * {@code #entity_tag} rolls {@code chance} per hostile hit taken),
	 * {@code item} (eating/using {@code item}), {@code admin} (command only).
	 */
	public record AcquisitionRule(String type, Optional<String> entity,
			Optional<Identifier> item, double chance) {

		public static final Codec<AcquisitionRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(AcquisitionRule::type),
				Codec.STRING.optionalFieldOf("entity").forGetter(AcquisitionRule::entity),
				Identifier.CODEC.optionalFieldOf("item").forGetter(AcquisitionRule::item),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(AcquisitionRule::chance)
		).apply(instance, AcquisitionRule::new));
	}

	/** How the condition is removed. {@code type}: {@code item} | {@code admin}. */
	public record CureRule(String type, Optional<Identifier> item) {

		public static final Codec<CureRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(CureRule::type),
				Identifier.CODEC.optionalFieldOf("item").forGetter(CureRule::item)
		).apply(instance, CureRule::new));
	}

	public static ConditionDefinition fromFile(Identifier id, ConditionFile file) {
		return new ConditionDefinition(id, file.displayName(), file.description(),
				file.abilities(), file.dietRules(), file.resources(), file.stages(),
				file.acquisition(), file.cures());
	}

	/** JSON shape of {@code data/<ns>/condition/<name>.json} (id excluded). */
	public record ConditionFile(
			String displayName,
			Optional<String> description,
			List<Identifier> abilities,
			Optional<Identifier> dietRules,
			List<Identifier> resources,
			List<Stage> stages,
			List<AcquisitionRule> acquisition,
			List<CureRule> cures) {

		public static final Codec<ConditionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(ConditionFile::displayName),
				Codec.STRING.optionalFieldOf("description").forGetter(ConditionFile::description),
				Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(ConditionFile::abilities),
				Identifier.CODEC.optionalFieldOf("diet_rules").forGetter(ConditionFile::dietRules),
				Identifier.CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(ConditionFile::resources),
				Stage.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(ConditionFile::stages),
				AcquisitionRule.CODEC.listOf().optionalFieldOf("acquisition", List.of()).forGetter(ConditionFile::acquisition),
				CureRule.CODEC.listOf().optionalFieldOf("cures", List.of()).forGetter(ConditionFile::cures)
		).apply(instance, ConditionFile::new));
	}
}
