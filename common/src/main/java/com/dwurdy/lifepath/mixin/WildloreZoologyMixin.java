package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wildlore zoology awards → parallel lifepath activities (NeoForge-only,
 * gated by the {@code lifepath.compat.wildlore.mixins.json} config's
 * {@code requiredMods}). Verified against wildlore-1.0.0 bytecode:
 * {@code Zoology}'s public static {@code award*}/{@code study} methods are the
 * mod's own per-action take points — every caller-side gate (feed/harvest
 * cooldowns, tame/breed/birth event checks, player-caused kills) has already
 * run by the time they fire, so a TAIL hook sees exactly the actions Wildlore
 * credited. This is a SHARE bridge: Wildlore's zoology XP is untouched; we
 * publish the equivalent {@link ActivityEvent} and let xp_source data decide
 * what it pays.
 *
 * <p>Mapping (act tag = the {@code lifepath:<act>} tag xp_source
 * {@code per_tag} keys on):
 * <ul>
 *   <li>{@code awardFeed} → {@code lifepath:husbandry} + {@code lifepath:fed}</li>
 *   <li>{@code awardBreed} → {@code lifepath:husbandry} + {@code lifepath:bred}</li>
 *   <li>{@code awardBirth} → {@code lifepath:husbandry} + {@code lifepath:born}</li>
 *   <li>{@code awardTame} → {@code lifepath:husbandry} + {@code lifepath:cared}</li>
 *   <li>{@code awardHarvest} → {@code lifepath:husbandry} + {@code lifepath:milked}
 *       + {@code lifepath:sheared} (Wildlore's harvest covers milk/shear-style
 *       interactions and cannot be distinguished; both tags are the same
 *       weight band in the shipped xp_source)</li>
 *   <li>{@code study} → {@code lifepath:husbandry} + {@code lifepath:studied}
 *       (no {@code lifepath:scholarship} activity type exists — scholarship
 *       XP binds {@code lifepath:crafting} + {@code lifepath:scholarly}, so
 *       the field-journal inspect stays in the husbandry domain. The
 *       {@code studied} tag is unmatched in current xp_source and resolves
 *       via per_subject/base_xp.)</li>
 * </ul>
 *
 * <p>{@code awardKill} is deliberately NOT hooked: it fires on the same
 * {@code LivingDeathEvent} the vanilla kill seam consumes, and Wildlore's
 * {@code MobRules.isTracked} accepts every non-player {@code Mob} — a hook
 * would double-pay combat/athletics/hunting XP on every tracked kill.
 *
 * <p>The same overlap applies to the hooked awards: a vanilla cow feed
 * credits {@code awardFeed} AND {@code Animal.mobInteract}, so both seams
 * would emit {@code lifepath:fed} for one act. Every lifepath
 * feed/breed/birth/tame/milk/shear seam lives on the {@link Animal}
 * family, so the award hooks emit only for subjects those seams cannot
 * see — non-{@code Animal} mobs (Wildlore's exotic wildlife:
 * {@code PathfinderMob}/{@code WaterAnimal} critters). {@code study} has
 * no vanilla equivalent at all and stays ungated.
 */
@Pseudo
@Mixin(targets = "dev.tinsley.wildlore.zoology.Zoology", remap = false)
public abstract class WildloreZoologyMixin {

	@Inject(method = "awardFeed", at = @At("TAIL"), remap = false, require = 0)
	private static void lifepath$awardFeed(ServerPlayer player, EntityType<?> type,
			CallbackInfo ci) {
		lifepath$award(player, type, "fed");
	}

	@Inject(method = "awardBreed", at = @At("TAIL"), remap = false, require = 0)
	private static void lifepath$awardBreed(ServerPlayer player, EntityType<?> type,
			CallbackInfo ci) {
		lifepath$award(player, type, "bred");
	}

	@Inject(method = "awardBirth", at = @At("TAIL"), remap = false, require = 0)
	private static void lifepath$awardBirth(ServerPlayer player, EntityType<?> type,
			CallbackInfo ci) {
		lifepath$award(player, type, "born");
	}

	@Inject(method = "awardTame", at = @At("TAIL"), remap = false, require = 0)
	private static void lifepath$awardTame(ServerPlayer player, EntityType<?> type,
			CallbackInfo ci) {
		lifepath$award(player, type, "cared");
	}

	@Inject(method = "awardHarvest", at = @At("TAIL"), remap = false, require = 0)
	private static void lifepath$awardHarvest(ServerPlayer player, EntityType<?> type,
			CallbackInfo ci) {
		lifepath$award(player, type, "milked", "sheared");
	}

	/**
	 * Field-journal study → husbandry. {@code study} early-returns on the
	 * {@code zoologyEnabled} config and the per-entity {@code STUDY_COOLDOWNS}
	 * gate before touching XP, so a TAIL hook would pay for rejected studies —
	 * the injection point is the {@code gain} call itself (fires only when
	 * Wildlore actually awarded the study). Edge case: a pack that sets
	 * {@code xpStudy <= 0} still reaches {@code gain} (which no-ops inside) —
	 * the act occurred, so the event still emits.
	 */
	@Inject(method = "study",
			at = @At(value = "INVOKE",
					target = "Ldev/tinsley/wildlore/zoology/Zoology;gain"
							+ "(Lnet/minecraft/server/level/ServerPlayer;"
							+ "Lnet/minecraft/world/entity/EntityType;"
							+ "ILjava/util/function/Function;)V",
					shift = At.Shift.AFTER),
			remap = false, require = 0)
	private static void lifepath$study(ServerPlayer player, LivingEntity studied,
			CallbackInfo ci) {
		lifepath$husbandry(player, studied == null ? null : studied.getType(), "studied");
	}

	/**
	 * Award-path emit — skips subjects the vanilla husbandry seams cover.
	 * Every vanilla feed/breed/birth/tame/milk/shear emit lives on the
	 * {@link Animal} family ({@code Animal.mobInteract},
	 * {@code spawnChildFromBreeding}, {@code TamableAnimal.tame}, the
	 * Animal-gated item-side funnel), and Wildlore's {@code isTracked}
	 * accepts every non-player {@code Mob} — so an {@code Animal} subject
	 * would emit the same act tag twice for one interact (a vanilla cow
	 * feed). Non-{@code Animal} mobs are precisely the gap this bridge
	 * exists for. Residual miss: a Wildlore mechanic that cancels the
	 * interact before {@code mobInteract} also loses its vanilla pay —
	 * the same fail-closed trade the rest of the pack makes.
	 */
	@Unique
	private static void lifepath$award(ServerPlayer player, EntityType<?> type,
			String... acts) {
		if (type == null || lifepath$vanillaCovers(type, player)) {
			return;
		}
		lifepath$husbandry(player, type, acts);
	}

	/** Per-type probe cache: one throwaway construction per entity type. */
	@Unique
	private static final Map<EntityType<?>, Boolean> lifepath$VANILLA_COVERED =
			new ConcurrentHashMap<>();

	/**
	 * {@code true} when the subject's class is in the {@link Animal} family —
	 * the only classes the vanilla seams can reach. {@code
	 * EntityType.create(Level)} is the only public type→instance path (there
	 * is no type→class map); the probe is never added to the world. A
	 * {@code null} factory probes as non-Animal (exotic type — emit); a
	 * throwable probes as covered (never emit on uncertainty).
	 */
	@Unique
	private static boolean lifepath$vanillaCovers(EntityType<?> type,
			ServerPlayer player) {
		return lifepath$VANILLA_COVERED.computeIfAbsent(type, t -> {
			try {
				net.minecraft.world.entity.Entity probe =
						t.create(player.serverLevel());
				return probe instanceof net.minecraft.world.entity.animal.Animal;
			} catch (Throwable t2) {
				return true;
			}
		});
	}

	/**
	 * Emits the {@code lifepath:husbandry} activity: subject = the acted-on
	 * entity type id; tags = the entity type's own tags (the {@code
	 * lifepath:animals} membership that xp_source {@code required_tags} gates
	 * on) plus the act tag(s). Emitted with {@code Cause.PLAYER} — every
	 * Wildlore award point is already player-caused by construction.
	 */
	@Unique
	private static void lifepath$husbandry(ServerPlayer player, EntityType<?> type,
			String... acts) {
		if (player == null || lifepath$isAutomation(player) || type == null) {
			return;
		}
		Set<ResourceLocation> tags = lifepath$typeTags(type);
		for (String act : acts) {
			tags.add(LifepathMod.id(act));
		}
		ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		Map<String, String> attrs = acts.length == 0
				? Map.of()
				: Map.of("act", String.join(",", acts), "source", "wildlore");
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.HUSBANDRY,
				typeId, tags, ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), attrs));
	}

	@Unique
	private static Set<ResourceLocation> lifepath$typeTags(EntityType<?> type) {
		return type.builtInRegistryHolder().tags().map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
	}

	/** Same contract as the vanilla producers: machine players (Create
	 * deployers, loader FakePlayers) pass {@code instanceof ServerPlayer}, so
	 * every attributed event must exclude them. Uninitialized platform (tests)
	 * conservatively means "real". */
	@Unique
	private static boolean lifepath$isAutomation(ServerPlayer player) {
		return Platform.isInitialized() && Platform.get().isAutomation(player);
	}
}
