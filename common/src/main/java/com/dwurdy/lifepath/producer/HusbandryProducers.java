package com.dwurdy.lifepath.producer;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Husbandry activity producers: feed/breed/birth/milk/shear/care on animals.
 * Called by the {@code Animal}/{@code Cow}/{@code Sheep}/{@code Goat}/
 * {@code MushroomCow}/{@code AbstractHorse}/{@code ItemStack} mixins (vanilla
 * surface, base mixin config) and by the Animal Husbandry compat mixin
 * ({@code lifepath.compat.animalhusbandry.mixins.json}) — producers emit
 * events; xp_source data owns all amounts.
 *
 * <p><b>Marker contract (pack-staged):</b> every emitted event's tag set is
 * the subject's entity-type tags (which carry {@code lifepath:animals} for
 * datapack-tagged species) plus exactly one synthetic marker:
 * {@code lifepath:fed|lifepath:cared|lifepath:bred|lifepath:born|
 * lifepath:milked|lifepath:sheared}. sourceId = the animal's entity type id
 * (the newborn's own type for {@code born}).
 *
 * <p><b>Emit-point map:</b>
 * <ul>
 *   <li>{@code Animal.mobInteract} TAIL — consumed, non-cancelled result on
 *       the vanilla path = feed ({@code Animal.mobInteract} only consumes for
 *       {@code isFood} server-side: love-mode or baby growth). Cancelled
 *       results are skipped — a HEAD-cancelling mod (Animal Husbandry)
 *       reports through its own seam, never double-emitted here.</li>
 *   <li>{@code AbstractHorse.fedFood} TAIL — horse-family feeding lives in
 *       {@code fedFood} (invoked from {@code Horse}/{@code AbstractChestedHorse}
 *       {@code mobInteract}, never reaching {@code Animal.mobInteract}).</li>
 *   <li>{@code Cow}/{@code Goat.mobInteract} — the
 *       {@code ItemUtils.createFilledResult} call = bucket milk take-out.</li>
 *   <li>{@code MushroomCow.mobInteract} — 4-arg {@code createFilledResult} =
 *       stew take-out (milked family); {@code shear} call = sheared.</li>
 *   <li>{@code Sheep.mobInteract} — the {@code shear(SoundSource)} call =
 *       sheared (guarded by {@code readyForShearing} upstream).</li>
 *   <li>{@code ItemStack.interactLivingEntity} TAIL — the single funnel for
 *       item-side entity interactions (AH brush/medicine/fertility items,
 *       name tags, saddles, leads) — consumed result on an Animal = cared.
 *       Runs only when {@code mobInteract} didn't consume, so it can never
 *       double-emit with the feed/product seams.</li>
 *   <li>{@code Animal.spawnChildFromBreeding} RETURN — breeding that resolved
 *       (post-call {@code getAge() > 0}: vanilla finalize AND AH's
 *       pregnancy-start both {@code setAge(6000)}) emits {@code bred}. The
 *       {@code addFreshEntityWithPassengers} invoke inside it = instant
 *       vanilla birth → {@code born}.</li>
 *   <li>AH {@code AnimalMixin.onAiStep} — the pregnancy-completion
 *       {@code addFreshEntityWithPassengers} = deferred birth → {@code born}
 *       (NeoForge's {@code BabyEntitySpawnEvent} is bypassed there — AH's
 *       {@code startPregnancy} cancels the spawning method entirely).</li>
 * </ul>
 *
 * <p><b>Attribution:</b> breeders are tracked on the animal via
 * {@link HusbandryTracked} — every completed real-player interaction records
 * {@code lastInteractor}; conception stamps {@code birthCredit} on both
 * parents (persisted in entity NBT). Birth prefers the conception credit,
 * then falls back to a windowed last-interactor, then emits nothing —
 * unattended breeding and automation ({@code isAutomation} FakePlayers like
 * PF's {@code "feeding_trough"}) pay nothing, matching pack policy.
 */
public final class HusbandryProducers {
	private HusbandryProducers() {
	}

	private static final ResourceLocation TAG_FED = LifepathMod.id("fed");
	private static final ResourceLocation TAG_CARED = LifepathMod.id("cared");
	private static final ResourceLocation TAG_BRED = LifepathMod.id("bred");
	private static final ResourceLocation TAG_BORN = LifepathMod.id("born");
	private static final ResourceLocation TAG_MILKED = LifepathMod.id("milked");
	private static final ResourceLocation TAG_SHEARED = LifepathMod.id("sheared");

	/**
	 * Fallback-attribution window: one in-game day. AH managed animals need
	 * roughly daily feeding to stay breedable (care decays per day), so the
	 * last keeper to tend them lands inside this window; a trough-only animal
	 * never gets a record at all. The conception {@code birthCredit} is
	 * authoritative and ignores the window.
	 */
	static final long LAST_INTERACTOR_WINDOW_TICKS = 24000L;

	private static final String NBT_LAST_INTERACTOR = "LifepathLastInteractor";
	private static final String NBT_LAST_INTERACT_TIME = "LifepathLastInteractTime";
	private static final String NBT_BIRTH_CREDIT = "LifepathBirthCredit";

	// ---- vanilla mobInteract family --------------------------------------

	/**
	 * {@code Animal.mobInteract} TAIL. A consumed, non-cancelled result from
	 * the vanilla body is a feed ({@code isFood} gates every server-side
	 * consume there); subclasses that delegate (cow wheat, pig carrot, goat
	 * feed) funnel through this same call. Skipped entirely when a mod
	 * cancelled the interaction (AH's managed-species handler emits through
	 * {@link #onAnimalHusbandryFeed} instead).
	 */
	public static void onAnimalMobInteract(Player player, Animal animal,
			InteractionHand hand, InteractionResult result, boolean cancelled) {
		if (cancelled || !result.consumesAction()) {
			return;
		}
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(animal, sp);
		// Animal.mobInteract only consumes for food server-side, but a
		// discriminating non-food consume (delegating subclass/modded) reads
		// as cared. A fully-eaten stack reads empty → still the food path.
		// Leads are excluded defensively: vanilla leashing consumes inside
		// Entity.interact before mobInteract ever runs (so no vanilla path
		// reaches this gate), but a modded Animal consuming a lead interact
		// inside its own mobInteract would be a free repeatable cared farm.
		ItemStack held = player.getItemInHand(hand);
		boolean food = held.isEmpty() || animal.isFood(held);
		if (!food && held.is(Items.LEAD)) {
			return;
		}
		emit(sp, animal, food ? TAG_FED : TAG_CARED, food ? "fed" : "cared");
	}

	/**
	 * {@code AbstractHorse.fedFood} TAIL — feeding/taming/healing horses,
	 * donkeys, mules, llamas. Consumed result = fed.
	 */
	public static void onHorseFedFood(Player player, Animal horse,
			InteractionResult result) {
		if (!result.consumesAction()) {
			return;
		}
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(horse, sp);
		emit(sp, horse, TAG_FED, "fed");
	}

	/** Bucket-milk take-out (Cow/Goat {@code createFilledResult}). */
	public static void onMilkTaken(Player player, Animal animal) {
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(animal, sp);
		emit(sp, animal, TAG_MILKED, "milked");
	}

	/** Stew take-out from a mooshroom — the milked family, not fed. */
	public static void onStewTaken(Player player, Animal animal) {
		onMilkTaken(player, animal);
	}

	/** Shears take-out (Sheep/MushroomCow {@code shear}). */
	public static void onSheared(Player player, Animal animal) {
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(animal, sp);
		emit(sp, animal, TAG_SHEARED, "sheared");
	}

	/**
	 * {@code ItemStack.interactLivingEntity} TAIL — consumed result on an
	 * Animal from the item-side path (brush, medicine, name tag, saddle,
	 * fertility potion…) = cared. The entity-side {@code mobInteract} already
	 * consumed interactions never reach this funnel, so no feed/milk
	 * double-emission is possible. Shears are excluded: they shear through
	 * this same funnel ({@code ShearsItem.interactLivingEntity} runs inside
	 * {@code ItemStack.interactLivingEntity}), and the dedicated
	 * {@link #onSheared} emit in {@code ShearsItemMixin} already paid —
	 * re-counting as cared would double-pay every NeoForge shear.
	 */
	public static void onItemInteractOnEntity(Player player, LivingEntity target,
			ItemStack held, InteractionResult result, boolean cancelled) {
		if (cancelled || !result.consumesAction() || !(target instanceof Animal animal)
				|| held.is(Items.SHEARS)) {
			return;
		}
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(animal, sp);
		emit(sp, animal, TAG_CARED, "cared");
	}

	// ---- breeding / birth -------------------------------------------------

	/**
	 * {@code Animal.spawnChildFromBreeding} RETURN — fires for every return
	 * point including the early {@code getBreedOffspring == null} exit and
	 * injected cancels. Breeding actually resolved iff both parents came out
	 * on the breed cooldown ({@code setAge(6000)} — vanilla finalize and AH's
	 * startPregnancy both do it), so a null-offspring or gate-rejected call
	 * emits nothing. {@code ci.isCancelled()} == "a mod preempted the vanilla
	 * body" (AH pregnancy-start) → that path's {@code bred} is emitted by
	 * {@link #onAnimalHusbandryConception} instead.
	 */
	public static void onBreedingResolved(Animal animal, Animal partner, boolean cancelled) {
		if (cancelled || animal.level().isClientSide()) {
			return;
		}
		if (animal.getAge() <= 0) {
			return; // no breeding consumed (null offspring / gate bail)
		}
		ServerPlayer breeder = resolveBreeder(animal, partner);
		stampBirthCredit(animal, partner, breeder);
		if (breeder != null) {
			emit(breeder, animal, TAG_BRED, "bred");
		}
	}

	/**
	 * The {@code addFreshEntityWithPassengers} invoke inside
	 * {@code spawnChildFromBreeding} — the instant vanilla birth. AH-managed
	 * species never reach it (their method body is cancelled) → covered by
	 * {@link #onAnimalHusbandryBirth}.
	 */
	public static void onChildSpawned(Animal animal, Animal partner, Entity child) {
		if (animal.level().isClientSide()) {
			return;
		}
		ServerPlayer breeder = resolveBreeder(animal, partner);
		if (breeder != null) {
			emit(breeder, child, TAG_BORN, "born");
		}
	}

	// ---- mod-compat mob seams --------------------------------------------

	/**
	 * Compat feed — foreign feeding paths that never pass through
	 * {@code Animal.mobInteract} (e.g. Naturalist's dragonfly chorus-fruit
	 * interact on a {@code PathfinderMob}). {@link #recordInteraction} runs
	 * only for {@link Animal} targets — {@code HusbandryTracked} lives on
	 * Animal, and a dragonfly/starfish has no attribution record to stamp.
	 */
	public static void onMobFed(Player player, LivingEntity mob) {
		emitCompat(player, mob, TAG_FED, "fed");
	}

	/**
	 * Compat care — attended foreign interactions that bypass every vanilla
	 * seam: whistle commands, hand/net catching (non-bucket funnels),
	 * gear/harness equipping, wagon hitching. Also reached for non-Animal
	 * mobs (bucket-caught fish, wagon-loaded mobs).
	 */
	public static void onMobCared(Player player, LivingEntity mob) {
		emitCompat(player, mob, TAG_CARED, "cared");
	}

	/**
	 * Compat live-product take-out — the milked family for produce removed
	 * from a living mob (Naturalist clam pearl). Fed/sheared remain reserved
	 * for the vanilla-shape acts.
	 */
	public static void onMobProductTaken(Player player, LivingEntity mob) {
		emitCompat(player, mob, TAG_MILKED, "milked");
	}

	/**
	 * Compat tame — {@code TamableAnimal.tame} resolution, called by the
	 * vanilla {@code TamableAnimalMixin} seam. Reads as {@code cared} (the
	 * vocabulary has no {@code tamed} marker); taming consumes no item, so
	 * there is no fed/cared discrimination to make.
	 */
	public static void onMobTamed(Player player, LivingEntity mob) {
		emitCompat(player, mob, TAG_CARED, "cared");
	}

	/**
	 * Compat breeding resolution for foreign breed goals that bypass
	 * {@code Animal.spawnChildFromBreeding} entirely (Naturalist's
	 * {@code EggLayingBreedGoal} sets an egg flag instead of spawning a
	 * child, so neither the RETURN emit nor the birth wrap ever runs). The
	 * caller has already established that the pairing resolved — the
	 * age-cooldown gate from {@link #onBreedingResolved} does not apply
	 * (egg-layers never {@code setAge(6000)}).
	 */
	public static void onExternalBreeding(Animal animal, Animal partner) {
		if (animal.level().isClientSide()) {
			return;
		}
		ServerPlayer breeder = resolveBreeder(animal, partner);
		stampBirthCredit(animal, partner, breeder);
		if (breeder != null) {
			emit(breeder, animal, TAG_BRED, "bred");
		}
	}

	private static void emitCompat(Player player, LivingEntity mob,
			ResourceLocation marker, String action) {
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null || mob.level().isClientSide()) {
			return;
		}
		if (mob instanceof Animal animal) {
			recordInteraction(animal, sp);
		}
		emit(sp, mob, marker, action);
	}

	// ---- Animal Husbandry compat seam ------------------------------------

	/**
	 * RETURN of AH's {@code animalhusbandry$handleInteractions} — the managed
	 * -species feed/domesticate path that HEAD-cancels vanilla
	 * {@code mobInteract}. Consumed return value + food item = fed.
	 * Automation callers (trough FakePlayers) are filtered like everywhere.
	 */
	public static void onAnimalHusbandryFeed(Player player, LivingEntity animal,
			ItemStack held, @Nullable InteractionResult result) {
		// AH's handler only produces consumed results for isFood items (it
		// early-returns otherwise), so consumed ⇒ fed — no isFood re-check:
		// a fully-eaten (count-0) stack reads as AIR and would fail it.
		if (result == null || !result.consumesAction()
				|| !(animal instanceof Animal a)) {
			return;
		}
		ServerPlayer sp = asRealPlayer(player);
		if (sp == null) {
			return;
		}
		recordInteraction(a, sp);
		emit(sp, a, TAG_FED, "fed");
	}

	/**
	 * RETURN of AH's {@code animalhusbandry$startPregnancy} when it cancelled
	 * {@code spawnChildFromBreeding} — conception happened (pregnancy
	 * started) but the vanilla body (and our own RETURN emit there, depending
	 * on mixin application order) may never run. Emits {@code bred} and
	 * stamps the birth credit exactly like {@link #onBreedingResolved}.
	 */
	public static void onAnimalHusbandryConception(Animal animal, Animal partner,
			boolean cancelled) {
		if (!cancelled || animal.level().isClientSide()) {
			return;
		}
		ServerPlayer breeder = resolveBreeder(animal, partner);
		stampBirthCredit(animal, partner, breeder);
		if (breeder != null) {
			emit(breeder, animal, TAG_BRED, "bred");
		}
	}

	/**
	 * The {@code addFreshEntityWithPassengers} invoke inside AH's
	 * {@code AnimalMixin.onAiStep} — the deferred pregnancy birth. The
	 * mixin instance is the mother; attribution prefers the conception
	 * credit stamped at breed time, then the windowed last interactor.
	 */
	public static void onAnimalHusbandryBirth(LivingEntity mother, Entity child) {
		if (!(mother instanceof Animal animal) || mother.level().isClientSide()) {
			return;
		}
		ServerPlayer credited = resolveBirthCredit(animal);
		if (credited != null) {
			emit(credited, child, TAG_BORN, "born");
		}
		// Consumed — a fresh pregnancy will restamp it.
		tracked(animal).lifepath$setBirthCredit(null);
	}

	// ---- tracked-state plumbing -------------------------------------------

	/**
	 * Persist {@link HusbandryTracked} — called by {@code AnimalMixin} at
	 * TAIL of {@code addAdditionalSaveData}. Keeps the birth credit (and the
	 * windowed-interactor fallback) alive across chunk unload + restart,
	 * which AH-length pregnancies otherwise outlive.
	 */
	public static void saveTracked(Animal animal, CompoundTag tag) {
		HusbandryTracked tracked = tracked(animal);
		UUID last = tracked.lifepath$getLastInteractor();
		if (last != null) {
			tag.putUUID(NBT_LAST_INTERACTOR, last);
			tag.putLong(NBT_LAST_INTERACT_TIME, tracked.lifepath$getLastInteractGameTime());
		}
		UUID credit = tracked.lifepath$getBirthCredit();
		if (credit != null) {
			tag.putUUID(NBT_BIRTH_CREDIT, credit);
		}
	}

	/** Read-back half of {@link #saveTracked} — missing keys leave defaults. */
	public static void loadTracked(Animal animal, CompoundTag tag) {
		HusbandryTracked tracked = tracked(animal);
		if (tag.hasUUID(NBT_LAST_INTERACTOR)) {
			tracked.lifepath$setLastInteractor(tag.getUUID(NBT_LAST_INTERACTOR));
			tracked.lifepath$setLastInteractGameTime(tag.getLong(NBT_LAST_INTERACT_TIME));
		}
		if (tag.hasUUID(NBT_BIRTH_CREDIT)) {
			tracked.lifepath$setBirthCredit(tag.getUUID(NBT_BIRTH_CREDIT));
		}
	}

	// ---- internals ---------------------------------------------------------

	private static HusbandryTracked tracked(Animal animal) {
		return (HusbandryTracked) animal;
	}

	/** Records a completed real-player interaction as the animal's last-interactor. */
	private static void recordInteraction(Animal animal, ServerPlayer player) {
		HusbandryTracked tracked = tracked(animal);
		tracked.lifepath$setLastInteractor(player.getUUID());
		tracked.lifepath$setLastInteractGameTime(animal.level().getGameTime());
	}

	/**
	 * The "breeding initiator" tier, resolved from recorded interactions:
	 * the animal's own last feeder first (it is the one whose love-mode
	 * triggered the pairing), then the partner's, each inside the window.
	 * Vanilla's {@code getLoveCause} is unusable here — both vanilla
	 * {@code finalizeSpawnChildFromBreeding} and AH's {@code startPregnancy}
	 * call {@code resetLove()} before our RETURN seam runs — and AH
	 * auto-breeding calls {@code setInLove(null)} anyway.
	 */
	@Nullable
	private static ServerPlayer resolveBreeder(Animal animal, Animal partner) {
		ServerPlayer p = recentInteractor(animal);
		return p != null ? p : recentInteractor(partner);
	}

	@Nullable
	private static ServerPlayer recentInteractor(Animal animal) {
		HusbandryTracked tracked = tracked(animal);
		UUID id = tracked.lifepath$getLastInteractor();
		if (id == null) {
			return null;
		}
		long since = animal.level().getGameTime() - tracked.lifepath$getLastInteractGameTime();
		if (since < 0 || since > LAST_INTERACTOR_WINDOW_TICKS) {
			return null;
		}
		return onlinePlayer(animal, id);
	}

	/** Conception credit stamped on the (pregnant) mother — window-free. */
	@Nullable
	private static ServerPlayer resolveBirthCredit(Animal mother) {
		UUID credit = tracked(mother).lifepath$getBirthCredit();
		if (credit != null) {
			ServerPlayer p = onlinePlayer(mother, credit);
			if (p != null) {
				return p;
			}
		}
		return recentInteractor(mother);
	}

	/** Stamps the conception credit on BOTH parents — under AH the female
	 * (whichever animal that is) delivers, so we can't pick sides here. A
	 * null resolution actively clears stale credit so an unattended
	 * re-conception can't inherit the previous breeder's credit. */
	private static void stampBirthCredit(Animal animal, Animal partner,
			@Nullable ServerPlayer breeder) {
		UUID id = breeder != null ? breeder.getUUID() : null;
		tracked(animal).lifepath$setBirthCredit(id);
		tracked(partner).lifepath$setBirthCredit(id);
	}

	/** UUID → online player; offline/absent means no award target. */
	@Nullable
	private static ServerPlayer onlinePlayer(LivingEntity entity, UUID id) {
		var server = entity.level().getServer();
		return server == null ? null : server.getPlayerList().getPlayer(id);
	}

	/** Server-side, real-player gate — FakePlayers/automation emit nothing. */
	@Nullable
	private static ServerPlayer asRealPlayer(@Nullable Player player) {
		if (!(player instanceof ServerPlayer sp) || isAutomation(sp)) {
			return null;
		}
		return sp;
	}

	private static boolean isAutomation(Player player) {
		return com.dwurdy.lifepath.platform.Platform.isInitialized()
				&& com.dwurdy.lifepath.platform.Platform.get().isAutomation(player);
	}

	private static void emit(ServerPlayer player, Entity subject,
			ResourceLocation marker, String action) {
		ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(subject.getType());
		Set<ResourceLocation> tags = subject.getType().builtInRegistryHolder()
				.tags().map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		tags.add(marker);
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.HUSBANDRY,
				typeId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of("action", action)));
	}
}
