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
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Versioned, server-authoritative model of everything a Lifepath character HAS.
 * This is data, not logic â€” progression rules live in later milestones.
 *
 * <p>Field shape follows {@code docs/GAMEDESIGN.md} Â§18. Content references are
 * stored as bare {@link Identifier}s â€” this class never validates whether a
 * referenced definition exists (that is {@code ContentIndex}'s job, applied by
 * the persistence layer on load).
 *
 * <p><b>Death rule:</b> nothing resets on death. The data is stored in a
 * {@code copyOnDeath} entity attachment (see {@code CharacterAttachments}), so
 * death/respawn, logout/login, restarts and dimension changes all preserve it.
 * A future "reset on death" option would be implemented as an explicit reset
 * invoked by a respawn hook â€” deliberately not added as dead config.
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
			Identifier.CODEC.optionalFieldOf("species_id").forGetter(d -> Optional.ofNullable(d.speciesId)),
			Identifier.CODEC.optionalFieldOf("specialization_id").forGetter(d -> Optional.ofNullable(d.specializationId)),
			Codec.unboundedMap(Identifier.CODEC, SkillProgress.CODEC)
					.optionalFieldOf("skills", Map.of()).forGetter(d -> d.skills),
			Identifier.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(d -> d.traits),
			Identifier.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(d -> d.conditions),
			Identifier.CODEC.listOf().optionalFieldOf("attunements", List.of()).forGetter(d -> d.attunements),
			Identifier.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(d -> d.unlocks),
			Codec.unboundedMap(Identifier.CODEC, ResourceState.CODEC)
					.optionalFieldOf("resources", Map.of()).forGetter(d -> d.resources),
			Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
					.optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
					.optionalFieldOf("action_signatures", Map.of()).forGetter(d -> d.actionSignatures),
			Codec.INT.optionalFieldOf("data_version", 0).forGetter(d -> d.dataVersion)
	).apply(instance, PlayerCharacterData::fromCodec));

	@Nullable
	private Identifier speciesId;
	@Nullable
	private Identifier specializationId;
	private final Map<Identifier, SkillProgress> skills = new LinkedHashMap<>();
	private final List<Identifier> traits = new ArrayList<>();
	private final List<Identifier> conditions = new ArrayList<>();
	private final List<Identifier> attunements = new ArrayList<>();
	private final List<Identifier> unlocks = new ArrayList<>();
	private final Map<Identifier, ResourceState> resources = new LinkedHashMap<>();
	private final Map<Identifier, Long> cooldowns = new LinkedHashMap<>();
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
			Optional<Identifier> speciesId,
			Optional<Identifier> specializationId,
			Map<Identifier, SkillProgress> skills,
			List<Identifier> traits,
			List<Identifier> conditions,
			List<Identifier> attunements,
			List<Identifier> unlocks,
			Map<Identifier, ResourceState> resources,
			Map<Identifier, Long> cooldowns,
			Map<String, List<Long>> actionSignatures,
			int dataVersion) {
		PlayerCharacterData data = new PlayerCharacterData();
		data.speciesId = speciesId.orElse(null);
		data.specializationId = specializationId.orElse(null);
		data.skills.putAll(skills);
		data.traits.addAll(traits);
		data.conditions.addAll(conditions);
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
	public Identifier speciesId() {
		return speciesId;
	}

	public void setSpeciesId(@Nullable Identifier speciesId) {
		this.speciesId = speciesId;
	}

	@Nullable
	public Identifier specializationId() {
		return specializationId;
	}

	public void setSpecializationId(@Nullable Identifier specializationId) {
		this.specializationId = specializationId;
	}

	public Map<Identifier, SkillProgress> skills() {
		return Collections.unmodifiableMap(skills);
	}

	@Nullable
	public SkillProgress skill(Identifier skillId) {
		return skills.get(skillId);
	}

	public void setSkillProgress(Identifier skillId, SkillProgress progress) {
		skills.put(Objects.requireNonNull(skillId), Objects.requireNonNull(progress));
	}

	public void removeSkill(Identifier skillId) {
		skills.remove(skillId);
	}

	public List<Identifier> traits() {
		return Collections.unmodifiableList(traits);
	}

	public List<Identifier> conditions() {
		return Collections.unmodifiableList(conditions);
	}

	public List<Identifier> attunements() {
		return Collections.unmodifiableList(attunements);
	}

	public List<Identifier> unlocks() {
		return Collections.unmodifiableList(unlocks);
	}

	public boolean addId(ListKind list, Identifier id) {
		List<Identifier> target = list.of(this);
		if (target.contains(id)) {
			return false;
		}
		target.add(id);
		return true;
	}

	public boolean removeId(ListKind list, Identifier id) {
		return list.of(this).remove(id);
	}

	/** Read-only view of one Identifier-list domain. */
	public List<Identifier> list(ListKind list) {
		return Collections.unmodifiableList(list.of(this));
	}

	public Map<Identifier, ResourceState> resources() {
		return Collections.unmodifiableMap(resources);
	}

	public void setResource(Identifier resourceId, ResourceState state) {
		resources.put(Objects.requireNonNull(resourceId), Objects.requireNonNull(state));
	}

	public void removeResource(Identifier resourceId) {
		resources.remove(resourceId);
	}

	public Map<Identifier, Long> cooldowns() {
		return Collections.unmodifiableMap(cooldowns);
	}

	public void setCooldown(Identifier abilityId, long expiryEpochMs) {
		cooldowns.put(Objects.requireNonNull(abilityId), expiryEpochMs);
	}

	public void removeCooldown(Identifier abilityId) {
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

	/** Single-key read for the per-award hot path — avoids the deep view copy. */
	public List<Long> actionTimestamps(String signature) {
		return Collections.unmodifiableList(
				actionSignatures.getOrDefault(signature, List.of()));
	}

	/**
	 * Sweeps every signature list to entries after {@code cutoffEpochMs},
	 * dropping emptied keys — stale signatures otherwise accumulate forever.
	 */
	public void pruneActionSignatures(long cutoffEpochMs) {
		actionSignatures.values().forEach(times -> times.removeIf(t -> t <= cutoffEpochMs));
		actionSignatures.values().removeIf(List::isEmpty);
	}

	/** Clears the ledger — used on sync snapshots; the client never sees it. */
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

		private List<Identifier> of(PlayerCharacterData data) {
			return switch (this) {
				case TRAITS -> data.traits;
				case CONDITIONS -> data.conditions;
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
