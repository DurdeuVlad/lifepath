package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The open vocabulary behind ability composition (M4-1; extended by M4-2/4-3):
 * condition, action, and target evaluators keyed by {@code lifepath:<type>} ids.
 * Spec nodes in ability JSON select an evaluator by {@code "type"} and pass
 * their remaining fields as the raw {@link JsonObject}.
 *
 * <p>Registration is additive and reload-independent — a datapack never names
 * Java classes; it names vocabulary ids. Unknown ids degrade gracefully: the
 * engine logs at DEBUG and treats an unknown condition as failed / an unknown
 * action as a no-op.
 */
public final class AbilityVocabulary {
	private AbilityVocabulary() {}

	/**
	 * Evaluation context handed to every evaluator. {@link #self()} is null on
	 * data paths (unit tests, headless evaluation) — entity-requiring
	 * evaluators must null-check it rather than NPE. {@link #abilityId()} is
	 * the evaluating ability (for namespacing e.g. attribute modifiers); null
	 * only in bare test contexts.
	 */
	public record EvalContext(@Nullable ServerPlayerEntity self,
			PlayerCharacterData data, long now, @Nullable Identifier abilityId) {
		public EvalContext(ServerPlayerEntity self, PlayerCharacterData data, long now) {
			this(self, data, now, null);
		}
	}

	/**
	 * One resolved target: an entity ({@link #entity()}, any {@link Entity} —
	 * AoE resolvers return non-players too) and/or a position
	 * ({@link #pos()}, for block targets). Actions must null-check whichever
	 * they need. {@link #data()} is <b>the target's own</b> character model —
	 * set for player targets ({@code self}, players resolved by
	 * {@code entities_in_radius}), null for non-player entities and block
	 * targets. Resource/XP actions therefore apply per-target to player
	 * models and no-op on mobs — never multiplied against the caster's model.
	 */
	public record TargetContext(@Nullable Entity entity,
			@Nullable BlockPos pos, @Nullable PlayerCharacterData data) {
		public TargetContext(@Nullable Entity entity, @Nullable PlayerCharacterData data) {
			this(entity, entity == null ? null : entity.getBlockPos(), data);
		}
	}

	/**
	 * THE extension contract (M4-6): new ability mechanics register here by id —
	 * never by subclassing the engine or adding content-specific classes.
	 * Rules every evaluator/executor/resolver must obey:
	 * <ul>
	 *   <li><b>Register during mod init</b> (before datapack load) — load-time
	 *       validation rejects ability files naming unregistered types.</li>
	 *   <li><b>Fail closed:</b> missing/malformed params return false/no-op —
	 *       never throw for content errors (the engine isolates throws, but a
	 *       throwing evaluator silently disables the ability).</li>
	 *   <li><b>Nullability:</b> {@code ctx.self()} may be null (data-path evals);
	 *       {@code target.entity()} is null for block targets and
	 *       {@code target.data()} is null for model-less targets — guard both.</li>
	 *   <li><b>Authority:</b> runs on the server thread only; mutate character
	 *       state through services ({@code ResourceService}, {@code
	 *       SkillXpService}, {@code CooldownService}), never the model maps.</li>
	 *   <li><b>Ref params:</b> a node referencing content declares it via the
	 *       conventional keys {@code "resource"} / {@code "skill"} so load-time
	 *       validation can check existence.</li>
	 * </ul>
	 * Registry-ids inside params (effects/items/tags/sounds) can't be checked
	 * at decode — evaluators must resolve them fail-closed at use time.
	 */
	@FunctionalInterface
	public interface ConditionEvaluator {
		boolean test(EvalContext ctx, JsonObject params);
	}

	@FunctionalInterface
	public interface ActionExecutor {
		void run(TargetContext target, EvalContext ctx, JsonObject params);
	}

	@FunctionalInterface
	public interface TargetResolver {
		List<TargetContext> resolve(EvalContext ctx, JsonObject params);
	}

	private static final Map<Identifier, ConditionEvaluator> CONDITIONS = new ConcurrentHashMap<>();
	private static final Map<Identifier, ActionExecutor> ACTIONS = new ConcurrentHashMap<>();
	private static final Map<Identifier, TargetResolver> TARGETS = new ConcurrentHashMap<>();

	public static void registerCondition(Identifier id, ConditionEvaluator eval) {
		CONDITIONS.putIfAbsent(id, eval);
	}

	public static void registerAction(Identifier id, ActionExecutor exec) {
		ACTIONS.putIfAbsent(id, exec);
	}

	public static void registerTarget(Identifier id, TargetResolver resolver) {
		TARGETS.putIfAbsent(id, resolver);
	}

	@Nullable
	public static ConditionEvaluator condition(Identifier id) {
		return CONDITIONS.get(id);
	}

	@Nullable
	public static ActionExecutor action(Identifier id) {
		return ACTIONS.get(id);
	}

	@Nullable
	public static TargetResolver target(Identifier id) {
		return TARGETS.get(id);
	}

	private static boolean initialized;

	/** Whether the built-in vocabulary has been registered (load-time validators gate on this). */
	public static boolean isInitialized() {
		return initialized;
	}

	/** Registers the built-in vocabulary. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		registerCondition(LifepathMod.id("always"), (ctx, params) -> true);
		registerCondition(LifepathMod.id("has_resource"), (ctx, params) -> {
			Identifier res = id(params, "resource");
			if (res == null) {
				return false;
			}
			// Fail closed on unknown ids — a typo'd/removed resource file must
			// not satisfy the bare condition via the 0.0 fallback.
			boolean known = ctx.data().resources().containsKey(res)
					|| io.github.durdeuvlad.lifepath.registry.LifepathContent
							.resources().get(res) != null;
			// Unmaterialized-but-defined resources read as their default.
			return known && io.github.durdeuvlad.lifepath.resource.ResourceService
					.current(ctx.data(), res) >= num(params, "min", 0.0);
		});

		registerTarget(LifepathMod.id("self"), (ctx, params) ->
				List.of(new TargetContext(ctx.self(), ctx.data())));
		BuiltinTargets.init();
		BuiltinActions.init();

		registerAction(LifepathMod.id("grant_xp"), (target, ctx, params) -> {
			Identifier skill = id(params, "skill");
			double amount = num(params, "amount", 0.0);
			if (skill == null || amount <= 0 || target.data() == null) {
				return;
			}
			SkillXpService.awardXp(target.data(), skill, amount,
					ActivityEvent.of(LifepathMod.id("ability"), skill));
		});
		registerAction(LifepathMod.id("resource_delta"), (target, ctx, params) -> {
			Identifier res = id(params, "resource");
			double delta = num(params, "amount", 0.0);
			if (res == null || delta == 0.0 || target.data() == null) {
				return;
			}
			// Routes through ResourceService: def-bounds clamp, band
			// transitions, delta sync — never a raw map write.
			io.github.durdeuvlad.lifepath.resource.ResourceService.modify(
					target.data(),
					target.entity() instanceof ServerPlayerEntity sp ? sp : null,
					res, delta, ctx.now());
		});
		BuiltinConditions.init(); // M4-2 primitive vocabulary

		registerAction(LifepathMod.id("debug_log"), (target, ctx, params) -> {
			var el = params.get("message");
			LifepathMod.LOGGER.info("[ability] {}",
					el != null && el.isJsonPrimitive() ? el.getAsString() : "(no message)");
		});
	}

	@Nullable
	static Identifier id(JsonObject params, String key) {
		var el = params.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
				? Identifier.tryParse(el.getAsString()) : null;
	}

	static double num(JsonObject params, String key, double def) {
		var el = params.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
				? el.getAsDouble() : def;
	}

	/**
	 * Load-time validation for ability files (M4-2 contract): every spec node's
	 * {@code type} must resolve to a registered condition/target/action —
	 * unknown types fail the file (naming the ability) rather than silently
	 * fail-closing forever. Returns the list of unknown types; empty when the
	 * vocabulary isn't initialized (data-path tests that never ran {@link #init}).
	 */
	public static java.util.List<Identifier> unknownNodeTypes(
			io.github.durdeuvlad.lifepath.content.AbilityDefinition def) {
		if (!initialized) {
			return java.util.List.of();
		}
		java.util.List<Identifier> unknown = new java.util.ArrayList<>();
		for (var node : def.conditions().all()) {
			if (condition(node.type()) == null) {
				unknown.add(node.type());
			}
		}
		for (var node : def.conditions().any()) {
			if (condition(node.type()) == null) {
				unknown.add(node.type());
			}
		}
		if (target(def.target().type()) == null) {
			unknown.add(def.target().type());
		}
		for (var node : def.actions()) {
			if (action(node.type()) == null) {
				unknown.add(node.type());
			}
		}
		return unknown;
	}

	/** Test hook: clears registrations + init flag. Not for production use. */
	public static void resetForTests() {
		CONDITIONS.clear();
		ACTIONS.clear();
		TARGETS.clear();
		initialized = false;
		BuiltinConditions.resetForTests();
		BuiltinTargets.resetForTests();
		BuiltinActions.resetForTests();
	}
}
