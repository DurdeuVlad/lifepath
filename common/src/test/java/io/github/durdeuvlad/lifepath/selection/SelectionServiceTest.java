package io.github.durdeuvlad.lifepath.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.unlock.UnlockService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M14 catalog + gate semantics on plain {@link PlayerCharacterData} — the
 * network/screen layers stay thin, so all policy coverage lives here:
 * hidden filtering, unlock-aware availability, and the one-time
 * specialization rule.
 */
class SelectionServiceTest {
	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	@AfterEach
	void tearDown() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	private static SpeciesDefinition species(String path,
			SpeciesDefinition.Visibility visibility, SpeciesDefinition.Selection selection) {
		return new SpeciesDefinition(LifepathMod.id(path), path, Optional.of("desc"),
				visibility, selection, List.of(), List.of(), Map.of(), List.of(),
				Optional.empty(), Optional.empty(), 1.0);
	}

	private static SpecializationDefinition spec(String path) {
		return new SpecializationDefinition(LifepathMod.id(path), path,
				Map.of(LifepathMod.id("smithing"), 20),
				Map.of(LifepathMod.id("smithing"), Aptitude.A),
				Map.of(), Map.of(), Map.of(), List.of(), 1.0);
	}

	@Test
	void availabilityReflectsSelectionPolicyAndUnlocks() {
		SpeciesDefinition open = species("open_sp", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.OPEN);
		SpeciesDefinition locked = species("locked_sp", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.UNLOCKED);
		SpeciesDefinition admin = species("admin_sp", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.ADMIN_ONLY);

		assertEquals(Entry.AVAILABLE, SelectionService.availability(data, open));
		assertEquals(Entry.NEEDS_UNLOCK, SelectionService.availability(data, locked));
		assertEquals(Entry.ADMIN_ONLY, SelectionService.availability(data, admin));

		UnlockService.grant(data, null, LifepathMod.id("locked_sp"));
		assertEquals(Entry.AVAILABLE, SelectionService.availability(data, locked));
		// admin_only never flips — the unlock changes nothing.
		UnlockService.grant(data, null, LifepathMod.id("admin_sp"));
		assertEquals(Entry.ADMIN_ONLY, SelectionService.availability(data, admin));
	}

	@Test
	void catalogOmitsHiddenUnlessUnlockHeld() {
		LifepathContent.species().register(LifepathMod.id("open_sp"),
				species("open_sp", SpeciesDefinition.Visibility.NORMAL,
						SpeciesDefinition.Selection.OPEN));
		LifepathContent.species().register(LifepathMod.id("secret_sp"),
				species("secret_sp", SpeciesDefinition.Visibility.HIDDEN,
						SpeciesDefinition.Selection.UNLOCKED));

		SelectionCatalogPayload catalog = SelectionService.buildCatalog(data);
		assertEquals(1, catalog.species().size());
		assertEquals("lifepath:open_sp", catalog.species().get(0).id());

		// Once unlocked, the hidden species surfaces as a normal card.
		UnlockService.grant(data, null, LifepathMod.id("secret_sp"));
		catalog = SelectionService.buildCatalog(data);
		assertEquals(2, catalog.species().size());
		Entry secret = catalog.species().stream()
				.filter(e -> e.id().equals("lifepath:secret_sp")).findFirst().orElseThrow();
		assertEquals(Entry.AVAILABLE, secret.availability());
	}

	@Test
	void specializationEntriesAreAlreadyChosenOnceSet() {
		LifepathContent.specializations().register(LifepathMod.id("smith"), spec("smith"));

		SelectionCatalogPayload catalog = SelectionService.buildCatalog(data);
		assertEquals(Entry.AVAILABLE, catalog.specializations().get(0).availability());
		// Footer line carries the translation key + resolved args — the
		// client renders it in its own locale.
		var detail = catalog.specializations().get(0).details().get(0);
		var contents = (net.minecraft.network.chat.contents.TranslatableContents)
				detail.getContents();
		assertEquals("text.lifepath.detail.skill_start_apt", contents.getKey());
		assertEquals("smithing", ((net.minecraft.network.chat.Component)
						contents.getArgs()[0]).getString());
		assertEquals(List.of(20, "A"),
				List.of(contents.getArgs()[1], contents.getArgs()[2]));

		data.setSpecializationId(LifepathMod.id("smith"));
		catalog = SelectionService.buildCatalog(data);
		assertEquals(Entry.ALREADY_CHOSEN, catalog.specializations().get(0).availability());
	}

	@Test
	void speciesDetailsLeadWithColoredProsCons() {
		// Strengths ride as green "+ ..." literals, weaknesses as red
		// "- ..." — the color is the signal, so pin both text and style.
		LifepathContent.species().register(LifepathMod.id("s"),
				new SpeciesDefinition(LifepathMod.id("s"), "S", Optional.of("desc"),
						SpeciesDefinition.Visibility.NORMAL,
						SpeciesDefinition.Selection.OPEN,
						List.of(), List.of(), Map.of(), List.of(),
						Optional.empty(), Optional.empty(), 1.0, Optional.empty(),
						List.of("Breathes underwater"), List.of("Dries out")));

		Entry e = SelectionService.buildCatalog(data).species().get(0);
		assertEquals(2, e.details().size());
		assertEquals("+ Breathes underwater", e.details().get(0).getString());
		assertEquals(net.minecraft.network.chat.TextColor
						.fromLegacyFormat(net.minecraft.ChatFormatting.GREEN),
				e.details().get(0).getStyle().getColor());
		assertEquals("- Dries out", e.details().get(1).getString());
		assertEquals(net.minecraft.network.chat.TextColor
						.fromLegacyFormat(net.minecraft.ChatFormatting.RED),
				e.details().get(1).getStyle().getColor());
	}

	@Test
	void canSelectSpecializationRequiresUnsetAndDefined() {
		LifepathContent.specializations().register(LifepathMod.id("smith"), spec("smith"));

		assertTrue(SelectionService.canSelectSpecialization(data, LifepathMod.id("smith")));
		assertFalse(SelectionService.canSelectSpecialization(data, LifepathMod.id("ghost")));
		data.setSpecializationId(LifepathMod.id("smith"));
		assertFalse(SelectionService.canSelectSpecialization(data, LifepathMod.id("smith")));
	}

	@Test
	void chooseAllowedMatchesAvailability() {
		// Hidden stays non-gating: a hidden+unlocked species is choosable
		// once the token is held (visibility filters display, not policy).
		SpeciesDefinition hiddenLocked = species("phantom",
				SpeciesDefinition.Visibility.HIDDEN, SpeciesDefinition.Selection.UNLOCKED);
		assertFalse(SelectionService.chooseAllowed(data, hiddenLocked));
		UnlockService.grant(data, null, LifepathMod.id("phantom"));
		assertTrue(SelectionService.chooseAllowed(data, hiddenLocked));
	}
}
