package io.github.durdeuvlad.lifepath.specialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped datapack: every {@code data/lifepath/specialization/*.json}
 * must parse through {@code SpecializationDefinitionFile.CODEC}, and
 * blacksmith must carry the GAMEDESIGN §5.1 pinned numbers.
 */
class ShippedSpecializationDefinitionsTest {
	private static final Path SPEC_DIR =
			Path.of("src/main/resources/data/lifepath/specialization");
	private static final List<String> REQUIRED =
			List.of("miner", "farmer", "blacksmith", "fisherman");

	@Test
	void everyShippedSpecializationFileParses() throws Exception {
		try (Stream<Path> files = Files.list(SPEC_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				var parsed = SpecializationDefinition.SpecializationDefinitionFile.CODEC
						.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)));
				assertTrue(parsed.result().isPresent(),
						file.getFileName() + " must parse: " + parsed.error().orElse(null));
			}
		}
	}

	@Test
	void fourRequiredPresetsExist() {
		for (String name : REQUIRED) {
			assertTrue(Files.exists(SPEC_DIR.resolve(name + ".json")),
					"missing shipped specialization " + name);
		}
	}

	@Test
	void blacksmithMatchesThePinnedDesign() throws Exception {
		var file = SpecializationDefinition.SpecializationDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(SPEC_DIR.resolve("blacksmith.json"))))
				.result().orElseThrow(() -> new AssertionError("blacksmith failed to parse"));
		ResourceLocation smithing = ResourceLocation.fromNamespaceAndPath("lifepath", "smithing");
		ResourceLocation engineering = ResourceLocation.fromNamespaceAndPath("lifepath", "engineering");
		ResourceLocation mining = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");

		// §5.1 pinned values.
		assertEquals(Map.of(smithing, 20, engineering, 15, mining, 10),
				file.startingSkills());
		assertEquals(1.35, file.xpModifiers().get(smithing));
		assertEquals(1.25, file.xpModifiers().get(engineering));
		assertEquals(0.3, file.decayModifiers().get(smithing));
		assertEquals(0.2, file.decayModifiers().get(engineering));
		assertEquals(Map.of(smithing, 30), file.protectedFloors());
		assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("lifepath", "forge_mastery_i")), file.signatureRefs());
	}
}
