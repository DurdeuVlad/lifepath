package io.github.durdeuvlad.lifepath.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.LevelCurveDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.content.XpSourceDefinition;
import io.github.durdeuvlad.lifepath.reload.ReloadManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The content registries (M1-3): species, specialization, skill — plus the
 * datapack loaders that fill them.
 *
 * <p><b>Registry choice:</b> plain {@link ContentRegistry} maps instead of
 * Fabric dynamic registries. These are server-side content metadata with no
 * registry-sync requirement yet (character sync carries the character's own
 * state, not definitions); dynamic registries would add entry-codec and
 * client-sync machinery with no consumer until M4/M6. Revisit if definitions
 * must reach the client.
 *
 * <p><b>Loading:</b> each domain is a {@link ReloadManager#registerData}
 * reloader reading {@code data/<any-ns>/<domain>/*.json} — vanilla datapack
 * override semantics apply, and both {@code /reload} and {@code /lifepath
 * reload} rebuild registries through the same M0-2 lifecycle. Registries are
 * cleared then repopulated; a bad file is ERROR+skipped, never fatal, and
 * cross-references the file makes to content in other domains are recorded
 * in {@link #unresolvedReferences()} for full validation (M7-5).
 *
 * <p>Also installs the real {@link ContentIndex}: species/specialization/skill
 * existence is answered from these registries; domains with no registry yet
 * (traits, conditions, attunements, …) answer permissively until their milestone lands.
 */
public final class LifepathContent {
	private static final ContentRegistry<SpeciesDefinition> SPECIES =
			new ContentRegistry<>(LifepathMod.id("species"));
	private static final ContentRegistry<SpecializationDefinition> SPECIALIZATIONS =
			new ContentRegistry<>(LifepathMod.id("specialization"));
	private static final ContentRegistry<SkillDefinition> SKILLS =
			new ContentRegistry<>(LifepathMod.id("skill"));
	private static final ContentRegistry<LevelCurveDefinition> LEVEL_CURVES =
			new ContentRegistry<>(LifepathMod.id("level_curve"));
	private static final ContentRegistry<XpSourceDefinition> XP_SOURCES =
			new ContentRegistry<>(LifepathMod.id("xp_source"));
	private static final ContentRegistry<AbilityDefinition> ABILITIES =
			new ContentRegistry<>(LifepathMod.id("ability"));

	/** A cross-reference a loaded file made to content no registry resolved (recorded for M7-5 validation). */
	public record UnresolvedReference(String domain, Identifier source, Identifier ref, String targetDomain) {
	}

	private static final List<UnresolvedReference> UNRESOLVED = new ArrayList<>();
	private static boolean initialized;

	private LifepathContent() {
	}

	/** Registers the content loaders (datapack + {@code /lifepath reload} share this path). Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ReloadManager.registerData(LifepathMod.id("species"),
				manager -> loadDomain(manager, "species", SpeciesDefinition.SpeciesDefinitionFile.CODEC,
						SpeciesDefinition::fromFile, SPECIES));
		ReloadManager.registerData(LifepathMod.id("specialization"),
				manager -> loadDomain(manager, "specialization", SpecializationDefinition.SpecializationDefinitionFile.CODEC,
						SpecializationDefinition::fromFile, SPECIALIZATIONS));
		ReloadManager.registerData(LifepathMod.id("skill"),
				manager -> loadDomain(manager, "skill", SkillDefinition.SkillDefinitionFile.CODEC,
						SkillDefinition::fromFile, SKILLS));
		// Nested domain: curves live under data/<ns>/skill/curve/ — the skill
		// reloader only scans the domain dir's direct children, so no collision.
		ReloadManager.registerData(LifepathMod.id("level_curve"),
				manager -> loadDomain(manager, "skill/curve", LevelCurveDefinition.LevelCurveFile.CODEC,
						LevelCurveDefinition::fromFile, LEVEL_CURVES));
		ReloadManager.registerData(LifepathMod.id("xp_source"),
				manager -> loadDomain(manager, "xp_source", XpSourceDefinition.XpSourceFile.CODEC,
						XpSourceDefinition::fromFile, XP_SOURCES));
		// Ability files must only name registered vocabulary types (M4-2
		// contract) — an unknown condition/action/target fails the file with
		// an error naming the ability instead of silently no-op'ing at eval.
		ReloadManager.registerData(LifepathMod.id("ability"),
				manager -> loadDomain(manager, "ability", AbilityDefinition.AbilityFile.CODEC,
						(id, file) -> {
							AbilityDefinition def = AbilityDefinition.fromFile(id, file);
							var unknown = io.github.durdeuvlad.lifepath.ability
									.AbilityVocabulary.unknownNodeTypes(def);
							if (!unknown.isEmpty()) {
								throw new IllegalArgumentException(
										"ability " + id + " uses unknown spec node types " + unknown);
							}
							return def;
						}, ABILITIES));
		ReloadManager.registerData(LifepathMod.id("content_validation"),
				manager -> validateReferences());
		CharacterPersistence.setContentIndex(LifepathContent::exists);
	}

	public static ContentRegistry<SpeciesDefinition> species() {
		return SPECIES;
	}

	public static ContentRegistry<SpecializationDefinition> specializations() {
		return SPECIALIZATIONS;
	}

	public static ContentRegistry<SkillDefinition> skills() {
		return SKILLS;
	}

	public static ContentRegistry<LevelCurveDefinition> levelCurves() {
		return LEVEL_CURVES;
	}

	public static ContentRegistry<XpSourceDefinition> xpSources() {
		return XP_SOURCES;
	}

	public static ContentRegistry<AbilityDefinition> abilities() {
		return ABILITIES;
	}

	/**
	 * {@link ContentIndex} implementation. Domains backed by a real registry
	 * answer definitively; domains without a registry yet (traits, conditions,
	 * attunements, resources, …) answer permissively so nothing gets dropped
	 * before its own milestone lands.
	 */
	public static boolean exists(String domain, Identifier id) {
		return switch (domain) {
			case "species" -> SPECIES.contains(id);
			case "specialization" -> SPECIALIZATIONS.contains(id);
			case "skill" -> SKILLS.contains(id);
			case "level_curve" -> LEVEL_CURVES.contains(id);
			case "xp_source" -> XP_SOURCES.contains(id);
			case "ability" -> ABILITIES.contains(id);
			default -> true;
		};
	}

	/** Cross-domain references recorded during the last load; replaced each reload. For M7-5 full validation. */
	public static List<UnresolvedReference> unresolvedReferences() {
		return List.copyOf(UNRESOLVED);
	}

	@FunctionalInterface
	public interface FileDecoder<F, T> {
		T decode(Identifier id, F file);
	}

	/**
	 * Rebuilds {@code registry} from {@code data/<ns>/<directory>/&#42;.json}.
	 * Per-file failures are logged ERROR and skipped; a duplicate id across
	 * namespaces resolves by datapack precedence via {@code findResources}
	 * (the ResourceManager already returns only the winning resource per id).
	 */
	public static <F, T> void loadDomain(ResourceManager manager, String directory,
			Codec<F> fileCodec, FileDecoder<F, T> decoder, ContentRegistry<T> registry) {
		// Read stage first: the registry keeps its previous contents if the
		// scan itself fails — only a completed read replaces the domain.
		Map<Identifier, JsonElement> parsed = new LinkedHashMap<>();
		// findResources recurses — only DIRECT children of the domain dir are
		// entries; nested paths belong to their own domain (e.g. skill/curve).
		// The startsWith guard is required too: non-vanilla PackResources may
		// prefix-match loosely and feed sibling dirs back through.
		Map<Identifier, Resource> files =
				manager.findResources(directory, id -> {
					String path = id.getPath();
					if (!path.startsWith(directory + "/") || !path.endsWith(".json")) {
						return false;
					}
					return !path.substring(directory.length() + 1).contains("/");
				});
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			try (var reader = new InputStreamReader(file.getValue().getInputStream(), StandardCharsets.UTF_8)) {
				parsed.put(entryId(file.getKey(), directory), JsonParser.parseReader(reader));
			} catch (Exception e) {
				LifepathMod.LOGGER.error("skipping {} file {}: {}", directory, file.getKey(), e.getMessage());
			}
		}
		registry.clear();
		registerAll(directory, parsed, fileCodec, decoder, registry);
	}

	/**
	 * Decodes and registers every parsed file; a malformed entry is ERROR +
	 * skipped without affecting the rest. Returns the number of entries
	 * registered. Separated from {@link #loadDomain} so it can be unit-tested
	 * without a {@link ResourceManager}.
	 */
	public static <F, T> int registerAll(String directory, Map<Identifier, JsonElement> files,
			Codec<F> fileCodec, FileDecoder<F, T> decoder, ContentRegistry<T> registry) {
		int loaded = 0;
		for (Map.Entry<Identifier, JsonElement> file : files.entrySet()) {
			try {
				F parsed = fileCodec.parse(JsonOps.INSTANCE, file.getValue())
						.getOrThrow(err -> new IllegalArgumentException(
								"invalid " + directory + " file " + file.getKey() + ": " + err));
				registry.register(file.getKey(), decoder.decode(file.getKey(), parsed));
				loaded++;
			} catch (Exception e) {
				LifepathMod.LOGGER.error("skipping {} file {}: {}", directory, file.getKey(), e.getMessage());
			}
		}
		LifepathMod.LOGGER.info("loaded {} {} definition(s)", loaded, directory);
		return loaded;
	}

	/** {@code ns:species/foo.json} under directory {@code species} → {@code ns:foo}. */
	static Identifier entryId(Identifier fileId, String directory) {
		String path = fileId.getPath();
		String stripped = path.substring(directory.length() + 1, path.length() - ".json".length());
		return Identifier.of(fileId.getNamespace(), stripped);
	}

	/**
	 * Runs AFTER the domain loaders (registration order): records every
	 * cross-domain reference the freshly loaded definitions make. A ref whose
	 * target domain has a registry is recorded only when missing there; a ref
	 * into a domain with no registry yet (traits, attunements, …) is recorded as
	 * pending — {@link #unresolvedReferences()} is the M7-5 validation input.
	 */
	static void validateReferences() {
		UNRESOLVED.clear();
		for (SpeciesDefinition def : SPECIES.all().values()) {
			recordRefs("species", def.id(), def.passiveAbilities(), "ability");
			recordRefs("species", def.id(), def.activeAbilities(), "ability");
			recordRefs("species", def.id(), def.minAptitudes().keySet(), "skill");
			recordRefs("species", def.id(), def.resources(), "resource");
			recordRef("species", def.id(), def.dietRules(), "diet_rules");
			recordRef("species", def.id(), def.mobDispositions(), "mob_disposition");
		}
		for (SpecializationDefinition def : SPECIALIZATIONS.all().values()) {
			recordRefs("specialization", def.id(), def.startingSkills().keySet(), "skill");
			recordRefs("specialization", def.id(), def.aptitudes().keySet(), "skill");
			recordRefs("specialization", def.id(), def.xpModifiers().keySet(), "skill");
			recordRefs("specialization", def.id(), def.decayModifiers().keySet(), "skill");
			recordRefs("specialization", def.id(), def.protectedFloors().keySet(), "skill");
			recordRefs("specialization", def.id(), def.signatureRefs(), "ability");
		}
		for (SkillDefinition def : SKILLS.all().values()) {
			recordRef("skill", def.id(), def.levelCurve(), "level_curve");
			recordRefs("skill", def.id(), def.xpSources(), "xp_source");
			recordRef("skill", def.id(), def.passiveScaling(), "scaling");
			for (SkillDefinition.Milestone milestone : def.milestones()) {
				recordRefs("skill", def.id(), milestone.effectRefs(), "ability");
			}
		}
		for (XpSourceDefinition def : XP_SOURCES.all().values()) {
			recordRef("xp_source", def.id(), Optional.of(def.skill()), "skill");
		}
	}

	private static void recordRefs(String domain, Identifier source,
			Iterable<Identifier> refs, String targetDomain) {
		for (Identifier ref : refs) {
			recordRef(domain, source, Optional.of(ref), targetDomain);
		}
	}

	private static void recordRef(String domain, Identifier source,
			Optional<Identifier> ref, String targetDomain) {
		ref.ifPresent(id -> {
			ContentRegistry<?> registry = registryFor(targetDomain);
			if (registry == null || !registry.contains(id)) {
				UNRESOLVED.add(new UnresolvedReference(domain, source, id, targetDomain));
				LifepathMod.LOGGER.warn(
						"{}:{} references unknown {} '{}' (typo? missing file? pending domain?)",
						domain, source, targetDomain, id);
			}
		});
	}

	@Nullable
	private static ContentRegistry<?> registryFor(String domain) {
		return switch (domain) {
			case "species" -> SPECIES;
			case "specialization" -> SPECIALIZATIONS;
			case "skill" -> SKILLS;
			case "level_curve" -> LEVEL_CURVES;
			case "xp_source" -> XP_SOURCES;
			case "ability" -> ABILITIES;
			default -> null;
		};
	}
}
