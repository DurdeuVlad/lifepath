package io.github.durdeuvlad.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import io.github.durdeuvlad.lifepath.config.ConfigSpec;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M4-4 cooldown service: the single owner of the cooldown map — trigger /
 * isOnCooldown / remaining / clear / schedule markers / persistence threshold.
 * Packet sends are player-path and covered by shape; the data-path cores are
 * exercised fully.
 */
class CooldownServiceTest {
	@TempDir
	Path configDir;

	private PlayerCharacterData data;
	private static final Identifier ABILITIES = LifepathMod.id("abilities");
	private static final Identifier AB = LifepathMod.id("flash_freeze");

	@BeforeEach
	void setUp() {
		LifepathConfig.resetForTests();
		data = PlayerCharacterData.createDefault();
	}

	@AfterEach
	void tearDown() {
		LifepathConfig.resetForTests();
		CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
	}

	private void defineAbilitiesConfig() {
		LifepathConfig.define(ABILITIES, ConfigSpec.builder()
				.define("cooldown_multiplier", 1.0, v -> v >= 0 && v <= 100, "")
				.define("persist_min_seconds", 5.0, v -> v >= 0 && v <= 3600, "")
				.build());
	}

	@Test
	void triggerSetsExpiryAndIsOnCooldown() {
		assertFalse(CooldownService.isOnCooldown(data, AB, 1000L));
		assertEquals(0L, CooldownService.remainingMillis(data, AB, 1000L));

		long expiry = CooldownService.trigger(data, AB, 30.0, 1000L);
		assertEquals(31_000L, expiry); // default multiplier 1.0
		assertTrue(CooldownService.isOnCooldown(data, AB, 1000L));
		assertTrue(CooldownService.isOnCooldown(data, AB, 30_999L));
		assertFalse(CooldownService.isOnCooldown(data, AB, 31_000L));
		assertEquals(29_500L, CooldownService.remainingMillis(data, AB, 1_500L));
		assertEquals(0L, CooldownService.remainingMillis(data, AB, 60_000L));
	}

	@Test
	void cooldownMultiplierScalesDuration() throws Exception {
		Files.writeString(configDir.resolve("abilities.toml"),
				"cooldown_multiplier = 0.5\npersist_min_seconds = 5.0\n");
		defineAbilitiesConfig();
		LifepathConfig.loadAll(configDir);

		assertEquals(16_000L, CooldownService.trigger(data, AB, 30.0, 1000L));
	}

	@Test
	void zeroMultiplierDisablesCooldowns() throws Exception {
		Files.writeString(configDir.resolve("abilities.toml"),
				"cooldown_multiplier = 0.0\npersist_min_seconds = 5.0\n");
		defineAbilitiesConfig();
		LifepathConfig.loadAll(configDir);

		data.setCooldown(AB, 999_999L);
		assertEquals(-1L, CooldownService.trigger(data, AB, 30.0, 1000L));
		assertFalse(CooldownService.isOnCooldown(data, AB, 1000L),
				"multiplier 0 must remove any existing cooldown");
	}

	@Test
	void clearRemovesOneCooldownButNeverScheduleMarkers() {
		data.setCooldown(AB, 50_000L);
		Identifier marker = CooldownService.scheduleKey(AB);
		data.setCooldown(marker, 50_000L);

		assertFalse(CooldownService.clear(data, marker),
				"schedule markers are engine bookkeeping, not cooldowns");
		assertTrue(CooldownService.clear(data, AB));
		assertFalse(data.cooldowns().containsKey(AB));
		assertTrue(data.cooldowns().containsKey(marker));
	}

	@Test
	void clearAllDropsOnlyRealCooldowns() {
		data.setCooldown(AB, 50_000L);
		data.setCooldown(LifepathMod.id("other"), 60_000L);
		Identifier marker = CooldownService.scheduleKey(AB);
		data.setCooldown(marker, 50_000L);

		assertEquals(2, CooldownService.clearAll(data));
		assertTrue(data.cooldowns().isEmpty()
				|| data.cooldowns().keySet().stream()
						.allMatch(id -> id.equals(marker)));
	}

	@Test
	void scheduleMarkersRoundTrip() {
		assertNull(CooldownService.nextDueAt(data, AB));
		CooldownService.markNextDue(data, AB, 42_000L);
		assertEquals(42_000L, CooldownService.nextDueAt(data, AB));
		// The marker key namespaces the ability: ns/path preserved losslessly.
		assertEquals(LifepathMod.id("schedule/lifepath/flash_freeze"),
				CooldownService.scheduleKey(AB));
	}

	@Test
	void sanitizeDropsBelowThresholdCooldowns() throws Exception {
		Files.writeString(configDir.resolve("abilities.toml"),
				"cooldown_multiplier = 1.0\npersist_min_seconds = 5.0\n");
		defineAbilitiesConfig();
		LifepathConfig.loadAll(configDir);

		long now = 100_000L;
		data.setCooldown(AB, now + 3_000L);              // 3s left — below 5s threshold
		data.setCooldown(LifepathMod.id("long_cd"), now + 60_000L); // survives
		Identifier marker = CooldownService.scheduleKey(AB);
		data.setCooldown(marker, now + 1_000L);          // bookkeeping — exempt

		CharacterPersistence.sanitize(data, now);

		assertFalse(data.cooldowns().containsKey(AB));
		assertTrue(data.cooldowns().containsKey(LifepathMod.id("long_cd")));
		assertTrue(data.cooldowns().containsKey(marker));
	}

	@Test
	void sanitizeDropsOfflineElapsedCooldowns() throws Exception {
		defineAbilitiesConfig();
		LifepathConfig.loadAll(configDir);

		// Saved with 30s remaining; the player relogged 40s later — expired.
		long savedNow = 100_000L;
		data.setCooldown(AB, savedNow + 30_000L);
		CharacterPersistence.sanitize(data, savedNow + 40_000L);
		assertFalse(data.cooldowns().containsKey(AB));
	}
}
