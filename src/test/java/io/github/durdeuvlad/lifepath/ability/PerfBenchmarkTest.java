package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M7-3 profiling harness — the passive-sweep hot loop measured over
 * synthetic load (N characters × M passives). Numbers print to stdout and
 * feed docs/PERFORMANCE.md; nothing here asserts on wall time, so CI noise
 * can't flake it. World-dependent paths ({@code getBlockState} scans)
 * return early on a null player — measured cost is loop + matcher +
 * schedule-marker overhead; the world-scan bound is documented separately.
 */
class PerfBenchmarkTest {

	private static final int PLAYERS = 5;
	private static final int PASSIVES = 12;

	@BeforeEach
	void setUp() {
		LifepathContent.abilities().clear();
		LifepathContent.species().clear();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		// Passive that runs real vocabulary work: a fail-closed condition
		// (null self) after matcher construction — representative of a
		// condition-gated passive's non-world cost.
		List<ResourceLocation> passives = new ArrayList<>();
		for (int i = 0; i < PASSIVES; i++) {
			ResourceLocation id = LifepathMod.id("bench_p" + i);
			AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
					.parse(JsonOps.INSTANCE, JsonParser.parseString("""
							{"display_name": "B", "trigger": {"type": "passive", "interval_ticks": 20},
							 "target": {"type": "lifepath:self"},
							 "conditions": {"all": [{"type": "lifepath:block_nearby",
							                 "block": "#minecraft:flowers", "radius": 8}]},
							 "actions": [{"type": "lifepath:grant_xp", "skill": "lifepath:mining", "xp": 1}]}
							"""))
					.result().orElseThrow();
			LifepathContent.abilities().register(id, AbilityDefinition.fromFile(id, file));
			passives.add(id);
		}
		ResourceLocation species = LifepathMod.id("bench_species");
		LifepathContent.species().register(species, new SpeciesDefinition(
				species, "Bench", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.OPEN, passives, List.of(),
				java.util.Map.of(), List.of(),
				java.util.Optional.empty(), java.util.Optional.empty()));
	}

	@Test
	void passiveSweepProfile() {
		List<PlayerCharacterData> chars = new ArrayList<>();
		for (int i = 0; i < PLAYERS; i++) {
			PlayerCharacterData d = PlayerCharacterData.createDefault();
			d.setSpeciesId(LifepathMod.id("bench_species"));
			chars.add(d);
		}
		long now = 1_000_000L;
		// Warmup: JIT + schedule markers seeded.
		for (int w = 0; w < 50; w++) {
			long t = now + w * 1200;
			for (PlayerCharacterData d : chars) {
				AbilityEngine.runPassiveSweep(d, null, t);
			}
		}
		// Measured: 200 sweeps × 5 players × 12 passives.
		long t0 = System.nanoTime();
		for (int w = 0; w < 200; w++) {
			long t = now + 60_000 + w * 1200;
			for (PlayerCharacterData d : chars) {
				AbilityEngine.runPassiveSweep(d, null, t);
			}
		}
		long totalNanos = System.nanoTime() - t0;
		double perPlayerSweepUs = totalNanos / 1000.0 / (200.0 * PLAYERS);
		double perAbilityUs = perPlayerSweepUs / PASSIVES;
		System.out.printf(
				"[PERF] passive_sweep: %d players × %d passives — %.1f us/player-sweep, %.2f us/ability-eval%n",
				PLAYERS, PASSIVES, perPlayerSweepUs, perAbilityUs);
	}
}
