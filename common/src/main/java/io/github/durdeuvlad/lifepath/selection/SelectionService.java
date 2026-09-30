package io.github.durdeuvlad.lifepath.selection;

import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import io.github.durdeuvlad.lifepath.network.c2s.RequestSelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.c2s.SelectSpecializationPayload;
import io.github.durdeuvlad.lifepath.network.c2s.SelectSpeciesPayload;
import io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import io.github.durdeuvlad.lifepath.platform.Platform;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.specialization.SpecializationService;
import io.github.durdeuvlad.lifepath.unlock.UnlockService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
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
		LifepathNetworking.sendTo(player, buildCatalog(data));
	}

	/**
	 * Resolves the picker's option list for {@code data}. Species carry
	 * per-player availability ({@code unlocked} reflects held unlocks);
	 * {@code hidden} entries are omitted entirely unless the unlock is held —
	 * hidden means "not advertised", not "never pickable". Specializations
	 * have no visibility/selection policy of their own; the one gate is the
	 * first-pick rule (already specialized → {@link Entry#ALREADY_CHOSEN}).
	 */
	public static SelectionCatalogPayload buildCatalog(PlayerCharacterData data) {
		List<Entry> species = new ArrayList<>();
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
					speciesDetails(def), availability(data, def)));
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
		return new SelectionCatalogPayload(List.copyOf(species), List.copyOf(specs));
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
		if (!chooseAllowed(data, def)) {
			deny(player, speciesId, switch (availability(data, def)) {
				case Entry.NEEDS_UNLOCK -> "locked";
				case Entry.ADMIN_ONLY -> "admin_only";
				default -> "unavailable";
			});
			return;
		}
		data.setSpeciesId(speciesId);
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

	private static void deny(ServerPlayer player, ResourceLocation id,
			String reasonKey) {
		// The name rides as a translatable-with-fallback so the denial
		// message renders in the client's locale, not the server's.
		LifepathNetworking.sendTo(player, new FeedbackPayload("selection_denied",
				List.of(IdentitySummary.displayNameComponent(id),
						Component.literal(reasonKey))));
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
