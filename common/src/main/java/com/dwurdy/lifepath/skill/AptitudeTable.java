package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.config.LifepathConfig;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * Config-backed grade → multiplier lookup (M3-1). Every balance number lives
 * in {@code config/lifepath/skills.toml} — keys {@code aptitude_<grade>_xp_multiplier}
 * and {@code aptitude_<grade>_decay_multiplier}; the enum itself carries no
 * numbers (contract: balance is config, never constants).
 *
 * <p>Reads are per-call via {@code getOrDefault} — live-reloadable, and a
 * missing/bad key degrades to the spec default rather than crashing an award.
 */
public final class AptitudeTable {
	private AptitudeTable() {
	}

	private static final ResourceLocation SKILLS_CONFIG = LifepathMod.id("skills");

	/** XP multiplier for {@code grade} (config key {@code aptitude_<g>_xp_multiplier}). */
	public static double xpMultiplier(Aptitude grade) {
		return ((Number) LifepathConfig.getOrDefault(SKILLS_CONFIG,
				key(grade, "xp"), defaultXp(grade))).doubleValue();
	}

	/** Decay multiplier for {@code grade} (consumed by M3-3 decay scheduler). */
	public static double decayMultiplier(Aptitude grade) {
		return ((Number) LifepathConfig.getOrDefault(SKILLS_CONFIG,
				key(grade, "decay"), defaultDecay(grade))).doubleValue();
	}

	static String key(Aptitude grade, String kind) {
		return "aptitude_" + grade.name().toLowerCase(Locale.ROOT) + "_" + kind + "_multiplier";
	}

	/** Spec defaults (GAMEDESIGN §8) — used when config is absent or invalid. */
	static double defaultXp(Aptitude grade) {
		return switch (grade) {
			case D -> 0.70;
			case C -> 0.90;
			case B -> 1.00;
			case A -> 1.25;
			case S -> 1.50;
		};
	}

	static double defaultDecay(Aptitude grade) {
		return switch (grade) {
			case D -> 1.25;
			case C -> 1.10;
			case B -> 1.00;
			case A -> 0.80;
			case S -> 0.60;
		};
	}
}
