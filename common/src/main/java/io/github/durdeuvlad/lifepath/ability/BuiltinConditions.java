package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The M4-2 condition vocabulary (GAMEDESIGN §12.2) — the "if" primitives every
 * ability composes. Registered as additive entries in {@link AbilityVocabulary};
 * new conditions are a new registration + params, never engine surgery.
 *
 * <p>Params ride in the spec node's raw JsonObject: {@code idOrTag} params take
 * {@code "ns:id"} for an exact id or {@code "#ns:tag"} for a tag. Radius params
 * clamp to {@code abilities.toml} {@code nearby_max_radius}.
 *
 * <p>Conditions needing a world entity fail closed ({@code false}) when
 * {@code ctx.self()} is null (data-path evaluation has no player).
 */
public final class BuiltinConditions {
	private BuiltinConditions() {}

	private static boolean initialized;

	/** Registers all M4-2 primitives. Idempotent; called by {@link AbilityVocabulary#init}. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		register("biome_tag", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "tag");
			if (p == null || idOrTag == null) {
				return false;
			}
			var biome = p.level().getBiome(p.blockPosition());
			if (idOrTag.startsWith("#")) {
				ResourceLocation tag = ResourceLocation.tryParse(idOrTag.substring(1));
				return tag != null && biome.is(TagKey.create(Registries.BIOME, tag));
			}
			ResourceLocation id = ResourceLocation.tryParse(idOrTag);
			// Bare ids match either an exact biome or a tag by that name.
			return id != null && (biome.is(id)
					|| biome.is(TagKey.create(Registries.BIOME, id)));
		});
		register("dimension", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			ResourceLocation dim = id(params, "id");
			return p != null && dim != null
					&& p.level().dimension().location().equals(dim);
		});
		register("block_nearby", (ctx, params) ->
				io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
						"scan.block_nearby", () -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "block");
			if (p == null || idOrTag == null) {
				return false;
			}
			int radius = radius(params);
			Predicate<BlockState> match = blockMatcher(idOrTag);
			if (match == null) {
				return false;
			}
			Level world = p.level();
			BlockPos center = p.blockPosition();
			// iterateOutwards = cubic shells nearest-first (early exit finds the
			// closest match); isChunkLoaded guards against synchronous chunk
			// loads on the server tick at scan fringes.
			for (BlockPos pos : BlockPos.withinManhattan(center, radius, radius, radius)) {
				if (pos.closerThan(center, radius + 0.5)
						&& world.hasChunkAt(pos)
						&& match.test(world.getBlockState(pos))) {
					return true;
				}
			}
			return false;
		}));
		register("entity_nearby", (ctx, params) ->
				io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
						"scan.entity_nearby", () -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "entity");
			if (p == null || idOrTag == null) {
				return false;
			}
			int radius = radius(params);
			boolean livingOnly = bool(params, "living_only", true);
			Predicate<Entity> type = entityMatcher(idOrTag);
			if (type == null) {
				return false;
			}
			double r2 = radius * radius;
			return !p.level().getEntities(p, p.getBoundingBox().inflate(radius),
					e -> !e.isSpectator()
							&& (!livingOnly || e instanceof LivingEntity)
							&& e.distanceToSqr(p) <= r2
							&& type.test(e)).isEmpty();
		}));
		// Time-of-day thresholds, not World.isDay()/isNight() — the latter read
		// ambient darkness and lie during thunderstorms (night at noon) or in
		// fixed-time dimensions (never either).
		register("daylight", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			if (p == null) {
				return false;
			}
			long t = Math.floorMod(p.level().getDayTime(), 24000L);
			return t < 12300 || t >= 23700;
		});
		register("night", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			if (p == null) {
				return false;
			}
			long t = Math.floorMod(p.level().getDayTime(), 24000L);
			return t >= 12300 && t < 23700;
		});
		register("health_threshold", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String op = str(params, "op");
			return p != null && op != null && hasNumber(params, "value")
					&& compare(op, p.getHealth(), num(params, "value", 0));
		});
		register("inventory_contains", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "item");
			if (p == null || idOrTag == null) {
				return false;
			}
			Predicate<ItemStack> match = itemMatcher(idOrTag);
			if (match == null) {
				return false;
			}
			int needed = (int) num(params, "min_count", 1);
			if (needed < 1) {
				return false; // "contains ≤0 items" is vacuous — invalid input fails closed
			}
			int found = 0;
			for (int i = 0; i < p.getInventory().getContainerSize() && found < needed; i++) {
				ItemStack stack = p.getInventory().getItem(i);
				if (!stack.isEmpty() && match.test(stack)) {
					found += stack.getCount();
				}
			}
			return found >= needed;
		});
		register("equipment_contains", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "item");
			if (p == null || idOrTag == null) {
				return false;
			}
			Predicate<ItemStack> match = itemMatcher(idOrTag);
			if (match == null) {
				return false;
			}
			String slot = str(params, "slot");
			if (slot != null) {
				EquipmentSlot s = equipmentSlot(slot);
				return s != null && match.test(p.getItemBySlot(s));
			}
			for (EquipmentSlot s : EquipmentSlot.values()) {
				if (match.test(p.getItemBySlot(s))) {
					return true;
				}
			}
			return false;
		});
		register("submerged", (ctx, params) ->
				ctx.self() != null && ctx.self().isUnderWater());
		register("on_fire", (ctx, params) -> ctx.self() != null && ctx.self().isOnFire());
		register("weather", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String wanted = str(params, "state");
			if (p == null || wanted == null) {
				return false;
			}
			Level world = p.level();
			return switch (wanted) {
				case "thunder" -> world.isThundering();
				case "rain" -> world.isRaining() && !world.isThundering();
				case "clear" -> !world.isRaining();
				default -> false;
			};
		});
		// Data-path primitives — no live entity required.
		register("skill_level", (ctx, params) -> {
			ResourceLocation skill = id(params, "skill");
			String op = str(params, "op");
			if (skill == null || op == null) {
				return false;
			}
			SkillProgress progress = ctx.data().skill(skill);
			return hasNumber(params, "level")
					&& compare(op, progress != null ? progress.level() : 0,
							num(params, "level", 0));
		});
		register("resource_threshold", (ctx, params) -> {
			ResourceLocation res = id(params, "resource");
			String op = str(params, "op");
			if (res == null || op == null || !hasNumber(params, "value")) {
				return false;
			}
			// Unmaterialized resources resolve to their definition's default.
			double cur = io.github.durdeuvlad.lifepath.resource.ResourceService
					.current(ctx.data(), res);
			boolean known = ctx.data().resources().containsKey(res)
					|| io.github.durdeuvlad.lifepath.registry.LifepathContent
							.resources().get(res) != null;
			return known && compare(op, cur, num(params, "value", 0));
		});
		// M9-1 condition primitives — gate abilities on held condition state.
		register("has_condition", (ctx, params) -> {
			ResourceLocation cond = id(params, "condition");
			return cond != null && ctx.data() != null
					&& ctx.data().conditions().contains(cond);
		});
		register("condition_stage", (ctx, params) -> {
			ResourceLocation cond = id(params, "condition");
			if (cond == null || ctx.data() == null) {
				return false;
			}
			var st = ctx.data().conditionState(cond);
			return st != null && compare(str(params, "op") != null ? str(params, "op") : ">=",
					st.stage(), num(params, "stage", 0));
		});
		// M5-2 primitives: block occupancy + incoming-damage context.
		register("inside_block", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			String idOrTag = str(params, "block");
			if (p == null || idOrTag == null) {
				return false;
			}
			Predicate<BlockState> match = blockMatcher(idOrTag);
			// The block the feet occupy — "standing in vegetation/water/…".
			return match != null && match.test(
					p.level().getBlockState(p.blockPosition()));
		});
		register("attacker_entity", (ctx, params) -> {
			var damage = ctx.damage();
			String idOrTag = str(params, "entity");
			if (damage == null || damage.attacker() == null || idOrTag == null) {
				return false;
			}
			Predicate<Entity> match = entityMatcher(idOrTag);
			return match != null && match.test(damage.attacker());
		});
		register("damage_type", (ctx, params) -> {
			var damage = ctx.damage();
			// Param is "id" — "type" is the SpecNode discriminator and would
			// collide into a duplicate JSON key (M8-1 latent bug fix).
			String idOrTag = str(params, "id");
			if (damage == null || damage.source() == null || idOrTag == null) {
				return false;
			}
			if (idOrTag.startsWith("#")) {
				ResourceLocation tag = ResourceLocation.tryParse(idOrTag.substring(1));
				return tag != null && damage.source()
						.is(TagKey.create(Registries.DAMAGE_TYPE, tag));
			}
			ResourceLocation type = ResourceLocation.tryParse(idOrTag);
			return type != null && damage.source().typeHolder()
					.is(type);
		});
		register("damage_amount", (ctx, params) -> {
			var damage = ctx.damage();
			String op = str(params, "op");
			return damage != null && op != null && hasNumber(params, "value")
					&& compare(op, damage.amount(), num(params, "value", 0));
		});
		// B6: external faction probe — TeamLapen registry covers Vampirism AND
		// Werewolves through one surface. Fail-closed: absent mod / cut bridge
		// → false, so a deferred ability never wrongly fires.
		// {"type":"lifepath:player_faction","faction":"vampirism:vampire","min_level":2}
		register("player_faction", (ctx, params) -> {
			ServerPlayer p = ctx.self();
			ResourceLocation faction = id(params, "faction");
			if (p == null || faction == null) {
				return false;
			}
			var probe = io.github.durdeuvlad.lifepath.compat.vampirism
					.VampirismFactions.factionId(p);
			if (probe.isEmpty() || !probe.get().equals(faction)) {
				return false;
			}
			return !hasNumber(params, "min_level")
					|| io.github.durdeuvlad.lifepath.compat.vampirism.VampirismFactions
							.factionLevel(p) >= num(params, "min_level", 0);
		});
		// Origins-style negation: {"type":"lifepath:not","condition":{...node}}.
		// Fail-closed — malformed or throwing inner counts as "inner true",
		// so the not yields false and a defer can never wrongly suppress.
		register("not", (ctx, params) -> {
			JsonElement inner = params.get("condition");
			if (!(inner instanceof JsonObject obj)) {
				return false;
			}
			JsonElement type = obj.get("type");
			ResourceLocation typeId = type != null && type.isJsonPrimitive()
					? ResourceLocation.tryParse(type.getAsString()) : null;
			var eval = typeId == null ? null : AbilityVocabulary.condition(typeId);
			if (eval == null) {
				return false;
			}
			try {
				return !eval.test(ctx, obj);
			} catch (Exception e) {
				return false;
			}
		});
	}

	private static void register(String name, AbilityVocabulary.ConditionEvaluator eval) {
		AbilityVocabulary.registerCondition(LifepathMod.id(name), eval);
	}

	/** Test hook: paired with {@link AbilityVocabulary#resetForTests}. */
	static void resetForTests() {
		initialized = false;
	}

	/** Param radius, clamped to {@code abilities.toml} {@code nearby_max_radius}. */
	static int radius(JsonObject params) {
		int wanted = (int) num(params, "radius", 8);
		int max = ((Number) LifepathConfig.getOrDefault(
				LifepathMod.id("abilities"), "nearby_max_radius", 32)).intValue();
		return Math.max(0, Math.min(wanted, max));
	}

	/** {@code "ns:id"} → exact, {@code "#ns:tag"} → tag. Null on malformed input. */
	@Nullable
	static Predicate<BlockState> blockMatcher(String idOrTag) {
		if (idOrTag.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(idOrTag.substring(1));
			return tag == null ? null
					: state -> state.is(TagKey.create(Registries.BLOCK, tag));
		}
		ResourceLocation id = ResourceLocation.tryParse(idOrTag);
		var block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		return block == null ? null : state -> state.is(block);
	}

	@Nullable
	static Predicate<ItemStack> itemMatcher(String idOrTag) {
		if (idOrTag.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(idOrTag.substring(1));
			return tag == null ? null
					: stack -> stack.is(TagKey.create(Registries.ITEM, tag));
		}
		ResourceLocation id = ResourceLocation.tryParse(idOrTag);
		var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		return item == null ? null : stack -> stack.is(item);
	}

	@Nullable
	static Predicate<Entity> entityMatcher(String idOrTag) {
		if (idOrTag.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(idOrTag.substring(1));
			if (tag == null) {
				return null;
			}
			TagKey<net.minecraft.world.entity.EntityType<?>> key =
					TagKey.create(Registries.ENTITY_TYPE, tag);
			return e -> BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(e.getType()).is(key);
		}
		ResourceLocation id = ResourceLocation.tryParse(idOrTag);
		var type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		return type == null ? null : e -> e.getType() == type;
	}

	@Nullable
	private static EquipmentSlot equipmentSlot(String name) {
		return switch (name) {
			case "mainhand" -> EquipmentSlot.MAINHAND;
			case "offhand" -> EquipmentSlot.OFFHAND;
			case "head" -> EquipmentSlot.HEAD;
			case "chest" -> EquipmentSlot.CHEST;
			case "legs" -> EquipmentSlot.LEGS;
			case "feet" -> EquipmentSlot.FEET;
			case "body" -> EquipmentSlot.BODY;
			default -> null;
		};
	}

	/**
	 * Comparison operators for threshold-style conditions: {@code lt lte gt gte
	 * eq neq} plus the symbolic {@code < <= > >= == !=}. Unknown ops fail closed.
	 */
	static boolean compare(String op, double actual, double target) {
		return switch (op) {
			case "lt", "<" -> actual < target;
			case "lte", "<=" -> actual <= target;
			case "gt", ">" -> actual > target;
			case "gte", ">=" -> actual >= target;
			case "eq", "==" -> actual == target;
			case "neq", "!=" -> actual != target;
			default -> false;
		};
	}

	@Nullable
	static ResourceLocation id(JsonObject params, String key) {
		return AbilityVocabulary.id(params, key);
	}

	@Nullable
	static String str(JsonObject params, String key) {
		var el = params.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
				? el.getAsString() : null;
	}

	static double num(JsonObject params, String key, double def) {
		return AbilityVocabulary.num(params, key, def);
	}

	static boolean bool(JsonObject params, String key, boolean def) {
		var el = params.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()
				? el.getAsBoolean() : def;
	}

	/** True when {@code key} holds a JSON number — required-threshold params fail closed without it. */
	static boolean hasNumber(JsonObject params, String key) {
		var el = params.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
	}
}
