package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.content.LevelCurveDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Lookup and evaluation for {@link LevelCurveDefinition}s. Skills reference a
 * curve by id ({@code level_curve} in the definition file); an absent
 * reference resolves to {@code lifepath:default}. A missing/broken curve file
 * degrades to {@link #FALLBACK} with one WARN per id — XP math never crashes.
 */
public final class LevelCurves {
	private LevelCurves() {
	}

	/** Curve used when a definition references nothing (or nothing resolves). */
	public static final ResourceLocation DEFAULT_ID = LifepathMod.id("default");

	/**
	 * Last-resort table if even {@code lifepath:default} fails to load.
	 * Deliberately SHORT (6 levels) and NOT a copy of default.json — a missing
	 * shipped curve is a packaging bug; this keeps the game playable and the
	 * divergence loud in behavior, not silently identical.
	 */
	private static final LevelCurveDefinition FALLBACK = new LevelCurveDefinition(
			DEFAULT_ID, List.of(0.0, 40.0, 120.0, 300.0, 600.0, 1000.0));

	private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
	private static final int WARNED_CAP = 256;

	/** The curve a skill resolves to (its {@code level_curve} ref, else default, else fallback). */
	public static LevelCurveDefinition forSkill(SkillDefinition skill) {
		ResourceLocation ref = skill.levelCurve().orElse(DEFAULT_ID);
		return curve(ref).orElse(FALLBACK);
	}

	/** Looks up a curve by id; {@code empty} + WARN-once for unknown/missing ids. */
	public static Optional<LevelCurveDefinition> curve(@Nullable ResourceLocation id) {
		if (id == null) {
			return Optional.empty();
		}
		LevelCurveDefinition def = LifepathContent.levelCurves().get(id);
		if (def == null) {
			if (WARNED.add(id)) {
				if (WARNED.size() >= WARNED_CAP) {
					WARNED.clear();
					WARNED.add(id);
					LifepathMod.LOGGER.warn(
							"unknown level curve '{}' (warn set reset at cap {})", id, WARNED_CAP);
				} else {
					LifepathMod.LOGGER.warn("unknown level curve '{}' (no definition loaded)", id);
				}
			}
			return Optional.empty();
		}
		return Optional.of(def);
	}

	/** Level for {@code xp} on the given curve (missing curve → fallback). Never negative. */
	public static int levelFor(@Nullable ResourceLocation curveId, double xp) {
		return curve(curveId).orElse(FALLBACK).levelFor(xp);
	}

	/** Level for {@code xp} on a skill's own curve (missing curve → fallback). */
	public static int levelForSkill(SkillDefinition skill, double xp) {
		return forSkill(skill).levelFor(xp);
	}

	/** Total XP needed to reach {@code level} on the given curve (missing → fallback). */
	public static double xpForLevel(@Nullable ResourceLocation curveId, int level) {
		return curve(curveId).orElse(FALLBACK).xpForLevel(level);
	}

	/** Test hook: clears the warn-once set. Not for production use. */
	static void resetForTests() {
		WARNED.clear();
	}
}
