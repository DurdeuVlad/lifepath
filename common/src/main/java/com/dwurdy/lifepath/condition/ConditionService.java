package com.dwurdy.lifepath.condition;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.ConditionDefinition;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.resource.ResourceService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime service for acquired conditions (M9-1). Owns all mutation of
 * {@code PlayerCharacterData.conditionStates()} — acquire, cure, and stage
 * advancement — plus the vanilla-side hook entry points that evaluate
 * acquisition/cure rules:
 * <ul>
 *   <li>{@link #onDamagedBy} — {@code type:attack} acquisition rules
 *       (attacker entity id or {@code #tag} + chance roll).
 *   <li>{@link #onItemEaten} — {@code type:item} cures, then
 *       {@code type:item} acquisition rules (eaten food only).
 *   <li>{@link #onActivity} — stage {@code advance_events} counting.
 *   <li>{@link #tick} — stage {@code advance_after_seconds} checks
 *       (called from the ability passive sweep).
 * </ul>
 *
 * <p>Fail-closed throughout: unknown condition ids, missing rules, or absent
 * content are no-ops — never throw for data problems. Callers mark the
 * character dirty; this service only mutates + reports whether it did.
 */
public final class ConditionService {
	private ConditionService() {
	}

	/** Grants the condition at stage 0 + materializes its declared resources. */
	public static boolean acquire(PlayerCharacterData data, @Nullable ServerPlayer player,
			ResourceLocation conditionId, long nowMs) {
		ConditionDefinition def = LifepathContent.conditions().get(conditionId);
		if (def == null || data.conditions().contains(conditionId)) {
			return false;
		}
		data.putCondition(conditionId, ConditionState.fresh(nowMs));
		for (ResourceLocation res : def.resources()) {
			var rdef = LifepathContent.resources().get(res);
			if (rdef != null && data.resources().get(res) == null) {
				data.setResource(res, new PlayerCharacterData.ResourceState(
						rdef.defaultValue(), rdef.min(), rdef.max()));
			}
		}
		mark(player);
		return true;
	}

	/** Removes the condition and drops its declared resource state. */
	public static boolean cure(PlayerCharacterData data, @Nullable ServerPlayer player,
			ResourceLocation conditionId) {
		ConditionDefinition def = LifepathContent.conditions().get(conditionId);
		if (def == null || !data.conditions().contains(conditionId)) {
			return false;
		}
		data.removeCondition(conditionId);
		for (ResourceLocation res : def.resources()) {
			data.removeResource(res);
		}
		mark(player);
		return true;
	}

	/** Every ability id the held conditions graft at their current stages. */
	public static List<ResourceLocation> activeAbilities(PlayerCharacterData data) {
		List<ResourceLocation> out = new ArrayList<>();
		for (ResourceLocation id : data.conditions()) {
			ConditionDefinition def = LifepathContent.conditions().get(id);
			if (def == null) {
				continue; // unloaded content — held id survives a reload
			}
			ConditionState st = data.conditionState(id);
			out.addAll(def.abilitiesAt(st == null ? 0 : clampStage(def, st.stage())));
		}
		return out;
	}

	/** Advances one stage if under the declared count; re-timers the stage. */
	public static boolean advance(PlayerCharacterData data, @Nullable ServerPlayer player,
			ResourceLocation conditionId, long nowMs) {
		ConditionDefinition def = LifepathContent.conditions().get(conditionId);
		ConditionState st = data.conditionState(conditionId);
		if (def == null || st == null || st.stage() + 1 >= def.stageCount()) {
			return false;
		}
		data.putCondition(conditionId, st.advanced(nowMs));
		mark(player);
		return true;
	}

	/** Event-driven advancement: matching {@code advance_events} count up. */
	public static void onActivity(PlayerCharacterData data, @Nullable ServerPlayer player,
			ActivityEvent event, long nowMs) {
		for (ResourceLocation id : data.conditions()) {
			ConditionDefinition def = LifepathContent.conditions().get(id);
			ConditionState st = data.conditionState(id);
			// A stage index at/above the last has no next stage to advance to.
			if (def == null || st == null || st.stage() + 1 >= def.stageCount()) {
				continue;
			}
			ConditionDefinition.Stage stage = def.stages().get(clampStage(def, st.stage()));
			if (!stage.advanceEvents().contains(event.type())) {
				continue;
			}
			ConditionState seen = st.eventSeen();
			data.putCondition(id, seen);
			if (seen.eventProgress() >= stage.advanceCount()) {
				advance(data, player, id, nowMs);
			} else {
				mark(player);
			}
		}
	}

	/** Time-driven advancement — {@code advance_after_seconds} elapsed. */
	public static void tick(PlayerCharacterData data, @Nullable ServerPlayer player,
			long nowMs) {
		for (ResourceLocation id : data.conditions()) {
			ConditionDefinition def = LifepathContent.conditions().get(id);
			ConditionState st = data.conditionState(id);
			if (def == null || st == null || st.stage() + 1 >= def.stageCount()) {
				continue;
			}
			var limit = def.stages().get(clampStage(def, st.stage())).advanceAfterSeconds();
			if (limit.isPresent() && nowMs - st.stageStartedAtMs() >= limit.get() * 1000L) {
				advance(data, player, id, nowMs);
			}
		}
	}

	/**
	 * {@code type:attack} acquisition — each un-held condition whose rule's
	 * {@code entity} matches the attacker rolls its {@code chance}.
	 */
	public static void onDamagedBy(ServerPlayer victim, Entity attacker, RandomSource random,
			long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(victim);
		if (data == null) {
			return;
		}
		for (ConditionDefinition def : LifepathContent.conditions().all().values()) {
			if (data.conditions().contains(def.id())) {
				continue;
			}
			for (ConditionDefinition.AcquisitionRule rule : def.acquisition()) {
				if ("attack".equals(rule.type()) && rule.entity().isPresent()
						&& entityMatches(attacker, rule.entity().get())
						&& random.nextDouble() < rule.chance()) {
					acquire(data, victim, def.id(), nowMs);
					break;
				}
			}
		}
	}

	/**
	 * {@code type:death} acquisition — fired from the {@code ServerPlayer.die}
	 * seam when a player dies. {@code species} gates which species the rule
	 * applies to. Re-death while holding the condition resets it to stage 0
	 * (a phoenix rebirth re-enters the molt rather than being skipped).
	 */
	public static void onDeath(ServerPlayer player, long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data != null) {
			onDeath(data, player, nowMs);
		}
	}

	/** Data-path core of {@link #onDeath(ServerPlayer, long)} — tests + seam. */
	static void onDeath(PlayerCharacterData data, @Nullable ServerPlayer player,
			long nowMs) {
		for (ConditionDefinition def : LifepathContent.conditions().all().values()) {
			boolean matches = def.acquisition().stream().anyMatch(r ->
					"death".equals(r.type())
							&& (r.species().isEmpty()
									|| r.species().get().equals(data.speciesId())));
			if (!matches) {
				continue;
			}
			if (data.conditions().contains(def.id())) {
				data.putCondition(def.id(), ConditionState.fresh(nowMs));
				mark(player);
			} else {
				acquire(data, player, def.id(), nowMs);
			}
		}
	}

	/**
	 * {@code type:item} rules on an eaten stack: cures first (held
	 * conditions), then item-based acquisition (un-held conditions).
	 */
	public static void onItemEaten(ServerPlayer player, ItemStack stack, long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null || stack.isEmpty()) {
			return;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (ResourceLocation id : List.copyOf(data.conditions())) {
			ConditionDefinition def = LifepathContent.conditions().get(id);
			if (def != null && def.cures().stream().anyMatch(c ->
					"item".equals(c.type()) && c.item().map(itemId::equals).orElse(false))) {
				cure(data, player, id);
			}
		}
		for (ConditionDefinition def : LifepathContent.conditions().all().values()) {
			if (data.conditions().contains(def.id())) {
				continue;
			}
			boolean take = def.acquisition().stream().anyMatch(r ->
					"item".equals(r.type()) && r.item().map(itemId::equals).orElse(false));
			if (take) {
				acquire(data, player, def.id(), nowMs);
			}
		}
	}

	private static boolean entityMatches(Entity entity, String idOrTag) {
		if (idOrTag.startsWith("#")) {
			ResourceLocation tagId = ResourceLocation.tryParse(idOrTag.substring(1));
			return tagId != null && entity.getType().builtInRegistryHolder()
					.is(TagKey.create(BuiltInRegistries.ENTITY_TYPE.key(), tagId));
		}
		ResourceLocation id = ResourceLocation.tryParse(idOrTag);
		return id != null && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id);
	}

	private static int clampStage(ConditionDefinition def, int stage) {
		return Math.max(0, Math.min(stage, def.stageCount() - 1));
	}

	private static void mark(@Nullable ServerPlayer player) {
		if (player != null) {
			CharacterManager.changed(player);
		}
	}
}
