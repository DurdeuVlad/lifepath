package com.dwurdy.lifepath.content;

import com.dwurdy.lifepath.skill.RankBands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Outcome-scaling rule (M22): maps a skill's rank band to modifiers applied to
 * the activity's OUTPUT — crafted/forged stacks now, gather yields and loot
 * later. Files live under {@code data/<ns>/outcome_rule/*.json}.
 *
 * <pre>
 * {
 *   "skill": "lifepath:smithing",
 *   "activity": "lifepath:smithing",        // optional; absent = any activity
 *   "required_tags": ["lifepath:x"],        // optional, event must carry all
 *   "excluded_subjects": ["minecraft:y"],   // optional sourceId denylist
 *   "bands": {
 *     "untrained": { "output_count_mult": 0.8, "failure_chance": 0.15,
 *                    "failure_count_mult": 0.5, "quality_tier": "crude" },
 *     "master":    { "output_count_mult": 1.25, "quality_tier": "masterwork",
 *                    "sign_items": true }
 *   },
 *   "material_gates": { "lifepath:smithing_tier_steel": 30 }
 * }
 * </pre>
 *
 * <p>Band keys are {@link RankBands.RankBand} names (lowercase). A band absent
 * from the map resolves to the identity modifiers — no scaling for that band.
 * Unknown band keys warn once at load (typo tolerance, like abilities' unknown
 * event subscribers); they never fail the file.
 */
public record OutcomeRuleDefinition(
		ResourceLocation id,
		ResourceLocation skill,
		Optional<ResourceLocation> activity,
		Set<ResourceLocation> subjects,
		Set<ResourceLocation> requiredTags,
		Set<ResourceLocation> excludedSubjects,
		Map<RankBands.RankBand, BandModifiers> bands,
		Map<ResourceLocation, Integer> materialGates,
		int blueprintMinLevel) {

	/** Per-band output modifiers. Absent fields keep defaults (identity). */
	public record BandModifiers(
			double outputCountMult,
			double failureChance,
			double failureCountMult,
			Optional<String> qualityTier,
			boolean signItems,
			double anvilCostMult,
			double junkUpgradeChance) {

		public static final BandModifiers IDENTITY = new BandModifiers(
				1.0, 0.0, 0.5, Optional.empty(), false, 1.0, 0.0);

		public static final Codec<BandModifiers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.doubleRange(0.0, 8.0).optionalFieldOf("output_count_mult", 1.0)
						.forGetter(BandModifiers::outputCountMult),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("failure_chance", 0.0)
						.forGetter(BandModifiers::failureChance),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("failure_count_mult", 0.5)
						.forGetter(BandModifiers::failureCountMult),
				Codec.STRING.optionalFieldOf("quality_tier")
						.forGetter(BandModifiers::qualityTier),
				Codec.BOOL.optionalFieldOf("sign_items", false)
						.forGetter(BandModifiers::signItems),
				Codec.doubleRange(0.0, 4.0).optionalFieldOf("anvil_cost_mult", 1.0)
						.forGetter(BandModifiers::anvilCostMult),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("junk_upgrade_chance", 0.0)
						.forGetter(BandModifiers::junkUpgradeChance))
				.apply(i, BandModifiers::new));
	}

	public static OutcomeRuleDefinition fromFile(ResourceLocation id, OutcomeRuleFile file) {
		EnumMap<RankBands.RankBand, BandModifiers> bands = new EnumMap<>(RankBands.RankBand.class);
		for (var e : file.bands().entrySet()) {
			RankBands.RankBand band;
			try {
				band = RankBands.RankBand.valueOf(e.getKey().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				com.dwurdy.lifepath.LifepathMod.LOGGER.warn(
						"outcome_rule {}: unknown band key '{}' — ignored", id, e.getKey());
				continue;
			}
			bands.put(band, e.getValue());
		}
		return new OutcomeRuleDefinition(id, file.skill(), file.activity(),
				Set.copyOf(file.subjects()), Set.copyOf(file.requiredTags()),
				Set.copyOf(file.excludedSubjects()), Map.copyOf(bands),
				Map.copyOf(file.materialGates()), file.blueprintMinLevel());
	}

	/** Modifiers for a band (absent → identity). */
	public BandModifiers forBand(RankBands.RankBand band) {
		return bands.getOrDefault(band, BandModifiers.IDENTITY);
	}

	/**
	 * Does this rule apply to the event shape? Activity must match when
	 * declared, required tags must be a subset of event tags, and the sourceId
	 * must not be excluded.
	 */
	public boolean matches(ResourceLocation activityId, ResourceLocation sourceId,
			Set<ResourceLocation> eventTags) {
		if (activity.isPresent() && !activity.get().equals(activityId)) {
			return false;
		}
		if (!subjects.isEmpty() && !subjects.contains(sourceId)) {
			return false;
		}
		if (excludedSubjects.contains(sourceId)) {
			return false;
		}
		return eventTags.containsAll(requiredTags);
	}

	/**
	 * Rough specificity for deterministic rule selection: activity-pinned and
	 * tag-filtered rules outrank blanket rules.
	 */
	public int specificity() {
		return subjects.size() * 10 + requiredTags.size()
				+ (activity.isPresent() ? 1 : 0);
	}

	public record OutcomeRuleFile(
			ResourceLocation skill,
			Optional<ResourceLocation> activity,
			List<ResourceLocation> subjects,
			List<ResourceLocation> requiredTags,
			List<ResourceLocation> excludedSubjects,
			Map<String, BandModifiers> bands,
			Map<ResourceLocation, Integer> materialGates,
			int blueprintMinLevel) {

		public static final Codec<OutcomeRuleFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("skill").forGetter(OutcomeRuleFile::skill),
				ResourceLocation.CODEC.optionalFieldOf("activity")
						.forGetter(OutcomeRuleFile::activity),
				ResourceLocation.CODEC.listOf().optionalFieldOf("subjects", List.of())
						.forGetter(OutcomeRuleFile::subjects),
				ResourceLocation.CODEC.listOf().optionalFieldOf("required_tags", List.of())
						.forGetter(OutcomeRuleFile::requiredTags),
				ResourceLocation.CODEC.listOf().optionalFieldOf("excluded_subjects", List.of())
						.forGetter(OutcomeRuleFile::excludedSubjects),
				Codec.unboundedMap(Codec.STRING, BandModifiers.CODEC)
						.fieldOf("bands").forGetter(OutcomeRuleFile::bands),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
						.optionalFieldOf("material_gates", Map.of())
						.forGetter(OutcomeRuleFile::materialGates),
				Codec.INT.optionalFieldOf("blueprint_min_level", -1)
						.forGetter(OutcomeRuleFile::blueprintMinLevel))
				.apply(i, OutcomeRuleFile::new));
	}
}
