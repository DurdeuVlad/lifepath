package io.github.durdeuvlad.lifepath.character.persistence;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.CooldownService;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.character.migration.CharacterMigrations;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.github.durdeuvlad.lifepath.skill.SkillService;
import io.github.durdeuvlad.lifepath.util.Serialization;
import java.nio.file.Files;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Serialization boundary for {@link PlayerCharacterData}: raw NBT in,
 * validated model out.
 *
 * <p>Persistence mechanism: a {@code copyOnDeath} Fabric entity attachment
 * holding raw {@link CompoundTag} (see {@code CharacterAttachments}). Raw NBT
 * is stored rather than a typed attachment so that (a) migrations can reshape
 * the blob before the codec sees it, and (b) a corrupt blob can be quarantined
 * to a backup file instead of breaking player login.
 *
 * <p>Load order: migrate (raw NBT, v&lt;current steps in order) → codec decode →
 * sanitize against {@link ContentIndex} (unknown content IDs dropped with WARN).
 * Any failure after migration → the raw blob is written to
 * {@code <backupDir>/corrupt/<uuid>-<millis>.snbt}, ERROR logged, and fresh
 * defaults returned. A character load never crashes a join.
 */
public final class CharacterPersistence {
	private static volatile ContentIndex contentIndex = ContentIndex.PERMISSIVE;

	private CharacterPersistence() {
	}

	/** Installs the existence view used by sanitization. M1-3 wires real registries here. */
	public static void setContentIndex(ContentIndex index) {
		contentIndex = index;
	}

	public static CompoundTag serialize(PlayerCharacterData data) {
		return (CompoundTag) Serialization.toNbt(PlayerCharacterData.CODEC, data);
	}

	/**
	 * Migrates + decodes + sanitizes. Throws on decode/migration failure.
	 * Older blobs are restamped to {@link LifepathMod#DATA_VERSION}; NEWER
	 * blobs keep their version (matching {@link CharacterMigrations}' forward-compat
	 * rule — a downgrade never rewrites data it doesn't understand).
	 */
	public static PlayerCharacterData deserialize(CompoundTag raw) {
		CompoundTag migrated = CharacterMigrations.migrate(raw.copy());
		PlayerCharacterData data = Serialization.fromNbt(PlayerCharacterData.CODEC, migrated);
		if (data.dataVersion() < LifepathMod.DATA_VERSION) {
			data.setDataVersion(LifepathMod.DATA_VERSION);
		}
		return sanitize(data, System.currentTimeMillis());
	}

	/**
	 * Deep copy via codec round-trip — no migration (input is a live model,
	 * already current) and no sanitize (copies must not prune references or
	 * emit sanitize WARNs; used for sync snapshots and cache copies).
	 */
	public static PlayerCharacterData copy(PlayerCharacterData data) {
		return Serialization.fromNbt(PlayerCharacterData.CODEC, serialize(data));
	}

	/** {@link #deserialize} with graceful degradation: backup + fresh defaults + ERROR on failure. */
	public static PlayerCharacterData loadSafe(CompoundTag raw, UUID owner, @Nullable Path backupDir) {
		try {
			return deserialize(raw);
		} catch (Exception e) {
			Path backup = writeBackup(raw, owner, backupDir);
			LifepathMod.LOGGER.error("corrupt character data for {} (backup: {}); loading fresh defaults",
					owner, backup, e);
			return PlayerCharacterData.createDefault();
		}
	}

	/** Drops every content reference the index says is gone, WARNing per entry. */
	public static PlayerCharacterData sanitize(PlayerCharacterData data) {
		return sanitize(data, System.currentTimeMillis());
	}

	/**
	 * {@link #sanitize} with an explicit {@code nowMs} — ability cooldowns
	 * whose remaining time is &le; {@code abilities.toml persist_min_seconds}
	 * (default 5s) do not survive relog (M4-4); cooldowns that expired while
	 * the player was offline fall under the same rule.
	 */
	public static PlayerCharacterData sanitize(PlayerCharacterData data, long nowMs) {
		boolean speciesDropped = false;
		if (unknown("species", data.speciesId())) {
			drop("species", data.speciesId());
			data.setSpeciesId(null);
			speciesDropped = true;
		}
		if (unknown("specialization", data.specializationId())) {
			drop("specialization", data.specializationId());
			data.setSpecializationId(null);
		}
		// Morph feature (M-1): a deleted morph_form clears the whole morph
		// state — an unknown form can neither render nor stat correctly. The
		// same drop applies when the species was just dropped: morph only
		// exists under a species that carries the morph ability, so a
		// species-less character must not keep an active disguise.
		if (data.morph() != null && (speciesDropped
				|| unknown("morph_form", data.morph().formId()))) {
			drop("morph_form", data.morph().formId());
			data.setMorph(null);
		}
		for (ResourceLocation id : new ArrayList<>(data.skills().keySet())) {
			if (unknown("skill", id)) {
				drop("skill", id);
				data.removeSkill(id);
			} else {
				// Known skill: repair broken invariants (level/floor/xp drift,
				// NaN, def maxLevel lowered since last save) at the load boundary.
				SkillProgress fixed = SkillService.clamped(id, data.skill(id));
				if (!fixed.equals(data.skill(id))) {
					data.setSkillProgress(id, fixed);
				}
			}
		}
		for (ResourceLocation id : new ArrayList<>(data.resources().keySet())) {
			if (unknown("resource", id)) {
				drop("resource", id);
				data.removeResource(id);
				continue;
			}
			// Known resource: repair non-finite values and out-of-bounds drift
			// (a def reload may have shrunk [min,max] under a stored value) at
			// the load boundary — NaN would brick modify() permanently.
			var s = data.resources().get(id);
			var def = io.github.durdeuvlad.lifepath.registry.LifepathContent
					.resources().get(id);
			double lo = def != null ? def.min() : s.min();
			double hi = def != null ? def.max() : s.max();
			double cur = s.current();
			double repaired = !Double.isFinite(cur)
					? (def != null ? def.defaultValue() : s.min())
					: Math.max(lo, Math.min(hi, cur));
			if (repaired != cur) {
				data.setResource(id,
						new PlayerCharacterData.ResourceState(repaired, lo, hi));
				LifepathMod.LOGGER.warn(
						"repaired out-of-range resource {} ({} -> {})", id, cur, repaired);
			}
		}
		long cooldownMinPersistMs = (long) (LifepathConfig.getOrDefault(
				LifepathMod.id("abilities"), "persist_min_seconds", 5.0) * 1000.0);
		for (ResourceLocation id : new ArrayList<>(data.cooldowns().keySet())) {
			// `schedule/*` keys are the ability engine's passive-eval markers —
			// engine bookkeeping, not ability references (M4-1).
			if (CooldownService.isScheduleKey(id)) {
				continue;
			}
			if (unknown("ability", id)) {
				drop("ability", id);
				data.removeCooldown(id);
				continue;
			}
			// M4-4: short-lived cooldowns don't persist — remaining <= the
			// configured threshold at load means the entry is dropped.
			Long expiry = data.cooldowns().get(id);
			if (expiry == null || expiry <= nowMs + cooldownMinPersistMs) {
				data.removeCooldown(id);
			}
		}
		for (PlayerCharacterData.ListKind list : PlayerCharacterData.ListKind.values()) {
			String domain = switch (list) {
				case TRAITS -> "trait";
				case CONDITIONS -> "condition";
				case ATTUNEMENTS -> "attunement";
				case UNLOCKS -> "unlock_content";
			};
			for (ResourceLocation id : new ArrayList<>(data.list(list))) {
				if (unknown(domain, id)) {
					drop(domain, id);
					data.removeId(list, id);
				}
			}
		}
		return data;
	}

	private static boolean unknown(String domain, @Nullable ResourceLocation id) {
		return id != null && !contentIndex.exists(domain, id);
	}

	private static void drop(String domain, ResourceLocation id) {
		LifepathMod.LOGGER.warn("dropping reference to missing {} definition {}", domain, id);
	}

	/**
	 * Writes {@code raw} to {@code <backupDir>/corrupt/<uuid>-<nanos>.snbt} for
	 * manual recovery. Returns the written path, or null if {@code backupDir}
	 * is null or the write fails (logged).
	 */
	public static Path writeBackup(CompoundTag raw, UUID owner, @Nullable Path backupDir) {
		if (backupDir == null) {
			return null;
		}
		try {
			Path dir = backupDir.resolve("corrupt");
			Files.createDirectories(dir);
			Path file = dir.resolve(owner + "-" + System.nanoTime() + ".snbt");
			Files.writeString(file, raw.toString());
			return file;
		} catch (Exception e) {
			LifepathMod.LOGGER.error("failed to write corrupt-data backup for {}", owner, e);
			return null;
		}
	}
}
