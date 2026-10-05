package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Data definition of a skill — something the character can actually practice
 * (GAMEDESIGN §8/§19). Level curve, XP sources and passive scaling are
 * references into registries owned by later milestones; this record only
 * carries the structure.
 */
public record SkillDefinition(
		ResourceLocation id,
		String displayName,
		String description,
		String improveHint,
		Category category,
		int maxLevel,
		Optional<ResourceLocation> levelCurve,
		List<Milestone> milestones,
		List<ResourceLocation> xpSources,
		Optional<ResourceLocation> passiveScaling,
		Optional<ResourceLocation> icon) {

	/** Back-compatible constructor for call sites predating {@code icon} (M12-1). */
	public SkillDefinition(ResourceLocation id, String displayName, String description,
			String improveHint, Category category, int maxLevel,
			Optional<ResourceLocation> levelCurve, List<Milestone> milestones,
			List<ResourceLocation> xpSources, Optional<ResourceLocation> passiveScaling) {
		this(id, displayName, description, improveHint, category, maxLevel,
				levelCurve, milestones, xpSources, passiveScaling, Optional.empty());
	}

	/** Back-compatible constructor for call sites that don't set UI strings. */
	public SkillDefinition(ResourceLocation id, String displayName, Category category,
			int maxLevel, Optional<ResourceLocation> levelCurve, List<Milestone> milestones,
			List<ResourceLocation> xpSources, Optional<ResourceLocation> passiveScaling) {
		this(id, displayName, "", "", category, maxLevel, levelCurve, milestones,
				xpSources, passiveScaling);
	}

	/** Broad grouping used for UI and balance bucketing. */
	public enum Category {
		GATHERING, CRAFTING, PHYSICAL, KNOWLEDGE;

		public static final Codec<Category> CODEC = Codec.STRING.xmap(
				name -> Category.valueOf(name.toUpperCase(Locale.ROOT)),
				category -> category.name().toLowerCase(Locale.ROOT));
	}

	/** A named level breakpoint: display-text key + references to effects (M4+). */
	public record Milestone(int level, String descriptionKey, List<ResourceLocation> effectRefs) {
		public static final Codec<Milestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(1, 10000).fieldOf("level").forGetter(Milestone::level),
				Codec.STRING.optionalFieldOf("description_key", "").forGetter(Milestone::descriptionKey),
				ResourceLocation.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Milestone::effectRefs)
		).apply(instance, Milestone::new));
	}

	public static SkillDefinition fromFile(ResourceLocation id, SkillDefinitionFile file) {
		return new SkillDefinition(id, file.displayName(), file.description(),
				file.improveHint(), file.category(), file.maxLevel(),
				file.levelCurve(), file.milestones(), file.xpSources(), file.passiveScaling(),
				file.icon().map(raw -> IconRef.resolve("skill", id, raw)));
	}

	/** JSON shape of {@code data/<ns>/skill/<name>.json} (id excluded). */
	public record SkillDefinitionFile(
			String displayName,
			String description,
			String improveHint,
			Category category,
			int maxLevel,
			Optional<ResourceLocation> levelCurve,
			List<Milestone> milestones,
			List<ResourceLocation> xpSources,
			Optional<ResourceLocation> passiveScaling,
			Optional<String> icon) {

		public static final Codec<SkillDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SkillDefinitionFile::displayName),
				Codec.STRING.optionalFieldOf("description", "").forGetter(SkillDefinitionFile::description),
				Codec.STRING.optionalFieldOf("improve_hint", "").forGetter(SkillDefinitionFile::improveHint),
				Category.CODEC.fieldOf("category").forGetter(SkillDefinitionFile::category),
				Codec.intRange(1, 10000).optionalFieldOf("max_level", 100).forGetter(SkillDefinitionFile::maxLevel),
				ResourceLocation.CODEC.optionalFieldOf("level_curve").forGetter(SkillDefinitionFile::levelCurve),
				Milestone.CODEC.listOf().optionalFieldOf("milestones", List.of()).forGetter(SkillDefinitionFile::milestones),
				ResourceLocation.CODEC.listOf().optionalFieldOf("xp_sources", List.of()).forGetter(SkillDefinitionFile::xpSources),
				ResourceLocation.CODEC.optionalFieldOf("passive_scaling").forGetter(SkillDefinitionFile::passiveScaling),
				Codec.STRING.optionalFieldOf("icon").forGetter(SkillDefinitionFile::icon)
		).apply(instance, SkillDefinitionFile::new));
	}
}
