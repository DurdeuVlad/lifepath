package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
			ServerPlayerEntity p = ctx.self();
			String idOrTag = str(params, "tag");
			if (p == null || idOrTag == null) {
				return false;
			}
			var biome = p.getWorld().getBiome(p.getBlockPos());
			if (idOrTag.startsWith("#")) {
				Identifier tag = Identifier.tryParse(idOrTag.substring(1));
				return tag != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tag));
			}
			Identifier id = Identifier.tryParse(idOrTag);
			// Bare ids match either an exact biome or a tag by that name.
			return id != null && (biome.matchesId(id)
					|| biome.isIn(TagKey.of(RegistryKeys.BIOME, id)));
		});
		register("dimension", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			Identifier dim = id(params, "id");
			return p != null && dim != null
					&& p.getWorld().getRegistryKey().getValue().equals(dim);
		});
		register("block_nearby", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			String idOrTag = str(params, "block");
			if (p == null || idOrTag == null) {
				return false;
			}
			int radius = radius(params);
			Predicate<BlockState> match = blockMatcher(idOrTag);
			if (match == null) {
				return false;
			}
			World world = p.getWorld();
			BlockPos center = p.getBlockPos();
			// iterateOutwards = cubic shells nearest-first (early exit finds the
			// closest match); isChunkLoaded guards against synchronous chunk
			// loads on the server tick at scan fringes.
			for (BlockPos pos : BlockPos.iterateOutwards(center, radius, radius, radius)) {
				if (pos.isWithinDistance(center, radius + 0.5)
						&& world.isChunkLoaded(pos)
						&& match.test(world.getBlockState(pos))) {
					return true;
				}
			}
			return false;
		});
		register("entity_nearby", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
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
			return !p.getWorld().getOtherEntities(p, p.getBoundingBox().expand(radius),
					e -> !e.isSpectator()
							&& (!livingOnly || e instanceof LivingEntity)
							&& e.squaredDistanceTo(p) <= r2
							&& type.test(e)).isEmpty();
		});
		// Time-of-day thresholds, not World.isDay()/isNight() — the latter read
		// ambient darkness and lie during thunderstorms (night at noon) or in
		// fixed-time dimensions (never either).
		register("daylight", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			if (p == null) {
				return false;
			}
			long t = Math.floorMod(p.getWorld().getTimeOfDay(), 24000L);
			return t < 12300 || t >= 23700;
		});
		register("night", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			if (p == null) {
				return false;
			}
			long t = Math.floorMod(p.getWorld().getTimeOfDay(), 24000L);
			return t >= 12300 && t < 23700;
		});
		register("health_threshold", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			String op = str(params, "op");
			return p != null && op != null && hasNumber(params, "value")
					&& compare(op, p.getHealth(), num(params, "value", 0));
		});
		register("inventory_contains", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
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
			for (int i = 0; i < p.getInventory().size() && found < needed; i++) {
				ItemStack stack = p.getInventory().getStack(i);
				if (!stack.isEmpty() && match.test(stack)) {
					found += stack.getCount();
				}
			}
			return found >= needed;
		});
		register("equipment_contains", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
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
				return s != null && match.test(p.getEquippedStack(s));
			}
			for (EquipmentSlot s : EquipmentSlot.values()) {
				if (match.test(p.getEquippedStack(s))) {
					return true;
				}
			}
			return false;
		});
		register("submerged", (ctx, params) ->
				ctx.self() != null && ctx.self().isSubmergedInWater());
		register("on_fire", (ctx, params) -> ctx.self() != null && ctx.self().isOnFire());
		register("weather", (ctx, params) -> {
			ServerPlayerEntity p = ctx.self();
			String wanted = str(params, "state");
			if (p == null || wanted == null) {
				return false;
			}
			World world = p.getWorld();
			return switch (wanted) {
				case "thunder" -> world.isThundering();
				case "rain" -> world.isRaining() && !world.isThundering();
				case "clear" -> !world.isRaining();
				default -> false;
			};
		});
		// Data-path primitives — no live entity required.
		register("skill_level", (ctx, params) -> {
			Identifier skill = id(params, "skill");
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
			Identifier res = id(params, "resource");
			String op = str(params, "op");
			if (res == null || op == null) {
				return false;
			}
			var state = ctx.data().resources().get(res);
			return state != null && hasNumber(params, "value")
					&& compare(op, state.current(), num(params, "value", 0));
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
			Identifier tag = Identifier.tryParse(idOrTag.substring(1));
			return tag == null ? null
					: state -> state.isIn(TagKey.of(RegistryKeys.BLOCK, tag));
		}
		Identifier id = Identifier.tryParse(idOrTag);
		var block = id == null ? null : Registries.BLOCK.getOrEmpty(id).orElse(null);
		return block == null ? null : state -> state.isOf(block);
	}

	@Nullable
	static Predicate<ItemStack> itemMatcher(String idOrTag) {
		if (idOrTag.startsWith("#")) {
			Identifier tag = Identifier.tryParse(idOrTag.substring(1));
			return tag == null ? null
					: stack -> stack.isIn(TagKey.of(RegistryKeys.ITEM, tag));
		}
		Identifier id = Identifier.tryParse(idOrTag);
		var item = id == null ? null : Registries.ITEM.getOrEmpty(id).orElse(null);
		return item == null ? null : stack -> stack.isOf(item);
	}

	@Nullable
	static Predicate<Entity> entityMatcher(String idOrTag) {
		if (idOrTag.startsWith("#")) {
			Identifier tag = Identifier.tryParse(idOrTag.substring(1));
			if (tag == null) {
				return null;
			}
			TagKey<net.minecraft.entity.EntityType<?>> key =
					TagKey.of(RegistryKeys.ENTITY_TYPE, tag);
			return e -> Registries.ENTITY_TYPE.getEntry(e.getType()).isIn(key);
		}
		Identifier id = Identifier.tryParse(idOrTag);
		var type = id == null ? null : Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
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
	static Identifier id(JsonObject params, String key) {
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
