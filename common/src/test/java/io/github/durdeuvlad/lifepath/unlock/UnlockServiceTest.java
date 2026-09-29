package io.github.durdeuvlad.lifepath.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.UnlockDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M9-4 service semantics — grant/revoke/source matching on plain
 * {@link PlayerCharacterData} with {@code player = null} (no dirty-mark
 * needed). The stack/player plumbing in {@code onUseItem} stays thin; the
 * match logic is covered through {@link UnlockService#itemSourceFor}.
 */
class UnlockServiceTest {
	private static final ResourceLocation GATE = LifepathMod.id("gated_thing");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.unlocks().clear();
	}

	@AfterEach
	void tearDown() {
		LifepathContent.unlocks().clear();
	}

	private static UnlockDefinition def(ResourceLocation id, List<ResourceLocation> unlocks,
			UnlockDefinition.SourceRule... sources) {
		return new UnlockDefinition(id, id.toString(), Optional.empty(),
				unlocks, List.of(sources));
	}

	private static UnlockDefinition.SourceRule rule(String type,
			ResourceLocation item, ResourceLocation event, ResourceLocation subject,
			ResourceLocation tag, ResourceLocation advancement, double chance, boolean consume) {
		return new UnlockDefinition.SourceRule(type,
				Optional.ofNullable(item), Optional.ofNullable(event),
				Optional.ofNullable(subject), Optional.ofNullable(tag),
				Optional.ofNullable(advancement), chance, consume);
	}

	private static ActivityEvent combat(ResourceLocation victimType, Set<ResourceLocation> tags) {
		return new ActivityEvent(null, ActivityTypes.COMBAT, victimType,
				tags, ActivityEvent.Cause.PLAYER, 0L, Map.of());
	}

	@Test
	void grantIsIdempotentAndIsUnlockedReadsTheList() {
		assertTrue(UnlockService.grant(data, null, GATE));
		assertTrue(data.unlocks().contains(GATE));
		assertFalse(UnlockService.grant(data, null, GATE));
		assertTrue(UnlockService.isUnlocked(data, GATE));
		assertFalse(UnlockService.isUnlocked(data, LifepathMod.id("other")));
	}

	@Test
	void revokeDropsOnlyHeldIds() {
		assertFalse(UnlockService.revoke(data, null, GATE));
		UnlockService.grant(data, null, GATE);
		assertTrue(UnlockService.revoke(data, null, GATE));
		assertFalse(data.unlocks().contains(GATE));
	}

	@Test
	void eventSourceMatchesTypeSubjectAndTag() {
		ResourceLocation defId = LifepathMod.id("hunt_unlock");
		LifepathContent.unlocks().register(defId, def(defId, List.of(GATE),
				rule("event", null, ActivityTypes.COMBAT, ResourceLocation.fromNamespaceAndPath("minecraft", "phantom"),
						null, null, 1.0, false)));
		// Wrong subject -> no grant.
		UnlockService.onActivity(data, null,
				combat(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), Set.of()), 0L);
		assertFalse(data.unlocks().contains(GATE));
		// Matching subject -> grant.
		UnlockService.onActivity(data, null,
				combat(ResourceLocation.fromNamespaceAndPath("minecraft", "phantom"), Set.of()), 0L);
		assertTrue(data.unlocks().contains(GATE));
	}

	@Test
	void eventSourceTagFilterNarrows() {
		ResourceLocation defId = LifepathMod.id("undead_slayer");
		ResourceLocation undeadTag = ResourceLocation.fromNamespaceAndPath("minecraft", "undead");
		LifepathContent.unlocks().register(defId, def(defId, List.of(GATE),
				rule("event", null, ActivityTypes.COMBAT, null,
						undeadTag, null, 1.0, false)));
		// Event lacks the required tag.
		UnlockService.onActivity(data, null,
				combat(ResourceLocation.fromNamespaceAndPath("minecraft", "cow"), Set.of()), 0L);
		assertFalse(data.unlocks().contains(GATE));
		UnlockService.onActivity(data, null,
				combat(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), Set.of(undeadTag)), 0L);
		assertTrue(data.unlocks().contains(GATE));
	}

	@Test
	void advancementSourceMatchesTheId() {
		ResourceLocation defId = LifepathMod.id("quest_unlock");
		ResourceLocation adv = ResourceLocation.fromNamespaceAndPath("minecraft", "story/enter_the_end");
		LifepathContent.unlocks().register(defId, def(defId, List.of(GATE),
				rule("advancement", null, null, null, null, adv, 1.0, false)));
		// onAdvancement needs a live player for CharacterManager — the pure
		// match the mixin relies on is "source rule advancement == id".
		UnlockDefinition loaded = LifepathContent.unlocks().get(defId);
		boolean matches = loaded.sources().stream().anyMatch(r ->
				"advancement".equals(r.type())
						&& r.advancement().map(adv::equals).orElse(false));
		assertTrue(matches);
		boolean otherMatches = loaded.sources().stream().anyMatch(r ->
				"advancement".equals(r.type())
						&& r.advancement().map(ResourceLocation.fromNamespaceAndPath("minecraft", "end/root")::equals)
								.orElse(false));
		assertFalse(otherMatches);
	}

	@Test
	void itemSourceForMatchesExactItemOnly() {
		ResourceLocation totem = ResourceLocation.fromNamespaceAndPath("minecraft", "totem_of_undying");
		UnlockDefinition d = def(LifepathMod.id("contract"), List.of(GATE),
				rule("item", totem, null, null, null, null, 1.0, true));
		var match = UnlockService.itemSourceFor(d, totem);
		assertTrue(match != null && match.consume());
		assertNull(UnlockService.itemSourceFor(d, ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")));
		// An event-rule def never matches the item seam.
		UnlockDefinition eventOnly = def(LifepathMod.id("e"), List.of(GATE),
				rule("event", null, ActivityTypes.COMBAT, null, null, null, 1.0, false));
		assertNull(UnlockService.itemSourceFor(eventOnly, totem));
	}

	@Test
	void codecParsesEverySourceTypeAndDefaults() throws Exception {
		var parsed = UnlockDefinition.UnlockFile.CODEC.parse(
				com.mojang.serialization.JsonOps.INSTANCE,
				com.google.gson.JsonParser.parseString("""
					{"display_name":"Contract","unlocks":["lifepath:x"],
					 "sources":[{"type":"item","item":"minecraft:totem_of_undying"},
					            {"type":"admin"},
					            {"type":"advancement","advancement":"minecraft:story/root"},
					            {"type":"event","event":"lifepath:combat",
					             "subject":"minecraft:phantom","chance":0.5,"consume":false}]}
					""")).result().orElseThrow();
		assertEquals(4, parsed.sources().size());
		// Defaults: chance 1.0, consume true when omitted.
		assertEquals(1.0, parsed.sources().get(0).chance());
		assertTrue(parsed.sources().get(0).consume());
		assertFalse(parsed.sources().get(3).consume());
		assertEquals("minecraft:phantom",
				parsed.sources().get(3).subject().orElseThrow().toString());
	}

	@Test
	void speciesSelectionPolicyGatesOnUnlocks() {
		SpeciesDefinition locked = new SpeciesDefinition(LifepathMod.id("phantom"),
				"Phantom", Optional.empty(),
				SpeciesDefinition.Visibility.HIDDEN, SpeciesDefinition.Selection.UNLOCKED,
				List.of(), List.of(), Map.of(), List.of(),
				Optional.empty(), Optional.empty(), 1.0);
		SpeciesDefinition open = new SpeciesDefinition(LifepathMod.id("human"),
				"Human", Optional.empty(),
				SpeciesDefinition.Visibility.NORMAL, SpeciesDefinition.Selection.OPEN,
				List.of(), List.of(), Map.of(), List.of(),
				Optional.empty(), Optional.empty(), 1.0);
		SpeciesDefinition adminOnly = new SpeciesDefinition(LifepathMod.id("x"),
				"X", Optional.empty(),
				SpeciesDefinition.Visibility.HIDDEN, SpeciesDefinition.Selection.ADMIN_ONLY,
				List.of(), List.of(), Map.of(), List.of(),
				Optional.empty(), Optional.empty(), 1.0);
		assertTrue(io.github.durdeuvlad.lifepath.selection.SelectionService
				.chooseAllowed(data, open));
		// Locked without the grant -> denied; after grant -> allowed.
		assertFalse(io.github.durdeuvlad.lifepath.selection.SelectionService
				.chooseAllowed(data, locked));
		UnlockService.grant(data, null, LifepathMod.id("phantom"));
		assertTrue(io.github.durdeuvlad.lifepath.selection.SelectionService
				.chooseAllowed(data, locked));
		// admin_only is never player-choosable, unlock held or not.
		UnlockService.grant(data, null, LifepathMod.id("x"));
		assertFalse(io.github.durdeuvlad.lifepath.selection.SelectionService
				.chooseAllowed(data, adminOnly));
	}
}
