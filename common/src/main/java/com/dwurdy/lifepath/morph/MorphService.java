package com.dwurdy.lifepath.morph;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.ability.CooldownService;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.IdentitySummary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.MorphFormDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.feedback.FeedbackService;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * M-3 morph lifecycle (docs/MORPH_FEATURE.md): the {@code anima_morph}
 * ability's {@code morph_toggle} action flips the character's locked form
 * on/off; the {@code Player.actuallyHurt} health-write seam force-demorphs on
 * lethal hits. The player entity never stops being a player — the form's
 * {@code stats} land as transient {@code ADD_VALUE} attribute modifiers keyed
 * {@code lifepath:morph/<attr>}, sized so base + modifier hits the form's
 * absolute value (the {@code modify_attribute} convention, minus the expiry).
 *
 * <p><b>HP carry (locked):</b> proportional both ways — hp% of the old bar
 * becomes the same % of the new bar. A lethal write while morphed is
 * rewritten: the morph bar absorbs what it held, the overflow lands on the
 * human bar scaled by the max ratio ({@link #forcedCarryHealth}); if that
 * still lands &le; 0, vanilla death proceeds.
 *
 * <p><b>Cooldown:</b> the ability's {@code cooldown} field stamps on every
 * successful activation (morph AND demorph — symmetric anti-flap), and a
 * forced demorph stamps it manually so a morph-pop can't be re-morphed
 * instantly. {@code lastMorphMs} records the wall-clock of the last
 * transition for display/audit.
 *
 * <p><b>Entity churn:</b> transient modifiers die with the entity, so the
 * join/respawn hooks re-apply stats while character state says morphed.
 * Unknown forms/attributes warn and skip — never crash, never corrupt.
 */
public final class MorphService {
	private MorphService() {
	}

	/** The vocabulary id the {@code anima_morph} ability JSON invokes. */
	public static final ResourceLocation TOGGLE_ACTION = LifepathMod.id("morph_toggle");

	/** Modifier-id prefix; ids are {@code lifepath:morph/<attrNs>_<attrPath>}. */
	private static final String MODIFIER_PREFIX = "morph/";

	/** What a toggle attempt resolves to — the data-path verdict tests use. */
	public enum ToggleCheck {
		READY, NO_FORM, FORM_MISSING
	}

	/** Registers entity-churn hooks. Called once during common mod init. */
	public static void init() {
		var platform = Platform.get();
		// Transient modifiers die with the entity — a fresh entity while the
		// state says morphed gets the stat profile re-applied. Join ordering:
		// CharacterManager.init's join listener registered first, but
		// getCharacter lazy-loads anyway, so no ordering dependency exists.
		platform.onPlayerJoin(MorphService::applyIfMorphed);
		platform.onPlayerRespawn((oldPlayer, newPlayer, alive) -> applyIfMorphed(newPlayer));
	}

	/**
	 * The {@code morph_toggle} action handler. The engine has already
	 * validated ownership/trigger/cooldown/conditions — this flips the state
	 * (or denies with a reason when the character never picked a form).
	 * {@code changed()}-sync is the caller's contract (engine marks on
	 * EXECUTED).
	 */
	public static void toggle(ServerPlayer player, PlayerCharacterData data,
			@Nullable ResourceLocation abilityId, long now) {
		var morph = data.morph();
		if (morph == null) {
			FeedbackService.send(player, "ability_denied",
					abilityId != null ? IdentitySummary.displayNameComponent(abilityId)
							: Component.literal("Morph"),
					Component.literal("no_form"), Component.literal("0"));
			return;
		}
		// Demorph never gates on the form def — a def deleted mid-morph must
		// still let the player out of the shape (modifier stripping is
		// def-agnostic).
		if (morph.active()) {
			demorph(player, data, morph.formId(), now);
			FeedbackService.send(player, "demorphed");
			return;
		}
		var form = LifepathContent.morphForms().get(morph.formId());
		if (form == null) {
			LifepathMod.LOGGER.warn("morph form {} missing on toggle for {}",
					morph.formId(), player.getUUID());
			FeedbackService.send(player, "ability_denied",
					abilityId != null ? IdentitySummary.displayNameComponent(abilityId)
							: Component.literal("Morph"),
					Component.literal("unavailable"), Component.literal("0"));
			return;
		}
		morph(player, data, form, now);
		// Resolved via the identity chain so RO clients get the translated name.
		FeedbackService.send(player, "morphed",
				IdentitySummary.displayNameComponent(form.id()));
	}

	/**
	 * Data-path toggle verdict — no entity needed. {@link #NO_FORM} when the
	 * character never picked a form (pre-picker anima, admin-cleared);
	 * {@link #FORM_MISSING} when the referenced def is gone.
	 */
	public static ToggleCheck check(PlayerCharacterData data) {
		var morph = data.morph();
		if (morph == null) {
			return ToggleCheck.NO_FORM;
		}
		return LifepathContent.morphForms().get(morph.formId()) != null
				? ToggleCheck.READY : ToggleCheck.FORM_MISSING;
	}

	/**
	 * True when the species def's active abilities carry a
	 * {@code morph_toggle} action — the data-driven answer to "does this
	 * species pick a form", so datapack-added morph species light the
	 * picker's form step without code changes. Resolved against ability
	 * defs at call time — an ability whose ref dangles counts as absent.
	 */
	public static boolean speciesHasMorphToggle(SpeciesDefinition def) {
		for (ResourceLocation ref : def.activeAbilities()) {
			AbilityDefinition ability = LifepathContent.abilities().get(ref);
			if (ability != null && ability.actions().stream()
					.anyMatch(node -> node.type().equals(TOGGLE_ACTION))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Drops an active morph's stat profile — no cooldown, no feedback: the
	 * re-pick/admin path's cleanup, so a form swap can't strand the old
	 * shape's modifiers. The picked {@code formId} is left for the caller
	 * to overwrite; inactive or absent morphs are a no-op.
	 */
	public static void clearActiveMorph(ServerPlayer player,
			PlayerCharacterData data) {
		var morph = data.morph();
		if (morph == null || !morph.active()) {
			return;
		}
		float morphMax = player.getMaxHealth();
		float ratio = morphMax > 0 ? player.getHealth() / morphMax : 1.0f;
		MorphDisguise.clear(player);
		removeMorphModifiers(player);
		player.setHealth(ratio * player.getMaxHealth());
		player.refreshDimensions();
	}

	/** Proportional carry: {@code cur/fromMax} lands as the same ratio of {@code toMax}. */
	static float carriedHealth(float cur, float fromMax, float toMax) {
		return fromMax > 0 ? cur / fromMax * toMax : toMax;
	}

	/**
	 * Forced-demorph carry: the hit's overflow past the morph bar lands on the
	 * human bar scaled by the max ratio. {@code newHealth} is the lethal write
	 * vanilla was about to apply (morphHealth − finalDamage &le; 0), so the
	 * overflow is {@code -newHealth}; the demorphed human starts at the
	 * carried ratio and takes it: {@code (morphHealth + newHealth)/morphMax
	 * × humanMax}. Values &le; 0 mean the carried damage still kills — the
	 * caller writes them through unchanged and vanilla death proceeds.
	 */
	static float forcedCarryHealth(float morphHealth, float newHealth,
			float morphMax, float humanMax) {
		return morphMax > 0 ? (morphHealth + newHealth) / morphMax * humanMax : newHealth;
	}

	/**
	 * Lethal-write seam — the {@code setHealth} call inside
	 * {@code Player.actuallyHurt} (post-armor, post-absorption, so this is the
	 * true lethal quantity). A write that would kill a <b>morphed</b> player
	 * is rewritten: force-demorph, stamp the re-morph cooldown, then land only
	 * the carried remainder — never the full hit a second time. Non-lethal
	 * writes and non-player entities pass through untouched.
	 */
	public static float onLethalHealthWrite(LivingEntity entity, float newHealth) {
		if (newHealth > 0 || !(entity instanceof ServerPlayer player)
				|| entity.level().isClientSide()) {
			return newHealth;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		var morph = data.morph();
		if (morph == null || !morph.active()) {
			return newHealth;
		}
		var form = LifepathContent.morphForms().get(morph.formId());
		float morphMax = player.getMaxHealth();
		if (form == null || morphMax <= 0) {
			// A morph we can't resolve can't shield — die normally.
			return newHealth;
		}
		long now = System.currentTimeMillis();
		float morphHealth = player.getHealth();
		data.setMorph(new PlayerCharacterData.MorphState(morph.formId(), false, now));
		MorphDisguise.clear(player);
		removeMorphModifiers(player);
		float post = forcedCarryHealth(morphHealth, newHealth, morphMax,
				player.getMaxHealth());
		player.refreshDimensions();
		stampRecastCooldown(player, data, now);
		CharacterManager.changed(player);
		FeedbackService.send(player, "demorphed");
		// post <= 0 is written through — setHealth clamps to 0 and hurt()'s
		// isDeadOrDying path runs the real death (totem still applies).
		return post;
	}

	private static void morph(ServerPlayer player, PlayerCharacterData data,
			MorphFormDefinition form, long now) {
		float humanMax = player.getMaxHealth();
		float ratio = humanMax > 0 ? player.getHealth() / humanMax : 1.0f;
		// Synced disguise flag before refreshDimensions so the dims seam
		// already sees the form when the box is recomputed.
		MorphDisguise.stamp(player, form);
		applyStats(player, form);
		data.setMorph(new PlayerCharacterData.MorphState(form.id(), true, now));
		// Now at morph max — land the carried ratio. setHealth clamps high.
		player.setHealth(ratio * player.getMaxHealth());
		player.refreshDimensions();
	}

	private static void demorph(ServerPlayer player, PlayerCharacterData data,
			ResourceLocation formId, long now) {
		float morphMax = player.getMaxHealth();
		float ratio = morphMax > 0 ? player.getHealth() / morphMax : 1.0f;
		data.setMorph(new PlayerCharacterData.MorphState(formId, false, now));
		MorphDisguise.clear(player);
		removeMorphModifiers(player);
		player.setHealth(ratio * player.getMaxHealth());
		player.refreshDimensions();
	}

	/** Re-applies the form profile when a fresh entity joins/respawns morphed. */
	private static void applyIfMorphed(ServerPlayer player) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		var morph = data.morph();
		if (morph == null || !morph.active()) {
			return;
		}
		var form = LifepathContent.morphForms().get(morph.formId());
		if (form == null) {
			LifepathMod.LOGGER.warn("morph form {} missing on entity refresh for {}"
					+ " — leaving stats alone", morph.formId(), player.getUUID());
			return;
		}
		// Fresh entity → fresh synced data and no modifiers — restamp both.
		MorphDisguise.stamp(player, form);
		applyStats(player, form);
		// A fresh entity arrives at full human HP — clamp into the morph bar.
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		player.refreshDimensions();
	}

	/**
	 * Lands each form stat as an {@code ADD_VALUE} modifier sized
	 * {@code form − player base}, so the effective value equals the form's
	 * absolute value while equipment/effect modifiers still compose on top.
	 * Re-application updates in place — never stacks.
	 */
	private static void applyStats(ServerPlayer player, MorphFormDefinition form) {
		for (var entry : form.stats().entrySet()) {
			var holder = BuiltInRegistries.ATTRIBUTE.getHolder(entry.getKey()).orElse(null);
			AttributeInstance instance = holder == null ? null
					: player.getAttribute(holder);
			if (instance == null) {
				LifepathMod.LOGGER.warn("morph form {} stat {} unresolved — skipped",
						form.id(), entry.getKey());
				continue;
			}
			instance.addOrUpdateTransientModifier(new AttributeModifier(
					modifierId(entry.getKey()),
					entry.getValue() - instance.getBaseValue(),
					AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/**
	 * Strips every {@code morph/} modifier regardless of what the current def
	 * contains — a datapack edit mid-morph can't strand a stale stat.
	 */
	private static void removeMorphModifiers(ServerPlayer player) {
		for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
			for (AttributeModifier mod : java.util.List.copyOf(instance.getModifiers())) {
				if (mod.id().getNamespace().equals(LifepathMod.MOD_ID)
						&& mod.id().getPath().startsWith(MODIFIER_PREFIX)) {
					instance.removeModifier(mod.id());
				}
			}
		}
	}

	/** The morph modifier id for one attribute: {@code lifepath:morph/<ns>_<path>}. */
	static ResourceLocation modifierId(ResourceLocation attribute) {
		return LifepathMod.id(MODIFIER_PREFIX
				+ attribute.toString().replace(':', '_'));
	}

	/**
	 * Stamps the morph ability's own cooldown after a forced demorph — the
	 * engine only stamps on activation, and a morph-pop that allowed an
	 * instant re-morph would make the buffer free. Finds the owned ability
	 * carrying a {@code morph_toggle} action; absence (datapack removed it)
	 * just skips the stamp.
	 */
	private static void stampRecastCooldown(ServerPlayer player,
			PlayerCharacterData data, long now) {
		for (ResourceLocation id : AbilityEngine.ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def == null || def.cooldown().isEmpty()) {
				continue;
			}
			boolean carriesToggle = def.actions().stream()
					.anyMatch(node -> node.type().equals(TOGGLE_ACTION));
			if (carriesToggle) {
				CooldownService.trigger(player, id, def.cooldown().get().seconds(), now);
				return;
			}
		}
	}
}
