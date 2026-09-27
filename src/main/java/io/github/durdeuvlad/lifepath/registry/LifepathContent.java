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
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.ConditionDefinition>
			CONDITIONS = new ContentRegistry<>(LifepathMod.id("condition"));
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.AttunementDefinition>
			ATTUNEMENTS = new ContentRegistry<>(LifepathMod.id("attunement"));
	/** M9-3: each item_weight file contributes an {@code id|#tag → weight} map. */
	private static final ContentRegistry<Map<String, Double>>
			ITEM_WEIGHTS = new ContentRegistry<>(LifepathMod.id("item_weight"));
	private static final ContentRegistry<io.github.durdeuvlad.lifepath.content.UnlockDefinition>
			UNLOCKS = new ContentRegistry<>(LifepathMod.id("unlock"));

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
		// Acquired conditions (M9-1): must see abilities/resources/diets —
		// loads after those domains, before the validation pass.
		ReloadManager.registerData(LifepathMod.id("condition"),
				manager -> loadDomain(manager, "condition",
						io.github.durdeuvlad.lifepath.content.ConditionDefinition.ConditionFile.CODEC,
						io.github.durdeuvlad.lifepath.content.ConditionDefinition::fromFile, CONDITIONS));
		// Acquired attunements (M9-2): refs abilities — load order same caveat.
		ReloadManager.registerData(LifepathMod.id("attunement"),
				manager -> loadDomain(manager, "attunement",
						io.github.durdeuvlad.lifepath.content.AttunementDefinition.AttunementFile.CODEC,
						io.github.durdeuvlad.lifepath.content.AttunementDefinition::fromFile, ATTUNEMENTS));
		// Encumbrance (M9-3): flat {"item|#tag": weight} maps, merged at query.
		ReloadManager.registerData(LifepathMod.id("item_weight"),
				manager -> loadDomain(manager, "item_weight",
						com.mojang.serialization.Codec.unboundedMap(
								com.mojang.serialization.Codec.STRING,
								com.mojang.serialization.Codec.DOUBLE),
						(id, map) -> map, ITEM_WEIGHTS));
		// Unlock grants (M9-4): sources fire -> unlocks[] content ids land.
		ReloadManager.registerData(LifepathMod.id("unlock"),
				manager -> loadDomain(manager, "unlock",
						io.github.durdeuvlad.lifepath.content.UnlockDefinition.UnlockFile.CODEC,
						io.github.durdeuvlad.lifepath.content.UnlockDefinition::fromFile, UNLOCKS));
		ReloadManager.registerData(LifepathMod.id("content_validation"),
				manager -> validateAll());
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

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.ConditionDefinition> conditions() {
		return CONDITIONS;
	}

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.AttunementDefinition> attunements() {
		return ATTUNEMENTS;
	}

	/** Merged {@code item|#tag → weight} tables keyed by file id (M9-3). */
	public static ContentRegistry<Map<String, Double>> itemWeights() {
		return ITEM_WEIGHTS;
	}

	public static ContentRegistry<io.github.durdeuvlad.lifepath.content.UnlockDefinition> unlocks() {
		return UNLOCKS;
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
			case "condition" -> CONDITIONS.contains(id);
			case "attunement" -> ATTUNEMENTS.contains(id);
			case "unlock" -> UNLOCKS.contains(id);
			// M9-4: unlocks[] holds gated CONTENT ids (species today), not def
			// ids — an entry is known iff it names gated content directly or a
			// surviving def still grants it.
			case "unlock_content" -> SPECIES.contains(id)
					|| UNLOCKS.all().values().stream()
							.anyMatch(def -> def.unlocks().contains(id));
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
			io.github.durdeuvlad.lifepath.event.ActivityTypes.ARCHERY,
			io.github.durdeuvlad.lifepath.event.ActivityTypes.DEFENCE,
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

	/** The most recent validation pass's report (startup or reload — same path). */
	public static ValidationReport lastValidationReport() {
		return LAST_REPORT;
	}

	private static ValidationReport LAST_REPORT = new ValidationReport(List.of());

	/**
	 * M7-5 full validation pass — runs AFTER the domain loaders (registration
	 * order) on startup and {@code /reload} alike. Collects EVERY issue into
	 * {@link #LAST_REPORT}: unresolved cross-domain refs (file+field+target
	 * named), structural range checks on loaded defs, within-file duplicate
	 * refs, and dependency cycles over the reference graph. The grouped
	 * summary is logged; the command source surfaces it via
	 * {@link #lastValidationReport()} on {@code /lifepath reload}.
	 */
	public static ValidationReport validateAll() {
		List<ValidationReport.Issue> issues = new ArrayList<>();
		// M12-1: leniently-decoded icon fields land here — a malformed icon
		// warns inside the report instead of failing its file.
		for (io.github.durdeuvlad.lifepath.content.IconRef.Warning w :
				io.github.durdeuvlad.lifepath.content.IconRef.drainWarnings()) {
			issues.add(new ValidationReport.Issue(ValidationReport.Severity.WARN,
					w.domain(), w.file(), w.field(), w.message()));
		}
		validateReferences(issues);
		validateStructures(issues);
		detectCycles(issues);
		ValidationReport report = new ValidationReport(List.copyOf(issues));
		LAST_REPORT = report;
		if (report.issues().isEmpty()) {
			LifepathMod.LOGGER.info("{}", report.summaryLine());
		} else {
			LifepathMod.LOGGER.warn("{}", report.summaryLine());
			for (String line : report.detailLines()) {
				LifepathMod.LOGGER.warn("{}", line);
			}
		}
		return report;
	}

	private static void validateReferences(List<ValidationReport.Issue> issues) {
		UNRESOLVED.clear();
		for (SpeciesDefinition def : SPECIES.all().values()) {
			recordRefs(issues, "species", def.id(), def.passiveAbilities(), "ability");
			recordRefs(issues, "species", def.id(), def.activeAbilities(), "ability");
			recordRefs(issues, "species", def.id(), def.minAptitudes().keySet(), "skill");
			recordRefs(issues, "species", def.id(), def.resources(), "resource");
			recordRef(issues, "species", def.id(), def.dietRules(), "diet");
			recordRef(issues, "species", def.id(), def.mobDispositions(), "relation");
		}
		for (SpecializationDefinition def : SPECIALIZATIONS.all().values()) {
			recordRefs(issues, "specialization", def.id(), def.startingSkills().keySet(), "skill");
			recordRefs(issues, "specialization", def.id(), def.aptitudes().keySet(), "skill");
			recordRefs(issues, "specialization", def.id(), def.xpModifiers().keySet(), "skill");
			recordRefs(issues, "specialization", def.id(), def.decayModifiers().keySet(), "skill");
			recordRefs(issues, "specialization", def.id(), def.protectedFloors().keySet(), "skill");
			recordRefs(issues, "specialization", def.id(), def.signatureRefs(), "ability");
		}
		for (SkillDefinition def : SKILLS.all().values()) {
			recordRef(issues, "skill", def.id(), def.levelCurve(), "level_curve");
			recordRefs(issues, "skill", def.id(), def.xpSources(), "xp_source");
			recordRef(issues, "skill", def.id(), def.passiveScaling(), "scaling");
			for (SkillDefinition.Milestone milestone : def.milestones()) {
				recordRefs(issues, "skill", def.id(), milestone.effectRefs(), "ability");
			}
		}
		for (XpSourceDefinition def : XP_SOURCES.all().values()) {
			recordRef(issues, "xp_source", def.id(), Optional.of(def.skill()), "skill");
		}
		for (var def : CONDITIONS.all().values()) {
			recordRefs(issues, "condition", def.id(), def.abilities(), "ability");
			recordRefs(issues, "condition", def.id(), def.resources(), "resource");
			recordRef(issues, "condition", def.id(), def.dietRules(), "diet");
			for (var stage : def.stages()) {
				recordRefs(issues, "condition", def.id(), stage.abilities(), "ability");
				for (Identifier event : stage.advanceEvents()) {
					if (!KNOWN_EVENT_TYPES.contains(event)) {
						LifepathMod.LOGGER.warn("condition {} stage {} advances on "
								+ "unknown event type {} — typo? it may never fire",
								def.id(), stage.id(), event);
					}
				}
			}
		}
		for (var def : ATTUNEMENTS.all().values()) {
			recordRefs(issues, "attunement", def.id(), def.abilities(), "ability");
			for (var rule : def.acquisition()) {
				rule.event().ifPresent(event -> {
					if (!KNOWN_EVENT_TYPES.contains(event)) {
						LifepathMod.LOGGER.warn("attunement {} acquires on unknown "
								+ "event type {} — typo? it may never fire",
								def.id(), event);
					}
				});
			}
		}
		for (var def : UNLOCKS.all().values()) {
			// Unlockable content today is species (selection:"unlocked") —
			// other domains warn rather than error since the field is generic.
			for (Identifier ref : def.unlocks()) {
				if (!SPECIES.contains(ref)) {
					LifepathMod.LOGGER.warn("unlock {} names {} — not a species "
							+ "id; it only gates selection when a locked species "
							+ "carries it", def.id(), ref);
				} else if (ABILITIES.contains(ref)) {
					// Ambiguity: ownedAbilities resolves held unlock ids
					// against the ability registry — a species id that is
					// ALSO an ability id would grant that ability silently.
					LifepathMod.LOGGER.warn("unlock {} names {} — that id is both "
							+ "a species and an ability; granting it also owns "
							+ "the ability. Rename one to keep them distinct.",
							def.id(), ref);
				}
			}
			for (var rule : def.sources()) {
				rule.event().ifPresent(event -> {
					if (!KNOWN_EVENT_TYPES.contains(event)) {
						LifepathMod.LOGGER.warn("unlock {} grants on unknown event "
								+ "type {} — typo? it may never fire", def.id(), event);
					}
				});
			}
		}
	}

	private static void recordRefs(List<ValidationReport.Issue> issues, String domain,
			Identifier source, Iterable<Identifier> refs, String targetDomain) {
		for (Identifier ref : refs) {
			recordRef(issues, domain, source, Optional.of(ref), targetDomain);
		}
	}

	private static void recordRef(List<ValidationReport.Issue> issues, String domain,
			Identifier source, Optional<Identifier> ref, String targetDomain) {
		ref.ifPresent(id -> {
			ContentRegistry<?> registry = registryFor(targetDomain);
			if (registry == null || !registry.contains(id)) {
				UNRESOLVED.add(new UnresolvedReference(domain, source, id, targetDomain));
				issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
						domain, source, "ref->" + targetDomain,
						"references missing " + targetDomain + " '" + id + "'"));
			}
		});
	}

	/**
	 * Structural checks on already-loaded defs — the issues a per-file codec
	 * cannot see because they compare fields or reference other defs.
	 */
	private static void validateStructures(List<ValidationReport.Issue> issues) {
		for (SpeciesDefinition def : SPECIES.all().values()) {
			var overlap = new java.util.HashSet<>(def.passiveAbilities());
			overlap.retainAll(def.activeAbilities());
			for (Identifier dup : overlap) {
				issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
						"species", def.id(), "passive_abilities/active_abilities",
						dup + " listed in BOTH passive and active — it would evaluate twice"));
			}
			duplicates("species", def.id(), "passive_abilities", def.passiveAbilities(), issues);
			duplicates("species", def.id(), "active_abilities", def.activeAbilities(), issues);
		}
		for (SpecializationDefinition def : SPECIALIZATIONS.all().values()) {
			def.startingSkills().forEach((skill, level) -> {
				SkillDefinition sd = SKILLS.get(skill);
				if (sd != null && (level < 0 || level > sd.maxLevel())) {
					issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
							"specialization", def.id(), "starting_skills." + skill,
							"level " + level + " outside [0," + sd.maxLevel() + "]"));
				}
			});
			def.protectedFloors().forEach((skill, floor) -> {
				SkillDefinition sd = SKILLS.get(skill);
				if (floor < 0 || (sd != null && floor > sd.maxLevel())) {
					issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
							"specialization", def.id(), "protected_floors." + skill,
							"floor " + floor + " outside [0," + (sd == null ? "?" : sd.maxLevel()) + "]"));
				}
			});
			duplicates("specialization", def.id(), "signature_abilities", def.signatureRefs(), issues);
		}
		for (SkillDefinition def : SKILLS.all().values()) {
			if (def.maxLevel() <= 0 || def.maxLevel() > 100) {
				issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
						"skill", def.id(), "max_level",
						"max_level " + def.maxLevel() + " outside [1,100]"));
			}
			java.util.Set<Integer> seen = new java.util.HashSet<>();
			for (SkillDefinition.Milestone m : def.milestones()) {
				if (m.level() <= 0 || m.level() > def.maxLevel()) {
					issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
							"skill", def.id(), "milestones[].level",
							"milestone level " + m.level() + " outside (0," + def.maxLevel() + "]"));
				}
				if (!seen.add(m.level())) {
					issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
							"skill", def.id(), "milestones[].level",
							"duplicate milestone level " + m.level()));
				}
			}
		}
	}

	private static void duplicates(String domain, Identifier file, String field,
			List<Identifier> refs, List<ValidationReport.Issue> issues) {
		java.util.Set<Identifier> seen = new java.util.HashSet<>();
		for (Identifier ref : refs) {
			if (!seen.add(ref)) {
				issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
						domain, file, field, "duplicate reference " + ref));
			}
		}
	}

	/**
	 * Cycle detection over the recursion-capable edge: a resource band's
	 * {@code modify_resource} entry action targeting ANOTHER resource. A→B→A
	 * would ping-pong band transitions on the tick loop; flag the cycle
	 * loudly so the author breaks it (data fix, not engine clamp).
	 */
	private static void detectCycles(List<ValidationReport.Issue> issues) {
		Map<Identifier, java.util.Set<Identifier>> edges = new java.util.HashMap<>();
		for (var def : RESOURCES.all().values()) {
			for (var band : def.bands()) {
				for (var node : band.actions()) {
					if (node.type().equals(LifepathMod.id("modify_resource"))) {
						var el = node.raw().get("resource");
						if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
								&& !el.getAsString().startsWith("#")) {
							Identifier target = Identifier.tryParse(el.getAsString());
							if (target != null && RESOURCES.contains(target)) {
								edges.computeIfAbsent(def.id(), k -> new java.util.HashSet<>())
										.add(target);
							}
						}
					}
				}
			}
		}
		// DFS over the resource->resource graph; report each back-edge target.
		java.util.Set<Identifier> visited = new java.util.HashSet<>();
		java.util.Set<Identifier> stack = new java.util.HashSet<>();
		java.util.List<Identifier> path = new java.util.ArrayList<>();
		java.util.Set<String> reported = new java.util.HashSet<>();
		for (Identifier start : edges.keySet()) {
			dfsCycles(start, edges, visited, stack, path, reported, issues);
		}
	}

	private static void dfsCycles(Identifier at,
			Map<Identifier, java.util.Set<Identifier>> edges,
			java.util.Set<Identifier> visited, java.util.Set<Identifier> stack,
			java.util.List<Identifier> path, java.util.Set<String> reported,
			List<ValidationReport.Issue> issues) {
		if (stack.contains(at)) {
			int from = path.indexOf(at);
			String cycle = String.join(" -> ",
					path.subList(from, path.size()).stream().map(Identifier::toString).toList())
					+ " -> " + at;
			if (reported.add(cycle)) {
				issues.add(new ValidationReport.Issue(ValidationReport.Severity.ERROR,
						"resource", at, "bands[].actions[modify_resource]",
						"cyclic band-action chain: " + cycle));
			}
			return;
		}
		if (!visited.add(at)) {
			return;
		}
		stack.add(at);
		path.add(at);
		for (Identifier next : edges.getOrDefault(at, java.util.Set.of())) {
			dfsCycles(next, edges, visited, stack, path, reported, issues);
		}
		path.remove(path.size() - 1);
		stack.remove(at);
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
