package io.github.durdeuvlad.lifepath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Versioned, server-authoritative model of everything a Lifepath character HAS.
 * This is data, not logic — progression rules live in later milestones.
 *
 * <p>Field shape follows {@code docs/GAMEDESIGN.md} §18. Content references are
 * stored as bare {@link ResourceLocation}s — this class never validates whether a
 * referenced definition exists (that is {@code ContentIndex}'s job, applied by
 * the persistence layer on load).
 *
 * <p><b>Death rule:</b> nothing resets on death. The data is stored in a
 * {@code copyOnDeath} entity attachment (see {@code CharacterAttachments}), so
 * death/respawn, logout/login, restarts and dimension changes all preserve it.
 * A future "reset on death" option would be implemented as an explicit reset
 * invoked by a respawn hook — deliberately not added as dead config.
 */
public final class PlayerCharacterData {
	/** One character resource pool (mana-like meters arrive in later milestones). */
	public record ResourceState(double current, double min, double max) {

		public static final Codec<ResourceState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.optionalFieldOf("current", 0.0).forGetter(ResourceState::current),
				Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(ResourceState::min),
				Codec.DOUBLE.optionalFieldOf("max", 0.0).forGetter(ResourceState::max)
		).apply(instance, ResourceState::new));
	}

	public static final Codec<PlayerCharacterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.optionalFieldOf("species_id").forGetter(d -> Optional.ofNullable(d.speciesId)),
			ResourceLocation.CODEC.optionalFieldOf("specialization_id").forGetter(d -> Optional.ofNullable(d.specializationId)),
			Codec.unboundedMap(ResourceLocation.CODEC, SkillProgress.CODEC)
					.optionalFieldOf("skills", Map.of()).forGetter(d -> d.skills),
			ResourceLocation.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(d -> d.traits),
			// M9-1: condition id -> stage state (v2 shape; v1 string lists are
			// rewritten by the migration chain).
			Codec.unboundedMap(ResourceLocation.CODEC, io.github.durdeuvlad.lifepath.condition.ConditionState.CODEC)
					.optionalFieldOf("conditions", Map.of()).forGetter(d -> d.conditions),
			ResourceLocation.CODEC.listOf().optionalFieldOf("attunements", List.of()).forGetter(d -> d.attunements),
			ResourceLocation.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(d -> d.unlocks),
			Codec.unboundedMap(ResourceLocation.CODEC, ResourceState.CODEC)
					.optionalFieldOf("resources", Map.of()).forGetter(d -> d.resources),
			Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG)
					.optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
					.optionalFieldOf("action_signatures", Map.of()).forGetter(d -> d.actionSignatures),
			Codec.INT.optionalFieldOf("data_version", 0).forGetter(d -> d.dataVersion)
	).apply(instance, PlayerCharacterData::fromCodec));

	@Nullable
	private ResourceLocation speciesId;
	@Nullable
	private ResourceLocation specializationId;
	private final Map<ResourceLocation, SkillProgress> skills = new LinkedHashMap<>();
	private final List<ResourceLocation> traits = new ArrayList<>();
	private final Map<ResourceLocation, io.github.durdeuvlad.lifepath.condition.ConditionState> conditions =
			new LinkedHashMap<>();
	private final List<ResourceLocation> attunements = new ArrayList<>();
	private final List<ResourceLocation> unlocks = new ArrayList<>();
	private final Map<ResourceLocation, ResourceState> resources = new LinkedHashMap<>();
	private final Map<ResourceLocation, Long> cooldowns = new LinkedHashMap<>();
	private final Map<String, List<Long>> actionSignatures = new LinkedHashMap<>();
	private int dataVersion = LifepathMod.DATA_VERSION;

	/** Fresh default data for a brand-new character. */
	public static PlayerCharacterData createDefault() {
		return new PlayerCharacterData();
	}

	/** Restores every field to fresh-default state in place (admin reset). */
	public void reset() {
		speciesId = null;
		specializationId = null;
		skills.clear();
		traits.clear();
		conditions.clear();
		attunements.clear();
		unlocks.clear();
		resources.clear();
		cooldowns.clear();
		actionSignatures.clear();
		dataVersion = LifepathMod.DATA_VERSION;
	}

	private static PlayerCharacterData fromCodec(
			Optional<ResourceLocation> speciesId,
			Optional<ResourceLocation> specializationId,
			Map<ResourceLocation, SkillProgress> skills,
			List<ResourceLocation> traits,
			Map<ResourceLocation, io.github.durdeuvlad.lifepath.condition.ConditionState> conditions,
			List<ResourceLocation> attunements,
			List<ResourceLocation> unlocks,
			Map<ResourceLocation, ResourceState> resources,
			Map<ResourceLocation, Long> cooldowns,
			Map<String, List<Long>> actionSignatures,
			int dataVersion) {
		PlayerCharacterData data = new PlayerCharacterData();
		data.speciesId = speciesId.orElse(null);
		data.specializationId = specializationId.orElse(null);
		data.skills.putAll(skills);
		data.traits.addAll(traits);
		data.conditions.putAll(conditions);
		data.attunements.addAll(attunements);
		data.unlocks.addAll(unlocks);
		data.resources.putAll(resources);
		data.cooldowns.putAll(cooldowns);
		actionSignatures.forEach((sig, times) -> data.actionSignatures.put(sig,
				new ArrayList<>(times.size() > 4096
						? times.subList(times.size() - 4096, times.size()) : times)));
		data.dataVersion = dataVersion;
		return data;
	}

	@Nullable
	public ResourceLocation speciesId() {
		return speciesId;
	}

	public void setSpeciesId(@Nullable ResourceLocation speciesId) {
		this.speciesId = speciesId;
	}

	@Nullable
	public ResourceLocation specializationId() {
		return specializationId;
	}

	public void setSpecializationId(@Nullable ResourceLocation specializationId) {
		this.specializationId = specializationId;
	}

	public Map<ResourceLocation, SkillProgress> skills() {
		return Collections.unmodifiableMap(skills);
	}

	@Nullable
	public SkillProgress skill(ResourceLocation skillId) {
		return skills.get(skillId);
	}

	public void setSkillProgress(ResourceLocation skillId, SkillProgress progress) {
		skills.put(Objects.requireNonNull(skillId), Objects.requireNonNull(progress));
	}

	public void removeSkill(ResourceLocation skillId) {
		skills.remove(skillId);
	}

	public List<ResourceLocation> traits() {
		return Collections.unmodifiableList(traits);
	}

	/** Held condition ids (stage state lives in {@link #conditionState}). */
	public List<ResourceLocation> conditions() {
		return List.copyOf(conditions.keySet());
	}

	@Nullable
	public io.github.durdeuvlad.lifepath.condition.ConditionState conditionState(ResourceLocation id) {
		return conditions.get(id);
	}

	/** Mutable by {@code ConditionService} only — stage state changes are service-owned. */
	public Map<ResourceLocation, io.github.durdeuvlad.lifepath.condition.ConditionState> conditionStates() {
		return Collections.unmodifiableMap(conditions);
	}

	public boolean putCondition(ResourceLocation id,
			io.github.durdeuvlad.lifepath.condition.ConditionState state) {
		return conditions.put(Objects.requireNonNull(id), Objects.requireNonNull(state)) == null;
	}

	public boolean removeCondition(ResourceLocation id) {
		return conditions.remove(id) != null;
	}

	public List<ResourceLocation> attunements() {
		return Collections.unmodifiableList(attunements);
	}

	public List<ResourceLocation> unlocks() {
		return Collections.unmodifiableList(unlocks);
	}

	public boolean addId(ListKind list, ResourceLocation id) {
		// CONDITIONS is a map domain (stage state); the rest are flat lists.
		if (list == ListKind.CONDITIONS) {
			return putCondition(id,
					io.github.durdeuvlad.lifepath.condition.ConditionState
							.fresh(System.currentTimeMillis()));
		}
		List<ResourceLocation> target = list.of(this);
		if (target.contains(id)) {
			return false;
		}
		target.add(id);
		return true;
	}

	public boolean removeId(ListKind list, ResourceLocation id) {
		if (list == ListKind.CONDITIONS) {
			return removeCondition(id);
		}
		return list.of(this).remove(id);
	}

	/** Read-only view of one Identifier-list domain. */
	public List<ResourceLocation> list(ListKind list) {
		if (list == ListKind.CONDITIONS) {
			return List.copyOf(conditions.keySet());
		}
		return Collections.unmodifiableList(list.of(this));
	}

	public Map<ResourceLocation, ResourceState> resources() {
		return Collections.unmodifiableMap(resources);
	}

	public void setResource(ResourceLocation resourceId, ResourceState state) {
		resources.put(Objects.requireNonNull(resourceId), Objects.requireNonNull(state));
	}

	public void removeResource(ResourceLocation resourceId) {
		resources.remove(resourceId);
	}

	public Map<ResourceLocation, Long> cooldowns() {
		return Collections.unmodifiableMap(cooldowns);
	}

	public void setCooldown(ResourceLocation abilityId, long expiryEpochMs) {
		cooldowns.put(Objects.requireNonNull(abilityId), expiryEpochMs);
	}

	public void removeCooldown(ResourceLocation abilityId) {
		cooldowns.remove(abilityId);
	}

	/**
	 * Rolling-window action timestamps per repetition signature (M3-4).
	 * Read view is unmodifiable; mutate via {@link #setActionTimestamps}.
	 */
	public Map<String, List<Long>> actionSignatures() {
		Map<String, List<Long>> view = new LinkedHashMap<>();
		actionSignatures.forEach((k, v) -> view.put(k, Collections.unmodifiableList(v)));
		return Collections.unmodifiableMap(view);
	}

	public void setActionTimestamps(String signature, List<Long> timestamps) {
		if (timestamps.isEmpty()) {
			actionSignatures.remove(signature);
		} else {
			actionSignatures.put(Objects.requireNonNull(signature), new ArrayList<>(timestamps));
		}
	}

	/** Single-key read for the per-award hot path - avoids the deep view copy. */
	public List<Long> actionTimestamps(String signature) {
		return Collections.unmodifiableList(
				actionSignatures.getOrDefault(signature, List.of()));
	}

	/**
	 * Sweeps every signature list to entries after {@code cutoffEpochMs},
	 * dropping emptied keys - stale signatures otherwise accumulate forever.
	 */
	public void pruneActionSignatures(long cutoffEpochMs) {
		actionSignatures.values().forEach(times -> times.removeIf(t -> t <= cutoffEpochMs));
		actionSignatures.values().removeIf(List::isEmpty);
	}

	/** Clears the ledger - used on sync snapshots; the client never sees it. */
	public void clearActionSignatures() {
		actionSignatures.clear();
	}

	public void clearExpiredCooldowns(long nowEpochMs) {
		cooldowns.values().removeIf(expiry -> expiry <= nowEpochMs);
	}

	public int dataVersion() {
		return dataVersion;
	}

	public void setDataVersion(int dataVersion) {
		this.dataVersion = dataVersion;
	}

	/** The Identifier-list domains of the model, used by {@code ContentIndex} sanitization. */
	public enum ListKind {
		TRAITS, CONDITIONS, ATTUNEMENTS, UNLOCKS;

		private List<ResourceLocation> of(PlayerCharacterData data) {
			// CONDITIONS is unreachable here — addId/removeId/list special-case
			// it (the enum still exists for ContentIndex domain naming).
			return switch (this) {
				case TRAITS -> data.traits;
				case CONDITIONS -> List.of();
				case ATTUNEMENTS -> data.attunements;
				case UNLOCKS -> data.unlocks;
			};
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PlayerCharacterData other)) {
			return false;
		}
		return dataVersion == other.dataVersion
				&& Objects.equals(speciesId, other.speciesId)
				&& Objects.equals(specializationId, other.specializationId)
				&& skills.equals(other.skills)
				&& traits.equals(other.traits)
				&& conditions.equals(other.conditions)
				&& attunements.equals(other.attunements)
				&& unlocks.equals(other.unlocks)
				&& resources.equals(other.resources)
				&& cooldowns.equals(other.cooldowns)
				&& actionSignatures.equals(other.actionSignatures);
	}

	@Override
	public int hashCode() {
		return Objects.hash(speciesId, specializationId, skills, traits, conditions,
				attunements, unlocks, resources, cooldowns, actionSignatures, dataVersion);
	}
}
