package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Data definition of a skill — something the character can actually practice
 * (GAMEDESIGN §8/§19). Level curve, XP sources and passive scaling are
 * references into registries owned by later milestones; this record only
 * carries the structure.
 */
public record SkillDefinition(
		Identifier id,
		String displayName,
		Category category,
		int maxLevel,
		Optional<Identifier> levelCurve,
		List<Milestone> milestones,
		Optional<Identifier> xpSources,
		Optional<Identifier> passiveScaling) {

	/** Broad grouping used for UI and balance bucketing. */
	public enum Category {
		GATHERING, CRAFTING, PHYSICAL, KNOWLEDGE;

		public static final Codec<Category> CODEC = Codec.STRING.xmap(
				name -> Category.valueOf(name.toUpperCase(Locale.ROOT)),
				category -> category.name().toLowerCase(Locale.ROOT));
	}

	/** A named level breakpoint: display text + references to effects (M4+). */
	public record Milestone(int level, String description, List<Identifier> effectRefs) {
		public static final Codec<Milestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("level").forGetter(Milestone::level),
				Codec.STRING.optionalFieldOf("description", "").forGetter(Milestone::description),
				Identifier.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Milestone::effectRefs)
		).apply(instance, Milestone::new));
	}

	public static SkillDefinition fromFile(Identifier id, SkillDefinitionFile file) {
		return new SkillDefinition(id, file.displayName(), file.category(), file.maxLevel(),
				file.levelCurve(), file.milestones(), file.xpSources(), file.passiveScaling());
	}

	/** JSON shape of {@code data/<ns>/skill/<name>.json} (id excluded). */
	public record SkillDefinitionFile(
			String displayName,
			Category category,
			int maxLevel,
			Optional<Identifier> levelCurve,
			List<Milestone> milestones,
			Optional<Identifier> xpSources,
			Optional<Identifier> passiveScaling) {

		public static final Codec<SkillDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SkillDefinitionFile::displayName),
				Category.CODEC.fieldOf("category").forGetter(SkillDefinitionFile::category),
				Codec.INT.optionalFieldOf("max_level", 100).forGetter(SkillDefinitionFile::maxLevel),
				Identifier.CODEC.optionalFieldOf("level_curve").forGetter(SkillDefinitionFile::levelCurve),
				Milestone.CODEC.listOf().optionalFieldOf("milestones", List.of()).forGetter(SkillDefinitionFile::milestones),
				Identifier.CODEC.optionalFieldOf("xp_sources").forGetter(SkillDefinitionFile::xpSources),
				Identifier.CODEC.optionalFieldOf("passive_scaling").forGetter(SkillDefinitionFile::passiveScaling)
		).apply(instance, SkillDefinitionFile::new));
	}
}
