package io.github.durdeuvlad.lifepath.skill;

import com.mojang.serialization.Codec;
import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.Locale;

/**
 * Learning aptitude for a skill, ordered worst (D) to best (S). How easily a
 * character learns — not what they know (GAMEDESIGN §8/§18). Set at character
 * creation (species/specialization floors and tables land in later milestones).
 */
public enum Aptitude {
	// DECLARATION ORDER IS SEMANTIC: ordinal() is the grade rank
	// (SkillService.effectiveAptitude compares ordinals for species floors).
	D, C, B, A, S;

	/** Lenient decode: an unknown rank degrades to {@link #B} (the neutral default) with a WARN rather than failing the file. */
	public static final Codec<Aptitude> CODEC = Codec.STRING.xmap(
			name -> {
				try {
					return Aptitude.valueOf(name.toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					LifepathMod.LOGGER.warn("unknown aptitude '{}', defaulting to B", name);
					return Aptitude.B;
				}
			},
			aptitude -> aptitude.name().toLowerCase(Locale.ROOT));
}
