package io.github.durdeuvlad.lifepath.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-skill progression state owned by a character (GAMEDESIGN §18). An
 * immutable snapshot record — progression INVARIANTS (level within
 * [0, maxLevel], {@code highestLevel} monotone non-decreasing,
 * {@code protectedFloor} within [0, level]) are enforced by
 * {@link SkillService}, which produces clamped copies; the record itself is
 * pure data.
 */
public record SkillProgress(
		double xp,
		int level,
		int highestLevel,
		int protectedFloor,
		Aptitude aptitude,
		long lastMeaningfulUse) {

	public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(SkillProgress::xp),
			Codec.INT.optionalFieldOf("level", 0).forGetter(SkillProgress::level),
			Codec.INT.optionalFieldOf("highest_level", 0).forGetter(SkillProgress::highestLevel),
			Codec.INT.optionalFieldOf("protected_floor", 0).forGetter(SkillProgress::protectedFloor),
			Aptitude.CODEC.optionalFieldOf("aptitude", Aptitude.B).forGetter(SkillProgress::aptitude),
			Codec.LONG.optionalFieldOf("last_meaningful_use", 0L).forGetter(SkillProgress::lastMeaningfulUse)
	).apply(instance, SkillProgress::new));

	/** Zero-progress record for a skill the character has never practiced. */
	public static SkillProgress fresh(Aptitude aptitude) {
		return new SkillProgress(0.0, 0, 0, 0, aptitude, 0L);
	}
}
