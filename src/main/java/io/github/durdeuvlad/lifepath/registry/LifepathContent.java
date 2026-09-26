package io.github.durdeuvlad.lifepath.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
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
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.ResourceDefinition>
			RESOURCES = new ContentRegistry<>(LifepathMod.id("resource"));
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.DietDefinition>
			DIETS = new ContentRegistry<>(LifepathMod.id("diet"));
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.RelationDefinition>
			RELATIONS = new ContentRegistry<>(LifepathMod.id("relation"));

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
		// Resource defs load BEFORE abilities — ability decode validates
		// `resource` refs against this registry (M4-6). Band actions validate
		// against the vocabulary, not the ability registry, so the order is
		// safe one way only.
		ReloadManager.registerData(LifepathMod.id("resource"),
				manager -> loadDomain(manager, "resource",
						io.github.durdeuvlad.lifepath.content.ResourceDefinition.ResourceFile.CODEC,
						LifepathContent::decodeResource, RESOURCES));
		// Ability files must only name registered vocabulary types (M4-2
		// contract) and existing content refs (M4-6) — failures name the file.
		ReloadManager.registerData(LifepathMod.id("ability"),
				manager -> loadDomain(manager, "ability", AbilityDefinition.AbilityFile.CODEC,
						LifepathContent::decodeAbility, ABILITIES));
		// Species-adjacent rule domains (M5-4): species refs are only recorded
		// post-load, so registration order relative to species does not matter.
		ReloadManager.registerData(LifepathMod.id("diet"),
				manager -> loadDomain(manager, "diet",
						io.github.durdeuvlad.lifepath.content.DietDefinition.DietFile.CODEC,
						io.github.durdeuvlad.lifepath.content.DietDefinition::fromFile, DIETS));
		ReloadManager.registerData(LifepathMod.id("relation"),
				manager -> loadDomain(manager, "relation",
						io.github.durdeuvlad.lifepath.content.RelationDefinition.RelationFile.CODEC,
						io.github.durdeuvlad.lifepath.content.RelationDefinition::fromFile, RELATIONS));
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

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.ResourceDefinition>
			resources() {
		return RESOURCES;
	}

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.DietDefinition> diets() {
		return DIETS;
	}

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.RelationDefinition> relations() {
		return RELATIONS;
	}

	/**
	 * {@link ContentIndex} implementation. Domains backed by a real registry
	 * answer definitively; domains without a registry yet (traits, conditions,
	 * attunements, …) answer permissively so nothing gets dropped before its
	 * own milestone lands.
	 */
	public static boolean exists(String domain, Identifier id) {
		return switch (domain) {
			case "species" -> SPECIES.contains(id);
			case "specialization" -> SPECIALIZATIONS.contains(id);
			case "skill" -> SKILLS.contains(id);
			case "level_curve" -> LEVEL_CURVES.contains(id);
			case "xp_source" -> XP_SOURCES.contains(id);
			case "ability" -> ABILITIES.contains(id);
			case "resource" -> RESOURCES.contains(id);
			case "diet" -> DIETS.contains(id);
			case "relation" -> RELATIONS.contains(id);
			default -> true;
		};
	}

	/**
	 * Ability decode + validation (M4-2/M4-6 contract): collects EVERY problem
	 * in the file and throws once, joined — the loader logs the full list so a
	 * broken file is diagnosed in one pass, then skipped. Checks: unknown
	 * condition/target/action types, dangling content refs ({@code resource},
	 * {@code skill} params + cost/interaction resources), and dead triggers
	 * (EVENT with no {@code events[]}). Registry-backed refs (item/effect/
	 * tag/sound ids) are validated lazily by the evaluators themselves —
	 * vanilla registries are not available at datapack-decode time on every
	 * code path — see docs/ABILITIES.md.
	 */
	public static AbilityDefinition decodeAbility(Identifier id, AbilityDefinition.AbilityFile file) {
		AbilityDefinition def = AbilityDefinition.fromFile(id, file);
		java.util.Set<String> errors = new java.util.LinkedHashSet<>();
		for (Identifier t : io.github.durdeuvlad.lifepath.ability
				.AbilityVocabulary.unknownNodeTypes(def)) {
			errors.add("unknown spec node type " + t);
		}
		if (def.trigger().kind() == AbilityDefinition.Kind.EVENT
				&& def.trigger().events().isEmpty()) {
			errors.add("trigger type \"event\" declares no events[] — it can never fire");
		}
		if (!def.trigger().events().isEmpty()
				&& def.trigger().kind() != AbilityDefinition.Kind.EVENT) {
			LifepathMod.LOGGER.warn("ability {} declares events[] on a {} "
					+ "trigger — ignored", id, def.trigger().kind());
		}
		if (def.trigger().kind() == AbilityDefinition.Kind.DAMAGE_TAKEN) {
			// Incoming-damage path consults only conditions + multiplier —
			// running actions mid-damage() would recurse.
			if (!def.actions().isEmpty() || def.cost().isPresent()
					|| def.cooldown().isPresent()
					|| !def.resourceInteractions().isEmpty()) {
				LifepathMod.LOGGER.warn("ability {} damage_taken trigger ignores "
						+ "actions/cost/cooldown/resource_interactions", id);
			}
		}
		if (def.actions().isEmpty()
				&& def.trigger().kind() != AbilityDefinition.Kind.DAMAGE_TAKEN) {
			LifepathMod.LOGGER.warn(
					"ability {} has no actions — it can only mark cooldowns", id);
		}
		// Content-ref scan: the param-name conventions `resource` / `skill`
		// are the contract for every spec node (documented in ABILITIES.md).
		// `#`-prefixed values are tag references — skipped by design.
		for (var node : specNodes(def)) {
			checkRef(errors, id, node, "resource", RESOURCES);
			checkRef(errors, id, node, "skill", SKILLS);
		}
		for (Identifier event : def.trigger().events()) {
			if (!KNOWN_EVENT_TYPES.contains(event)) {
				LifepathMod.LOGGER.warn("ability {} subscribes to unknown event "
						+ "type {} — typo? it may never fire", id, event);
			}
		}
		def.cost().ifPresent(c -> {
			if (!RESOURCES.contains(c.resource())) {
				errors.add("cost.resource " + c.resource() + " has no resource definition");
			}
		});
		for (var ri : def.resourceInteractions()) {
			if (!RESOURCES.contains(ri.resource())) {
				errors.add("resource_interactions[] resource " + ri.resource()
						+ " has no resource definition");
			}
		}
		if (!def.resourceInteractions().isEmpty()
				&& def.trigger().kind() != AbilityDefinition.Kind.PASSIVE) {
			LifepathMod.LOGGER.warn("ability {} declares resource_interactions "
					+ "on a {} trigger — they only apply under passive", id,
					def.trigger().kind());
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(
					"ability " + id + " invalid — " + String.join("; ", errors));
		}
		return def;
	}

	/** Activity ids the bus can actually produce — unknown subscribers warn. */
	private static final java.util.Set<Identifier> KNOWN_EVENT_TYPES = java.util.Set.of(
			io.github.durdeuvlad.lifepath.event.ActivityTypes.MINING,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.FARMING,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.SMITHING,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.FISHING,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.CRAFTING,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.COMBAT,
			io.github.durdeuvlad.lifepath.resource.ResourceService.BAND_ENTER,
			io.github.durdeuvlad.lifepath.resource.ResourceService.BAND_EXIT);

	private static java.util.List<AbilityDefinition.SpecNode> specNodes(AbilityDefinition def) {
		java.util.List<AbilityDefinition.SpecNode> all = new ArrayList<>();
		all.addAll(def.conditions().all());
		all.addAll(def.conditions().any());
		all.add(def.target());
		all.addAll(def.actions());
		return all;
	}

	private static void checkRef(java.util.Set<String> errors, Identifier file,
			AbilityDefinition.SpecNode node, String key, ContentRegistry<?> registry) {
		var el = node.raw().get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
			return;
		}
		String raw = el.getAsString();
		if (raw.startsWith("#")) {
			return; // tag reference — validated fail-closed at use time
		}
		Identifier ref = Identifier.tryParse(raw);
		if (ref == null) {
			errors.add("node " + node.type() + " " + key + "=\"" + raw
					+ "\" is not a valid identifier");
		} else if (!registry.contains(ref)) {
			errors.add("node " + node.type() + " " + key + "=" + ref
					+ " has no matching definition");
		}
	}

	/**
	 * Resource decode + validation (M4-5): shape/bounds checks run in
	 * {@code ResourceDefinition.fromFile}; band actions additionally must name
	 * registered action types (the same contract as ability spec nodes).
	 */
	public static io.github.durdeuvlad.lifepath.content.ResourceDefinition decodeResource(
			Identifier id,
			io.github.durdeuvlad.lifepath.content.ResourceDefinition.ResourceFile file) {
		var def = io.github.durdeuvlad.lifepath.content.ResourceDefinition.fromFile(id, file);
		java.util.List<Identifier> unknown = new ArrayList<>();
		java.util.Set<String> dangling = new java.util.LinkedHashSet<>();
		if (io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.isInitialized()) {
			for (var band : def.bands()) {
				for (var node : band.actions()) {
					if (io.github.durdeuvlad.lifepath.ability.AbilityVocabulary
							.action(node.type()) == null) {
						unknown.add(node.type());
					}
					// Band actions obey the same content-ref contract as
					// ability nodes — a dangling resource/skill fails loudly.
					checkRef(dangling, id, node, "resource", RESOURCES);
					checkRef(dangling, id, node, "skill", SKILLS);
				}
			}
		}
		// Band effect ids: a typo'd effect would be silently dropped every
		// sweep — fail the file the same way unknown action types do. Guarded:
		// vanilla Registries aren't bootstrapped in the unit-test environment,
		// so headless decodes defer this check to runtime fail-closed guards.
		try {
			for (var band : def.bands()) {
				for (var fx : band.effects()) {
					var entry = net.minecraft.registry.Registries.STATUS_EFFECT
							.getEntry(fx.effect());
					if (entry.isEmpty()) {
						throw new IllegalArgumentException("resource " + id
								+ " band effect names unknown status effect "
								+ fx.effect());
					}
					// Instant effects re-fire onApplied every sweep — they
					// cannot be "sustained" and would pulse each interval.
					if (entry.get().value().isInstant()) {
						throw new IllegalArgumentException("resource " + id
								+ " band effect " + fx.effect()
								+ " is instant — sustained effects require a "
								+ "duration-based status effect");
					}
					if (fx.durationTicks() < ((Number) LifepathConfig.getOrDefault(
							LifepathMod.id("resources"), "tick_interval_ticks", 20))
							.intValue()) {
						LifepathMod.LOGGER.warn("resource {} band effect {} "
								+ "duration_ticks {} is below tick_interval_ticks"
								+ " — it will expire mid-sweep", id, fx.effect(),
								fx.durationTicks());
					}
				}
			}
		} catch (ExceptionInInitializerError | NoClassDefFoundError e) {
			// Headless decode (tests): skip registry-backed validation only.
		}
		if (!unknown.isEmpty() || !dangling.isEmpty()) {
			java.util.List<String> all = new ArrayList<>();
			if (!unknown.isEmpty()) {
				all.add("band actions use unknown types " + unknown);
			}
			all.addAll(dangling);
			throw new IllegalArgumentException(
					"resource " + id + " invalid — " + String.join("; ", all));
		}
		return def;
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
			recordRef("species", def.id(), def.dietRules(), "diet");
			recordRef("species", def.id(), def.mobDispositions(), "relation");
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
			case "resource" -> RESOURCES;
			case "diet" -> DIETS;
			case "relation" -> RELATIONS;
			default -> null;
		};
	}
}
