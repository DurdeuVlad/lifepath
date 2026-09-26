package io.github.durdeuvlad.lifepath.ability;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.EvalContext;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload;
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

	/** Pending attribute-modifier expiries (player, attribute, modifier, tick). */
	private record PendingExpiry(UUID player, Identifier attribute,
			Identifier modifierId, long expireTick) {}

	private static final List<PendingExpiry> EXPIRIES = new ArrayList<>();
	private static long tick;

	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

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
			Identifier modifierId = LifepathMod.id("ability/"
					+ (ctx.abilityId() != null ? ctx.abilityId() : "external").toString()
							.replace(':', '_'));
			double value = num(params, "value", 0);
			instance.removeModifier(modifierId); // re-application replaces, never stacks
			instance.addTemporaryModifier(
					new EntityAttributeModifier(modifierId, value, op));
			if (hasNumber(params, "duration_ticks") && ctx.self() != null) {
				EXPIRIES.add(new PendingExpiry(ctx.self().getUuid(), attribute,
						modifierId, tick + (long) num(params, "duration_ticks", 0)));
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
			String source = str(params, "source");
			DamageSource ds = source == null ? world.getDamageSources().generic()
					: switch (source) {
						case "magic" -> world.getDamageSources().magic();
						case "starve" -> world.getDamageSources().starve();
						case "fall" -> world.getDamageSources().fall();
						case "fire" -> world.getDamageSources().inFire();
						case "wither" -> world.getDamageSources().wither();
						default -> world.getDamageSources().generic();
					};
			le.damage(ds, amount);
		});

		register("heal", (target, ctx, params) -> {
			if (target.entity() instanceof LivingEntity le && hasNumber(params, "amount")) {
				le.heal((float) num(params, "amount", 0));
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
			world.setBlockState(pos, temporary
					? Blocks.FROSTED_ICE.getDefaultState() : Blocks.ICE.getDefaultState());
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
						net.fabricmc.fabric.api.networking.v1
								.ServerPlayNetworking.send(watcher, payload);
					}
				}
			} else {
				// private: the casting player only — never the global glowing flag
				net.fabricmc.fabric.api.networking.v1
						.ServerPlayNetworking.send(ctx.self(), payload);
			}
		});

		register("consume_item", (target, ctx, params) -> {
			if (!(target.entity() instanceof ServerPlayerEntity p)) {
				return;
			}
			String idOrTag = str(params, "item");
			Predicate<ItemStack> match = BuiltinConditions.itemMatcher(idOrTag);
			if (match == null) {
				return;
			}
			int remaining = Math.max(1, (int) num(params, "count", 1));
			var inv = p.getInventory();
			for (int i = 0; i < inv.size() && remaining > 0; i++) {
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
			if (res == null) {
				return;
			}
			var cur = target.data().resources().get(res);
			if (cur == null) {
				return;
			}
			double next;
			if (hasNumber(params, "set_to")) {
				next = num(params, "set_to", cur.current());
			} else {
				next = cur.current() + num(params, "delta", 0.0);
			}
			next = Math.max(cur.min(), Math.min(cur.max(), next));
			target.data().setResource(res,
					new io.github.durdeuvlad.lifepath.character.PlayerCharacterData
							.ResourceState(next, cur.min(), cur.max()));
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
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.player());
			if (player == null) {
				continue; // offline — temporary modifiers die with the session anyway
			}
			var entry = Registries.ATTRIBUTE.getEntry(e.attribute()).orElse(null);
			var instance = entry == null ? null : player.getAttributeInstance(entry);
			if (instance != null) {
				instance.removeModifier(e.modifierId());
			}
		}
	}

	private static void register(String name, AbilityVocabulary.ActionExecutor exec) {
		AbilityVocabulary.registerAction(LifepathMod.id(name), exec);
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
