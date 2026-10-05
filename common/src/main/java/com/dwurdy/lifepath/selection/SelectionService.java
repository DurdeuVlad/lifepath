package com.dwurdy.lifepath.selection;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.IdentitySummary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.MorphFormDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.morph.MorphService;
import com.dwurdy.lifepath.network.LifepathNetworking;
import com.dwurdy.lifepath.network.c2s.RequestSelectionCatalogPayload;
import com.dwurdy.lifepath.network.c2s.SelectMorphFormPayload;
import com.dwurdy.lifepath.network.c2s.SelectSpecializationPayload;
import com.dwurdy.lifepath.network.c2s.SelectSpeciesPayload;
import com.dwurdy.lifepath.network.s2c.FeedbackPayload;
import com.dwurdy.lifepath.network.s2c.SelectionCatalogPayload;
import com.dwurdy.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.skill.Aptitude;
import com.dwurdy.lifepath.specialization.SpecializationService;
import com.dwurdy.lifepath.unlock.UnlockService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * M14 selection service — the server half of GUI-first identity onboarding.
 * Owns the per-player {@link SelectionCatalogPayload} (the picker's option
 * list, resolved server-side) and the two C2S select requests that replace
 * the old {@code /lifepath species choose} command path. Commands are now
 * admin-only; players pick in the screen.
 *
 * <p><b>Server authority:</b> select requests are re-validated end-to-end —
 * def existence, {@code selection} policy, held unlocks, and the one-time
 * specialization rule. A forged or replayed packet is at worst a denial
 * feedback event; it can never grant content.
 *
 * <p><b>Catalog freshness:</b> pushed on join (after {@code CharacterManager}
 * has populated the cache — init order guarantees it), after each accepted
 * selection, and on explicit refresh request (what the screen sends on
 * open). {@code hidden} species stay out of the list unless the player holds
 * their unlock — once unlocked they surface as a normal card, which is what
 * the unlock system exists for.
 */
public final class SelectionService {
	private SelectionService() {
	}

	private static boolean initialized;
	/** "What you get" lines per card — enough for strengths/weaknesses + signature. */
	private static final int DETAIL_CAP = 6;

	/** Wires join-push + C2S receivers. Idempotent; call after {@code CharacterManager.init()}. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		Platform.get().onPlayerJoin(SelectionService::pushCatalog);
		LifepathNetworking.onC2S(RequestSelectionCatalogPayload.ID,
				(payload, player) -> run(player, SelectionService::pushCatalog));
		LifepathNetworking.onC2S(SelectSpeciesPayload.ID,
				(payload, player) -> run(player,
						p -> selectSpecies(p, payload.speciesId())));
		LifepathNetworking.onC2S(SelectSpecializationPayload.ID,
				(payload, player) -> run(player,
						p -> selectSpecialization(p, payload.specializationId())));
		LifepathNetworking.onC2S(SelectMorphFormPayload.ID,
				(payload, player) -> run(player,
						p -> selectMorphForm(p, payload.formId())));
	}

	/**
	 * C2S receivers may fire off the server thread (loader-dependent) — hop
	 * over and drop the task if the player disconnected in between, so a
	 * stale packet can't touch an offline entity's cache entry.
	 */
	private static void run(ServerPlayer player, Consumer<ServerPlayer> task) {
		var server = player.getServer();
		if (server == null) {
			return;
		}
		server.execute(() -> {
			if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
				task.accept(player);
			}
		});
	}

	/** Sends the player's current catalog. No-op before the character cache loads. */
	public static void pushCatalog(ServerPlayer player) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		LifepathNetworking.sendTo(player, buildCatalog(data, freeRespec(player)));
	}

	/**
	 * Who may re-pick a species after choosing one: singleplayer players
	 * (testing convenience) and permission-level-2 operators. On a dedicated
	 * server a first-pick is permanent for normal players — later changes are
	 * admin territory ({@code /lifepath species set}).
	 */
	private static boolean freeRespec(ServerPlayer player) {
		var server = player.getServer();
		return (server != null && server.isSingleplayer())
				|| player.hasPermissions(2);
	}

	/**
	 * Resolves the picker's option list for {@code data}. Species carry
	 * per-player availability ({@code unlocked} reflects held unlocks);
	 * {@code hidden} entries are omitted entirely unless the unlock is held —
	 * hidden means "not advertised", not "never pickable". Specializations
	 * have no visibility/selection policy of their own; the one gate is the
	 * first-pick rule (already specialized → {@link Entry#ALREADY_CHOSEN}).
	 */
	public static SelectionCatalogPayload buildCatalog(PlayerCharacterData data,
			boolean freeRespec) {
		List<Entry> species = new ArrayList<>();
		boolean speciesLocked = !freeRespec && data.speciesId() != null;
		for (SpeciesDefinition def : LifepathContent.species().all().values()) {
			if (def.visibility() == SpeciesDefinition.Visibility.HIDDEN
					&& !UnlockService.isUnlocked(data, def.id())) {
				continue;
			}
			species.add(new Entry(def.id().toString(),
					IdentitySummary.keyedText(def.id(), "species", "name",
							def.displayName()),
					IdentitySummary.keyedText(def.id(), "species", "description",
							def.description().orElse("")),
					def.icon().map(ResourceLocation::toString).orElse(""),
					speciesDetails(def),
					speciesLocked ? Entry.ALREADY_CHOSEN
							: availability(data, def)));
		}
		List<Entry> specs = new ArrayList<>();
		boolean hasSpec = data.specializationId() != null;
		for (SpecializationDefinition def : LifepathContent.specializations().all().values()) {
			specs.add(new Entry(def.id().toString(),
					IdentitySummary.keyedText(def.id(), "specialization", "name",
							def.displayName()),
					Component.empty(),
					def.icon().map(ResourceLocation::toString).orElse(""),
					specDetails(def),
					hasSpec ? Entry.ALREADY_CHOSEN : Entry.AVAILABLE));
		}
		List<Entry> morphs = new ArrayList<>();
		// Locked like species: one pick, permanent on multiplayer; the
		// singleplayer/op re-pick seam mirrors freeRespec.
		boolean morphLocked = !freeRespec && data.morph() != null;
		for (MorphFormDefinition def : LifepathContent.morphForms().all().values()) {
			morphs.add(new Entry(def.id().toString(),
					IdentitySummary.keyedText(def.id(), "morph_form", "name",
							def.displayName()),
					IdentitySummary.keyedText(def.id(), "morph_form",
							"description", def.description().orElse("")),
					def.icon().map(ResourceLocation::toString).orElse(""),
					morphDetails(def),
					morphLocked ? Entry.ALREADY_CHOSEN : Entry.AVAILABLE));
		}
		// Morph-capable species, resolved data-side: any species whose
		// active abilities carry a morph_toggle action. The screen consults
		// this set to gate the form step rather than hardcoding anima.
		Set<String> morphSpecies = new LinkedHashSet<>();
		for (SpeciesDefinition def : LifepathContent.species().all().values()) {
			if (MorphService.speciesHasMorphToggle(def)) {
				morphSpecies.add(def.id().toString());
			}
		}
		return new SelectionCatalogPayload(List.copyOf(species), List.copyOf(specs),
				List.copyOf(morphs), Set.copyOf(morphSpecies));
	}

	/**
	 * The selection policy the player path enforces — moved here from
	 * {@code SpeciesCommands} when {@code choose} became the GUI's request
	 * (M14). {@code open} always selects; {@code unlocked} requires the
	 * species id in {@code unlocks[]}; {@code admin_only} is never
	 * player-choosable (admins use {@code set}). {@code visibility} is a
	 * picker concern, not a gate — a hidden+unlocked species stays choosable
	 * once the unlock is held.
	 */
	public static boolean chooseAllowed(PlayerCharacterData data, SpeciesDefinition def) {
		return availability(data, def) == Entry.AVAILABLE;
	}

	/** Per-player availability of one species def. */
	public static int availability(PlayerCharacterData data, SpeciesDefinition def) {
		return switch (def.selection()) {
			case OPEN -> Entry.AVAILABLE;
			case UNLOCKED -> UnlockService.isUnlocked(data, def.id())
					? Entry.AVAILABLE : Entry.NEEDS_UNLOCK;
			case ADMIN_ONLY -> Entry.ADMIN_ONLY;
		};
	}

	/** The player path's specialization gate: defined + still unspecialized. */
	public static boolean canSelectSpecialization(PlayerCharacterData data,
			ResourceLocation specId) {
		return data.specializationId() == null
				&& LifepathContent.specializations().contains(specId);
	}

	/**
	 * The player path's morph-form gate (M-2): the def must exist, the
	 * character's species must actually carry a {@code morph_toggle}
	 * ability, and the one-time pick must still be open (or the caller is
	 * on the free-respec seam). Matches the catalog's availability math.
	 */
	public static boolean canSelectMorphForm(PlayerCharacterData data,
			ResourceLocation formId, boolean freeRespec) {
		if (!LifepathContent.morphForms().contains(formId)
				|| (data.morph() != null && !freeRespec)) {
			return false;
		}
		SpeciesDefinition species = data.speciesId() == null ? null
				: LifepathContent.species().get(data.speciesId());
		return species != null && MorphService.speciesHasMorphToggle(species);
	}

	private static void selectSpecies(ServerPlayer player, ResourceLocation speciesId) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		SpeciesDefinition def = LifepathContent.species().get(speciesId);
		if (def == null) {
			deny(player, speciesId, "unknown");
			return;
		}
		// Picked once, picked forever — on multiplayer a species change after
		// the fact is admin territory; singleplayer (and ops) may re-pick for
		// testing.
		if (data.speciesId() != null && !freeRespec(player)) {
			deny(player, speciesId, "already_chosen");
			return;
		}
		if (!chooseAllowed(data, def)) {
			deny(player, speciesId, switch (availability(data, def)) {
				case Entry.NEEDS_UNLOCK -> "locked";
				case Entry.ADMIN_ONLY -> "admin_only";
				default -> "unavailable";
			});
			return;
		}
		data.setSpeciesId(speciesId);
		// A species swap retires the morph pick: the new species may not
		// carry the toggle ability, and a stranded active shape could never
		// demorph (the ability that flips it would be unowned).
		MorphService.clearActiveMorph(player, data);
		data.setMorph(null);
		// changed() syncs; the species_assigned feedback + description come
		// from the sync diff — no bespoke chat needed here.
		CharacterManager.changed(player);
		pushCatalog(player);
	}

	private static void selectSpecialization(ServerPlayer player, ResourceLocation specId) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		if (def == null) {
			deny(player, specId, "unknown");
			return;
		}
		if (!canSelectSpecialization(data, specId)) {
			deny(player, specId, "already_chosen");
			return;
		}
		SpecializationService.apply(data, specId);
		CharacterManager.changed(player);
		pushCatalog(player);
	}

	/**
	 * The M-2 form pick: species-select's second step. The form locks like
	 * the species itself — one pick on multiplayer, re-pickable only on the
	 * singleplayer/op seam. Picked INACTIVE: the player still toggles into
	 * the shape via the morph ability. A re-pick while morphed strips the
	 * active profile first so the old form's stats can't linger.
	 */
	private static void selectMorphForm(ServerPlayer player, ResourceLocation formId) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		if (!canSelectMorphForm(data, formId, freeRespec(player))) {
			String reason = denialReason(data, formId);
			// Not-capable maps to the generic "unavailable" — revealing the
			// morph-capable rule to a non-anima picker leaks nothing anyway,
			// but keeps one denial vocabulary for the picker UI.
			deny(player, formId,
					"not_morph_capable".equals(reason) ? "unavailable" : reason);
			return;
		}
		MorphService.clearActiveMorph(player, data);
		data.setMorph(new PlayerCharacterData.MorphState(formId, false,
				System.currentTimeMillis()));
		CharacterManager.changed(player);
		pushCatalog(player);
	}

	/** Why a morph pick would be denied — "unknown" | "not_morph_capable" | "already_chosen". */
	private static String denialReason(PlayerCharacterData data, ResourceLocation formId) {
		if (!LifepathContent.morphForms().contains(formId)) {
			return "unknown";
		}
		SpeciesDefinition species = data.speciesId() == null ? null
				: LifepathContent.species().get(data.speciesId());
		if (species == null || !MorphService.speciesHasMorphToggle(species)) {
			return "not_morph_capable";
		}
		return "already_chosen";
	}

	/**
	 * Form card footer lines: the disguise's entity name (translatable —
	 * "Fox" in the client's locale), then one {@code Attribute: value} line
	 * per declared stat so the trade-off reads before committing. Unknown
	 * stat attributes drop out silently (M-1 already warned at load).
	 */
	private static List<Component> morphDetails(MorphFormDefinition def) {
		List<Component> lines = new ArrayList<>();
		// getHolder, not get: ENTITY_TYPE is a DefaultedRegistry — get()
		// answers AIR for unknown ids rather than null.
		var entityType = BuiltInRegistries.ENTITY_TYPE.getHolder(def.entityType())
				.orElse(null);
		if (entityType != null) {
			lines.add(Component.translatable(entityType.value().getDescriptionId()));
		}
		for (var stat : def.stats().entrySet()) {
			var holder = BuiltInRegistries.ATTRIBUTE.getHolder(stat.getKey())
					.orElse(null);
			if (holder == null) {
				continue;
			}
			double v = stat.getValue();
			String value = v == Math.rint(v) ? Long.toString((long) v)
					: String.format(java.util.Locale.ROOT, "%.2f", v);
			lines.add(Component.translatable(holder.value().getDescriptionId())
					.append(Component.literal(": " + value)));
		}
		return cap(lines);
	}

	private static void deny(ServerPlayer player, ResourceLocation id,
			String reasonKey) {
		// The name rides as a translatable-with-fallback so the denial
		// message renders in the client's locale, not the server's.
		LifepathNetworking.sendTo(player, new FeedbackPayload("selection_denied",
				List.of(IdentitySummary.displayNameComponent(id),
						Component.literal(reasonKey))));
		// Refresh authoritative picker state — a denied card can't linger as
		// selected, and the client's close gate re-arms on the new catalog.
		pushCatalog(player);
	}

	/** Card footer lines: colored "+"/"-" strength/weakness lines first —
	 *  the picker's way of communicating good vs bad without a legend —
	 *  then actives (the headline) and passive names. Sent as translatable
	 *  components — the "Active:" chrome is the client's language, only the
	 *  resolved ability name crosses the wire. */
	private static List<Component> speciesDetails(SpeciesDefinition def) {
		List<Component> lines = new ArrayList<>();
		addProsCons(lines, def.id(), "species", def.strengths(), def.weaknesses());
		for (ResourceLocation id : def.activeAbilities()) {
			lines.add(Component.translatable("text.lifepath.detail.active",
					IdentitySummary.displayNameComponent(id)));
		}
		for (ResourceLocation id : def.passiveAbilities()) {
			lines.add(IdentitySummary.displayNameComponent(id));
		}
		return cap(lines);
	}

	/** Card footer lines: colored "+"/"-" lines, then starting skills with
	 *  aptitude, then signatures. */
	private static List<Component> specDetails(SpecializationDefinition def) {
		List<Component> lines = new ArrayList<>();
		addProsCons(lines, def.id(), "specialization", def.strengths(),
				def.weaknesses());
		def.startingSkills().forEach((skill, level) -> {
			Aptitude aptitude = def.aptitudes().get(skill);
			lines.add(aptitude == null
					? Component.translatable("text.lifepath.detail.skill_start",
							IdentitySummary.displayNameComponent(skill), level)
					: Component.translatable("text.lifepath.detail.skill_start_apt",
							IdentitySummary.displayNameComponent(skill), level,
							aptitude.name()));
		});
		for (ResourceLocation ref : def.signatureRefs()) {
			lines.add(Component.translatable("text.lifepath.detail.signature",
					IdentitySummary.displayNameComponent(ref)));
		}
		return cap(lines);
	}

	/** Datapack-authored "+"/"-" lines — green for strengths, red for
	 *  weaknesses. Each literal rides as the fallback of a
	 *  {@code translatableWithFallback} keyed {@code .strength.N} /
	 *  {@code .weakness.N}, so shipped content translates per-client while
	 *  custom datapack prose still renders as-is. */
	private static void addProsCons(List<Component> lines, ResourceLocation id,
			String domain, List<String> strengths, List<String> weaknesses) {
		for (int i = 0; i < strengths.size(); i++) {
			lines.add(Component.literal("+ ")
					.append(IdentitySummary.keyedText(id, domain,
							"strength." + i, strengths.get(i)))
					.withStyle(ChatFormatting.GREEN));
		}
		for (int i = 0; i < weaknesses.size(); i++) {
			lines.add(Component.literal("- ")
					.append(IdentitySummary.keyedText(id, domain,
							"weakness." + i, weaknesses.get(i)))
					.withStyle(ChatFormatting.RED));
		}
	}

	private static List<Component> cap(List<Component> lines) {
		if (lines.size() <= DETAIL_CAP) {
			return List.copyOf(lines);
		}
		List<Component> out = new ArrayList<>(lines.subList(0, DETAIL_CAP));
		out.add(Component.translatable("text.lifepath.detail.more",
				lines.size() - DETAIL_CAP));
		return List.copyOf(out);
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
