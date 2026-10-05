package com.dwurdy.lifepath.character;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.skill.SkillDecayService;
import com.dwurdy.lifepath.skill.SkillProgress;
import com.dwurdy.lifepath.skill.Aptitude;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * M7-3 profiling harness — sync snapshot + encode cost and the lazy-decay
 * login burst, over synthetic data shaped like a mid-game character
 * (8 skills, 12 cooldowns, 20 action-signature entries, 3 resources).
 */
class PerfSyncBenchmarkTest {

	private static PlayerCharacterData midGameCharacter(long lastSeen) {
		PlayerCharacterData d = PlayerCharacterData.createDefault();
		d.setSpeciesId(LifepathMod.id("sylvian"));
		for (int i = 0; i < 8; i++) {
			d.setSkillProgress(LifepathMod.id("skill_" + i),
					new SkillProgress(10.0 * (40 + i), 40 + i, 40 + i, 0,
							Aptitude.B, lastSeen));
		}
		for (int i = 0; i < 12; i++) {
			d.setCooldown(LifepathMod.id("cd_" + i), lastSeen + 600_000);
		}
		for (int i = 0; i < 20; i++) {
			d.setActionTimestamps("sig_" + i,
					List.of(lastSeen - 1000, lastSeen - 2000));
		}
		for (int i = 0; i < 3; i++) {
			d.setResource(LifepathMod.id("res_" + i),
					new PlayerCharacterData.ResourceState(50.0, 0.0, 100.0));
		}
		return d;
	}

	@Test
	void syncSnapshotProfile() {
		long now = 2_000_000L;
		PlayerCharacterData d = midGameCharacter(now - 300_000);
		// Warmup.
		for (int i = 0; i < 50; i++) {
			CharacterManager.snapshotForSync(d, now);
		}
		long t0 = System.nanoTime();
		int bytes = 0;
		for (int i = 0; i < 500; i++) {
			PlayerCharacterData snap = CharacterManager.snapshotForSync(d, now);
			bytes = PlayerCharacterData.CODEC
					.encodeStart(NbtOps.INSTANCE, snap).result()
					.map(Tag::sizeInBytes).orElse(0);
		}
		double usPerSync = (System.nanoTime() - t0) / 1000.0 / 500.0;
		System.out.printf("[PERF] sync_snapshot: %.1f us/snapshot+encode, payload=%d bytes%n",
				usPerSync, bytes);
	}

	@Test
	void decayLoginBurstProfile() {
		// 5 players logging in after 30 offline days — worst-case burst.
		long now = 2_000_000_000L;
		long lastSeen = now - 30L * 24 * 3600 * 1000;
		List<PlayerCharacterData> chars = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			chars.add(midGameCharacter(lastSeen));
		}
		for (int w = 0; w < 20; w++) {
			for (PlayerCharacterData d : chars) {
				SkillDecayService.applyLazyAll(d, now);
			}
		}
		long t0 = System.nanoTime();
		for (int i = 0; i < 200; i++) {
			for (PlayerCharacterData d : chars) {
				SkillDecayService.applyLazyAll(d, now);
			}
		}
		double usPerPlayer = (System.nanoTime() - t0) / 1000.0 / (200.0 * 5);
		System.out.printf("[PERF] decay_login_batch: %.1f us/player (8 skills, 30d offline)%n",
				usPerPlayer);
	}
}
