package io.github.durdeuvlad.lifepath.specialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecializationServiceTest {
	private static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");
	private static final ResourceLocation SMITHING = ResourceLocation.fromNamespaceAndPath("lifepath", "smithing");
	private static final ResourceLocation SPEC = ResourceLocation.fromNamespaceAndPath("lifepath", "test_spec");

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		LifepathContent.specializations().clear();
		LifepathContent.levelCurves().clear();
		registerSkill(MINING);
		registerSkill(SMITHING);
	}

	private static void registerSkill(ResourceLocation id) {
		LifepathContent.skills().register(id, new SkillDefinition(id, "S",
				SkillDefinition.Category.CRAFTING, 100, Optional.empty(), List.of(),
				List.of(), Optional.empty()));
	}

	private static void registerSpec(Map<ResourceLocation, Integer> starts,
			Map<ResourceLocation, Aptitude> aptitudes, Map<ResourceLocation, Double> xpMods,
			Map<ResourceLocation, Double> decayMods, Map<ResourceLocation, Integer> floors) {
		LifepathContent.specializations().register(SPEC,
				new SpecializationDefinition(SPEC, "Test Spec", starts, aptitudes,
						xpMods, decayMods, floors, List.of()));
	}

	@Test
	void applySetsIdAndRaisesStartingLevelsOnly() {
		registerSpec(Map.of(MINING, 20, SMITHING, 5), Map.of(), Map.of(), Map.of(), Map.of());
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// Pre-existing higher mining level must NOT be lowered on (re)apply.
		data.setSkillProgress(MINING, new SkillProgress(0, 50, 50, 0, Aptitude.B, 0));

		assertEquals(SpecializationService.ApplyResult.APPLIED,
				SpecializationService.apply(data, SPEC));
		assertEquals(SPEC, data.specializationId());
		assertEquals(50, data.skill(MINING).level(), "higher existing level preserved");
		assertEquals(5, data.skill(SMITHING).level(), "lower-than-start raises to start");
	}

	@Test
	void applySetsAptitudesAndFloors() {
		registerSpec(Map.of(), Map.of(MINING, Aptitude.S), Map.of(),
				Map.of(), Map.of(MINING, 30));
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertEquals(SpecializationService.ApplyResult.APPLIED,
				SpecializationService.apply(data, SPEC));
		assertEquals(Aptitude.S, data.skill(MINING).aptitude());
		assertEquals(30, data.skill(MINING).protectedFloor());
	}

	@Test
	void applyNeverDowngradesAptitudeOrLevel() {
		registerSpec(Map.of(MINING, 10), Map.of(MINING, Aptitude.C), Map.of(),
				Map.of(), Map.of());
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(MINING, new SkillProgress(0, 50, 50, 0, Aptitude.S, 0));

		SpecializationService.apply(data, SPEC);
		assertEquals(50, data.skill(MINING).level());
		assertEquals(Aptitude.S, data.skill(MINING).aptitude(),
				"aptitude overrides are raise-only like levels");
	}

	@Test
	void applyUnknownSpecFailsClean() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertEquals(SpecializationService.ApplyResult.UNKNOWN_SPEC,
				SpecializationService.apply(data, ResourceLocation.fromNamespaceAndPath("lifepath", "nope")));
		assertEquals(null, data.specializationId());
	}

	@Test
	void specModifierLookupsReadTheDefinition() {
		SkillXpService.init();
		registerSpec(Map.of(), Map.of(), Map.of(MINING, 1.5), Map.of(MINING, 0.3), Map.of());
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		SpecializationService.apply(data, SPEC);

		assertEquals(1.5, SpecializationService.xpModifierFor(SPEC, MINING));
		assertEquals(0.3, SpecializationService.decayResistanceFor(SPEC, MINING));
		assertEquals(1.0, SpecializationService.xpModifierFor(SPEC, SMITHING));
		assertEquals(1.0, SpecializationService.xpModifierFor(null, MINING));
		assertEquals(0.0, SpecializationService.decayResistanceFor(null, MINING));
	}

	@Test
	void removedSpecDegradesToIdentity() {
		registerSpec(Map.of(), Map.of(), Map.of(MINING, 9.9), Map.of(), Map.of());
		LifepathContent.specializations().clear(); // spec deleted via reload
		assertEquals(1.0, SpecializationService.xpModifierFor(SPEC, MINING),
				"dangling spec id must degrade to identity, not crash");
	}
}
