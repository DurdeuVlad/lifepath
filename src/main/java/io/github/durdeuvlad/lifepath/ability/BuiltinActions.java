package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The M4-3 action vocabulary (GAMEDESIGN §12.3) — the "then do" primitives.
 * Registered additively in {@link AbilityVocabulary}. Actions acting on
 * entities/positions no-op when the resolved {@link TargetContext} lacks the
 * part they need (e.g. data-path evaluation has no live entities).
 *
 * <p><b>modify_attribute semantics:</b> modifiers are <i>temporary</i>
 * (vanilla never persists them across entity unload). {@code duration_ticks}
 * absent = transient-until-unload; present = auto-removed after that many
 * server ticks via an expiry queue on the ability tick.
 */
final class BuiltinActions {
	private BuiltinActions() {}

	private static boolean initialized;

	/** Pending attribute-modifier expiries (world, entity, attribute, modifier, tick). */
	private record PendingExpiry(net.minecraft.registry.RegistryKey<World> world,
			UUID entity, Identifier attribute, Identifier modifierId, long expireTick) {}

	private static final List<PendingExpiry> EXPIRIES = new ArrayList<>();
	private static long tick;

	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		// Pending expiries must not linger across an integrated-server restart
		// in the same JVM (dev); temporary modifiers die on unload anyway.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> EXPIRIES.clear());

		register("apply_effect", (target, ctx, params) -> {
			Identifier effect = id(params, "effect");
			if (!(target.entity() instanceof LivingEntity le) || effect == null) {
				return;
			}
			var entry = Registries.STATUS_EFFECT.getEntry(effect).orElse(null);
			if (entry == null) {
				return;
			}
			int duration = (int) num(params, "duration", 200);
			int amplifier = (int) num(params, "amplifier", 0);
			le.addStatusEffect(new StatusEffectInstance(entry, duration, amplifier));
		});

		register("remove_effect", (target, ctx, params) -> {
			if (!(target.entity() instanceof LivingEntity le)) {
				return;
			}
			String idOrTag = str(params, "effect");
			if (idOrTag == null) {
				return;
			}
			if (idOrTag.startsWith("#")) {
				Identifier tag = Identifier.tryParse(idOrTag.substring(1));
				if (tag == null) {
					return;
				}
				var key = net.minecraft.registry.tag.TagKey.of(
						net.minecraft.registry.RegistryKeys.STATUS_EFFECT, tag);
				le.getActiveStatusEffects().keySet().stream()
						.filter(e -> e.isIn(key)).toList()
						.forEach(le::removeStatusEffect);
			} else {
				Identifier effect = Identifier.tryParse(idOrTag);
				if (effect != null) {
					Registries.STATUS_EFFECT.getEntry(effect)
							.ifPresent(le::removeStatusEffect);
				}
			}
		});

		register("modify_attribute", (target, ctx, params) -> {
			Identifier attribute = id(params, "attribute");
			String opName = str(params, "operation");
			if (!(target.entity() instanceof LivingEntity le) || attribute == null
					|| opName == null || !hasNumber(params, "value")) {
				return;
			}
			var entry = Registries.ATTRIBUTE.getEntry(attribute).orElse(null);
			var instance = entry == null ? null : le.getAttributeInstance(entry);
			EntityAttributeModifier.Operation op = switch (opName) {
				case "add_value" -> EntityAttributeModifier.Operation.ADD_VALUE;
				case "add_multiplied_base" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
				case "add_multiplied_total" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
				default -> null;
			};
			if (instance == null || op == null) {
				return;
			}
			// Modifier id namespaces ability AND attribute — one ability may
			// touch several attributes without collisions; re-applying the same
			// (ability, attribute) pair replaces rather than stacking.
			Identifier modifierId = LifepathMod.id("ability/"
					+ (ctx.abilityId() != null ? ctx.abilityId() : "external").toString()
							.replace(':', '_')
					+ "/" + attribute.toString().replace(':', '_'));
			double value = num(params, "value", 0);
			instance.removeModifier(modifierId); // re-application replaces, never stacks
			instance.addTemporaryModifier(
					new EntityAttributeModifier(modifierId, value, op));
			if (hasNumber(params, "duration_ticks") && le.getWorld() instanceof ServerWorld) {
				// Keyed by the MODIFIED entity — expiry must find the target,
				// not the caster. Recast replaces the pending entry so the
				// first cast's timer can't truncate the refreshed duration.
				EXPIRIES.removeIf(e -> e.entity().equals(le.getUuid())
						&& e.attribute().equals(attribute)
						&& e.modifierId().equals(modifierId));
				EXPIRIES.add(new PendingExpiry(le.getWorld().getRegistryKey(),
						le.getUuid(), attribute, modifierId,
						tick + (long) num(params, "duration_ticks", 0)));
			}
		});

		register("damage", (target, ctx, params) -> {
			if (!(target.entity() instanceof LivingEntity le) || !hasNumber(params, "amount")) {
				return;
			}
			if (!(le.getWorld() instanceof ServerWorld world)) {
				return;
			}
			float amount = (float) num(params, "amount", 0);
			if (amount <= 0) {
				return; // negative damage would heal while playing hurt feedback
			}
			String source = str(params, "source");
			DamageSource ds = source == null ? world.getDamageSources().generic()
					: switch (source) {
						case "magic" -> world.getDamageSources().magic();
						case "starve" -> world.getDamageSources().starve();
						case "fall" -> world.getDamageSources().fall();
						case "fire" -> world.getDamageSources().inFire();
						case "wither" -> world.getDamageSources().wither();
						case "drown" -> world.getDamageSources().drown();
						default -> world.getDamageSources().generic();
					};
			le.damage(ds, amount);
		});

		// Sets the target burning — {@code duration_ticks} (default 80). Never
		// shortens an existing burn; re-application only extends.
		register("ignite", (target, ctx, params) -> {
			Entity e = target.entity();
			if (!(e instanceof LivingEntity)) {
				return;
			}
			int ticks = Math.max(1, (int) num(params, "duration_ticks", 80));
			e.setFireTicks(Math.max(e.getFireTicks(), ticks));
		});

		// Chorus-fruit-style blink (M8-1, Enderian): samples {@code attempts}
		// random offsets within {@code range} blocks, picks the first spot with
		// two non-colliding blocks for the body and a floor within 4 below —
		// never into a wall, void, or lava. No-op when every sample fails.
		register("random_teleport", (target, ctx, params) -> {
			Entity e = target.entity();
			if (e == null || !(e.getWorld() instanceof ServerWorld world)) {
				return;
			}
			// Bounded so malformed data can't turn one cast into a chunk-load storm.
			double range = Math.min(Math.max(num(params, "range", 8), 1), 64);
			int attempts = Math.min(Math.max(1, (int) num(params, "attempts", 16)), 64);
			var rand = world.getRandom();
			for (int i = 0; i < attempts; i++) {
				double tx = e.getX() + (rand.nextDouble() - 0.5) * 2 * range;
				double ty = e.getY() + (rand.nextDouble() - 0.5) * 2
						* Math.min(range, 6);
				double tz = e.getZ() + (rand.nextDouble() - 0.5) * 2 * range;
				BlockPos pos = BlockPos.ofFloored(tx, ty, tz);
				if (!isSafeSpot(world, pos)) {
					continue;
				}
				double fy = pos.getY();
				if (e instanceof ServerPlayerEntity sp) {
					sp.teleport(world, tx, fy, tz, sp.getYaw(), sp.getPitch());
				} else {
					e.teleport(world, tx, fy, tz,
							java.util.Set.of(), e.getYaw(), e.getPitch());
				}
				return;
			}
		});

		register("heal", (target, ctx, params) -> {
			if (target.entity() instanceof LivingEntity le && hasNumber(params, "amount")) {
				float amount = (float) num(params, "amount", 0);
				if (amount > 0) { // heal(<=0) bypasses the death path — never allow
					le.heal(amount);
				}
			}
		});

		register("grow_blocks", (target, ctx, params) -> {
			BlockPos pos = target.pos();
			if (pos == null || ctx.self() == null) {
				return;
			}
			ServerWorld world = (ServerWorld) ctx.self().getWorld();
			int rolls = Math.max(1, (int) num(params, "growth_rolls", 1));
			for (int i = 0; i < rolls; i++) {
				var state = world.getBlockState(pos);
				if (state.getBlock() instanceof net.minecraft.block.Fertilizable f
						&& f.isFertilizable(world, pos, state)) {
					f.grow(world, world.getRandom(), pos, state);
				} else {
					break;
				}
			}
		});

		register("freeze_water", (target, ctx, params) -> {
			BlockPos pos = target.pos();
			if (pos == null || ctx.self() == null) {
				return;
			}
			ServerWorld world = (ServerWorld) ctx.self().getWorld();
			if (!world.getBlockState(pos).isOf(Blocks.WATER)) {
				return;
			}
			boolean temporary = bool(params, "temporary", true);
			if (temporary) {
				// Same path as FrostWalker: frosted ice + a scheduled melt tick.
				// (randomTick alone only melts under light — night ice would persist.)
				world.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState());
				world.scheduleBlockTick(pos, Blocks.FROSTED_ICE,
						60 + world.getRandom().nextInt(61));
			} else {
				world.setBlockState(pos, Blocks.ICE.getDefaultState());
			}
		});

		register("highlight_entities", (target, ctx, params) -> {
			Entity e = target.entity();
			if (e == null || ctx.self() == null) {
				return;
			}
			int duration = (int) num(params, "duration_ticks", 100);
			String visibility = str(params, "visibility");
			var payload = new HighlightEntitiesPayload(List.of(e.getId()), duration);
			ServerWorld world = (ServerWorld) ctx.self().getWorld();
			if ("global".equals(visibility)) {
				for (ServerPlayerEntity watcher : world.getPlayers()) {
					if (watcher.squaredDistanceTo(e) <= 128 * 128) {
						ServerPlayNetworking.send(watcher, payload);
					}
				}
			} else {
				// private: the casting player only — never the global glowing flag
				ServerPlayNetworking.send(ctx.self(), payload);
			}
		});

		register("consume_item", (target, ctx, params) -> {
			if (!(target.entity() instanceof ServerPlayerEntity p)) {
				return;
			}
			String idOrTag = str(params, "item");
			Predicate<ItemStack> match = idOrTag == null ? null
					: BuiltinConditions.itemMatcher(idOrTag);
			if (match == null) {
				return;
			}
			int remaining = Math.max(1, (int) num(params, "count", 1));
			var inv = p.getInventory();
			// Main inventory only — consuming worn armor / the offhand item
			// would be a surprising way to strip a player's equipment.
			for (int i = 0; i < inv.main.size() && remaining > 0; i++) {
				ItemStack stack = inv.getStack(i);
				if (!stack.isEmpty() && match.test(stack)) {
					int take = Math.min(stack.getCount(), remaining);
					inv.removeStack(i, take);
					remaining -= take;
				}
			}
		});

		register("modify_resource", (target, ctx, params) -> {
			Identifier res = id(params, "resource");
			if (res == null || target.data() == null) {
				return;
			}
			ServerPlayerEntity sp = target.entity() instanceof ServerPlayerEntity p
					? p : null;
			if (hasNumber(params, "set_to")) {
				io.github.durdeuvlad.lifepath.resource.ResourceService.setTo(
						target.data(), sp, res, num(params, "set_to", 0.0), ctx.now());
			} else {
				io.github.durdeuvlad.lifepath.resource.ResourceService.modify(
						target.data(), sp, res, num(params, "delta", 0.0), ctx.now());
			}
		});

		// Two-state resource flip (M9-4, Phantom form toggle): min ↔ max of the
		// resource's definition range. No-op on unknown resources.
		register("toggle_resource", (target, ctx, params) -> {
			Identifier res = id(params, "resource");
			if (res == null || target.data() == null) {
				return;
			}
			var def = io.github.durdeuvlad.lifepath.registry.LifepathContent
					.resources().get(res);
			if (def == null) {
				return;
			}
			double cur = io.github.durdeuvlad.lifepath.resource.ResourceService
					.current(target.data(), res);
			double mid = (def.min() + def.max()) / 2.0;
			ServerPlayerEntity sp = target.entity() instanceof ServerPlayerEntity p
					? p : null;
			io.github.durdeuvlad.lifepath.resource.ResourceService.setTo(
					target.data(), sp, res, cur >= mid ? def.min() : def.max(), ctx.now());
		});

		register("play_sound", (target, ctx, params) -> {
			Identifier sound = id(params, "sound");
			BlockPos pos = target.pos();
			if (sound == null || pos == null || ctx.self() == null) {
				return;
			}
			var event = Registries.SOUND_EVENT.getOrEmpty(sound).orElse(null);
			if (event == null) {
				return;
			}
			float volume = (float) num(params, "volume", 1.0);
			float pitch = (float) num(params, "pitch", 1.0);
			ctx.self().getWorld().playSound(null, pos, event,
					SoundCategory.PLAYERS, volume, pitch);
		});

		register("spawn_particle", (target, ctx, params) -> {
			Identifier particle = id(params, "particle");
			BlockPos pos = target.pos();
			if (particle == null || pos == null || ctx.self() == null) {
				return;
			}
			ParticleType<?> type = Registries.PARTICLE_TYPE.getOrEmpty(particle).orElse(null);
			if (!(type instanceof ParticleEffect effect)) {
				// dust/block/item particles need parameters — fail visibly, not silently
				LifepathMod.LOGGER.debug(
						"[ability] spawn_particle skipped: {} is not a simple particle", particle);
				return;
			}
			int count = (int) num(params, "count", 8);
			double dx = num(params, "dx", 0.3), dy = num(params, "dy", 0.5),
					dz = num(params, "dz", 0.3);
			double speed = num(params, "speed", 0.02);
			((ServerWorld) ctx.self().getWorld()).spawnParticles(effect,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					Math.max(1, count), dx, dy, dz, speed);
		});
	}

	/** Processes the attribute-modifier expiry queue — driven by the ability tick. */
	static void onServerTick(net.minecraft.server.MinecraftServer server) {
		tick++;
		Iterator<PendingExpiry> it = EXPIRIES.iterator();
		while (it.hasNext()) {
			PendingExpiry e = it.next();
			if (tick < e.expireTick()) {
				continue;
			}
			it.remove();
			ServerWorld world = server.getWorld(e.world());
			Entity entity = world == null ? null : world.getEntity(e.entity());
			if (!(entity instanceof LivingEntity le)) {
				continue; // gone or unloaded — temporary modifiers die with the entity
			}
			var entry = Registries.ATTRIBUTE.getEntry(e.attribute()).orElse(null);
			var instance = entry == null ? null : le.getAttributeInstance(entry);
			if (instance != null) {
				instance.removeModifier(e.modifierId());
			}
		}
	}

	private static void register(String name, AbilityVocabulary.ActionExecutor exec) {
		AbilityVocabulary.registerAction(LifepathMod.id(name), exec);
	}

	/** Feet+head must be non-colliding, with a solid floor within 4 blocks below. */
	private static boolean isSafeSpot(ServerWorld world, BlockPos pos) {
		if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
				|| !world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) {
			return false;
		}
		for (int d = 1; d <= 4; d++) {
			BlockPos below = pos.down(d);
			var state = world.getBlockState(below);
			if (!state.getCollisionShape(world, below).isEmpty()) {
				return true;
			}
			if (!state.getFluidState().isEmpty()) {
				return false; // don't strand the caster in lava or mid-water
			}
		}
		return false;
	}

	@Nullable
	static Identifier id(JsonObject params, String key) {
		return AbilityVocabulary.id(params, key);
	}

	@Nullable
	static String str(JsonObject params, String key) {
		return BuiltinConditions.str(params, key);
	}

	static double num(JsonObject params, String key, double def) {
		return AbilityVocabulary.num(params, key, def);
	}

	static boolean bool(JsonObject params, String key, boolean def) {
		return BuiltinConditions.bool(params, key, def);
	}

	static boolean hasNumber(JsonObject params, String key) {
		return BuiltinConditions.hasNumber(params, key);
	}

	/** Test hook — pairs with {@link AbilityVocabulary#resetForTests}. */
	static void resetForTests() {
		initialized = false;
		EXPIRIES.clear();
	}
}
