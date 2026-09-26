package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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

	/** Evaluation context handed to every evaluator. */
	public record EvalContext(@Nullable ServerPlayerEntity self,
			PlayerCharacterData data, long now) {}

	/**
	 * One resolved target — an entity wrapper so data-path tests (no live
	 * player) still exercise the per-target action loop; entity-requiring
	 * actions must null-check {@link #entity()}.
	 */
	public record TargetContext(@Nullable ServerPlayerEntity entity,
			PlayerCharacterData data) {}

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
			var state = ctx.data().resources().get(res);
			return state != null && state.current() >= num(params, "min", 0.0);
		});

		registerTarget(LifepathMod.id("self"), (ctx, params) ->
				List.of(new TargetContext(ctx.self(), ctx.data())));

		registerAction(LifepathMod.id("grant_xp"), (target, ctx, params) -> {
			Identifier skill = id(params, "skill");
			double amount = num(params, "amount", 0.0);
			if (skill == null || amount <= 0) {
				return;
			}
			SkillXpService.awardXp(target.data(), skill, amount,
					ActivityEvent.of(LifepathMod.id("ability"), skill));
		});
		registerAction(LifepathMod.id("resource_delta"), (target, ctx, params) -> {
			Identifier res = id(params, "resource");
			double delta = num(params, "amount", 0.0);
			if (res == null || delta == 0.0) {
				return;
			}
			var cur = ctx.data().resources().get(res);
			if (cur == null) {
				return;
			}
			double next = Math.max(cur.min(), Math.min(cur.max(), cur.current() + delta));
			ctx.data().setResource(res,
					new PlayerCharacterData.ResourceState(next, cur.min(), cur.max()));
		});
		registerAction(LifepathMod.id("debug_log"), (target, ctx, params) ->
				LifepathMod.LOGGER.info("[ability] {}", params.has("message")
						? params.get("message").getAsString() : "(no message)"));
	}

	@Nullable
	static Identifier id(JsonObject params, String key) {
		return params.has(key) ? Identifier.tryParse(params.get(key).getAsString()) : null;
	}

	static double num(JsonObject params, String key, double def) {
		return params.has(key) && params.get(key).isJsonPrimitive()
				? params.get(key).getAsDouble() : def;
	}

	/** Test hook: clears registrations + init flag. Not for production use. */
	public static void resetForTests() {
		CONDITIONS.clear();
		ACTIONS.clear();
		TARGETS.clear();
		initialized = false;
	}
}
