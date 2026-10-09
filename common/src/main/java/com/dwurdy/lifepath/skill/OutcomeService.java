package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.content.OutcomeRuleDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * M22 — outcome scaling: resolves a skill's rank band to output modifiers
 * (count multiplier, failure chance, quality tier, crafter signature) and
 * applies them to a produced {@link ItemStack}. Server-side only; every
 * producer seam calls {@link #apply} with the stack the player is receiving.
 *
 * <p>Gates (all in {@code config/lifepath/skills.toml}):
 * {@code outcome_scaling_enabled} master switch; {@code outcome_max_failure_chance},
 * {@code outcome_min_count_mult}, {@code outcome_max_count_mult} clamp hostile
 * datapack values. A skill with no matching {@code outcome_rule} file is
 * untouched — per-skill opt-in lives in datapacks.
 */
public final class OutcomeService {
	private OutcomeService() {
	}

	private static final ResourceLocation SKILLS_CONFIG = LifepathMod.id("skills");

	/** Resolved modifiers for one output; {@link #IDENTITY} = no scaling. */
	public record Outcome(
			double countMult,
			double failureChance,
			double failureCountMult,
			@Nullable String qualityTier,
			boolean signItems,
			double anvilCostMult,
			double junkUpgradeChance) {
		public static final Outcome IDENTITY =
				new Outcome(1.0, 0.0, 0.5, null, false, 1.0, 0.0);
	}

	/**
	 * Best matching rule's modifiers for {@code player}'s band on {@code skill}
	 * context implied by the event. Among matching rules the most specific wins
	 * (required_tags count, then activity-pin); ties break on id for stability.
	 */
	public static Outcome resolveForPlayer(ServerPlayer player, ResourceLocation activityId,
			ResourceLocation sourceId, Set<ResourceLocation> tags) {
		return resolve(
				com.dwurdy.lifepath.character.CharacterManager.getCharacter(player),
				activityId, sourceId, tags);
	}

	public static Outcome resolve(PlayerCharacterData data, ResourceLocation activityId,
			ResourceLocation sourceId, Set<ResourceLocation> tags) {
		if (!cfgEnabled()) {
			return Outcome.IDENTITY;
		}
		OutcomeRuleDefinition best = LifepathContent.outcomeRules().all().values().stream()
				.filter(r -> r.matches(activityId, sourceId, tags))
				.max(Comparator.comparingInt(OutcomeRuleDefinition::specificity)
						.thenComparing(r -> r.id().toString()))
				.orElse(null);
		if (best == null) {
			return Outcome.IDENTITY;
		}
		int level = SkillXpService.getLevel(data, best.skill());
		OutcomeRuleDefinition.BandModifiers mods = best.forBand(RankBands.bandFor(level));
		if (mods.equals(OutcomeRuleDefinition.BandModifiers.IDENTITY)) {
			return Outcome.IDENTITY;
		}
		return new Outcome(
				clamp(mods.outputCountMult(), "outcome_min_count_mult", 0.25,
						"outcome_max_count_mult", 2.0),
				Math.min(mods.failureChance(),
						cfgDouble("outcome_max_failure_chance", 0.5)),
				mods.failureCountMult(),
				mods.qualityTier().orElse(null),
				mods.signItems(),
				mods.anvilCostMult(),
				mods.junkUpgradeChance());
	}

	/**
	 * Drop-path variant of {@link #apply} for spawned loot (block drops,
	 * entity drops). {@code output_count_mult} applies <em>probabilistically
	 * per stack</em>: 0.6 on a single-item drop means "40% chance it never
	 * lands"; 1.4 means "40% chance of a bonus copy". Stacks can reach zero
	 * and are removed — unlike take-seams, a lost drop is the intended low
	 * skill penalty. Failure/botch logic does not apply here.
	 */
	public static void applyDrops(ServerPlayer player, ResourceLocation activityId,
			ResourceLocation sourceId, Set<ResourceLocation> tags,
			List<ItemStack> drops) {
		if (drops.isEmpty()) {
			return;
		}
		Outcome outcome = resolve(
				com.dwurdy.lifepath.character.CharacterManager.getCharacter(player),
				activityId, sourceId, tags);
		if (outcome == Outcome.IDENTITY) {
			return;
		}
		drops.removeIf(stack -> !applyDrop(player, outcome, stack));
	}

	/**
	 * Single-stack variant of {@link #applyDrops} — probabilistic count roll,
	 * quality/signature stamp, empty-check. Returns false when the drop should
	 * be discarded (used by the kill-drop consumer wrapper, where removal from
	 * a list isn't possible).
	 */
	public static boolean applyDrop(ServerPlayer player, Outcome outcome, ItemStack stack) {
		return applyDrop(player.getGameProfile().getName(), player.getRandom(),
				outcome, stack);
	}

	/** Testable core — random + signer name supplied instead of a player. */
	static boolean applyDrop(String signerName, RandomSource random,
			Outcome outcome, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.getMaxStackSize() > 1) {
			double raw = stack.getCount() * outcome.countMult();
			int floor = (int) Math.floor(raw);
			int newCount = floor
					+ (random.nextDouble() < raw - floor ? 1 : 0);
			stack.setCount(Math.min(newCount, stack.getMaxStackSize()));
		} else {
			// unstackable drops: countMult degrades to a drop chance.
			if (random.nextDouble() >= outcome.countMult()) {
				stack.setCount(0);
			}
		}
		String quality = outcome.qualityTier();
		if ("standard".equals(quality)) {
			quality = null;
		}
		if (!stack.isEmpty() && (quality != null || outcome.signItems())) {
			stamp(signerName, stack, quality, outcome.signItems());
		}
		return !stack.isEmpty();
	}

	/** What {@link #apply} did to the stack — seams post-process off this. */
	public record AppliedOutcome(
			Outcome outcome,
			boolean failed,
			@Nullable String effectiveQualityTier) {
		public static final AppliedOutcome NONE =
				new AppliedOutcome(Outcome.IDENTITY, false, null);
	}

	/**
	 * Apply the outcome to {@code stack} in place — this is the unified seam
	 * mixins call (result-slot takes, forge completions, drops). Mutates the
	 * passed stack: scales count, stamps quality/signature, degrades on
	 * failure and notifies the player. Returns what was applied so seams can
	 * post-process (e.g. Overgeared maps {@code effectiveQualityTier} onto its
	 * own ForgingQuality component).
	 */
	public static AppliedOutcome apply(ServerPlayer player, ResourceLocation activityId,
			ResourceLocation sourceId, Set<ResourceLocation> tags, ItemStack stack) {
		if (stack.isEmpty()) {
			return AppliedOutcome.NONE;
		}
		Outcome outcome = resolve(
				com.dwurdy.lifepath.character.CharacterManager.getCharacter(player),
				activityId, sourceId, tags);
		if (outcome == Outcome.IDENTITY) {
			return AppliedOutcome.NONE;
		}
		boolean failed = outcome.failureChance() > 0
				&& player.getRandom().nextDouble() < outcome.failureChance();
		int newCount = failed
				? Math.max(1, (int) Math.floor(stack.getCount() * outcome.countMult() * outcome.failureCountMult()))
				: Math.max(1, (int) Math.round(stack.getCount() * outcome.countMult()));
		if (stack.getMaxStackSize() > 1) {
			stack.setCount(Math.min(newCount, stack.getMaxStackSize()));
		}
		String quality = failed ? "crude" : outcome.qualityTier();
		boolean sign = outcome.signItems() && !failed;
		// "standard" is the explicit vanilla baseline — no stamp noise.
		if ("standard".equals(quality)) {
			quality = null;
		}
		if (quality != null || sign) {
			stamp(player, stack, quality, sign);
		}
		if (failed) {
			player.displayClientMessage(
					Component.translatable("lifepath.outcome.failed"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.4f, 1.2f);
		}
		return new AppliedOutcome(outcome, failed, quality);
	}

	/**
	 * Stamps quality + signature: machine-readable {@code lifepath:quality} in
	 * custom_data plus lore lines every client can read in its own locale.
	 */
	private static void stamp(ServerPlayer player, ItemStack stack,
			@Nullable String quality, boolean sign) {
		stamp(player.getGameProfile().getName(), stack, quality, sign);
	}

	private static void stamp(String signerName, ItemStack stack,
			@Nullable String quality, boolean sign) {
		if (quality != null) {
			CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
					.copyTag();
			tag.putString("lifepath:quality", quality);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		if (sign) {
			CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
					.copyTag();
			tag.putString("lifepath:crafter", signerName);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		List<Component> extra = new ArrayList<>(2);
		if (quality != null) {
			extra.add(Component.translatable("lifepath.quality." + quality));
		}
		if (sign) {
			extra.add(Component.translatable("lifepath.outcome.crafted_by", signerName));
		}
		if (!extra.isEmpty()) {
			stack.update(DataComponents.LORE, ItemLore.EMPTY, lore -> {
				List<Component> lines = new ArrayList<>(lore.lines());
				lines.addAll(extra);
				return new ItemLore(lines);
			});
		}
		// Give the cosmetic tier real teeth via the dep's own quality
		// component (Overgeared forging quality, KCookery food quality).
		com.dwurdy.lifepath.compat.ExternalQualityBridge.apply(stack, quality);
	}

	private static double clamp(double value, String minKey, double minDefault,
			String maxKey, double maxDefault) {
		return Math.min(Math.max(value, cfgDouble(minKey, minDefault)),
				cfgDouble(maxKey, maxDefault));
	}

	/**
	 * Unloaded config (headless tests, pre-init calls) resolves to defaults —
	 * same convention as {@link RankBands}.
	 */
	private static boolean cfgEnabled() {
		return !LifepathConfig.isLoaded(SKILLS_CONFIG)
				|| LifepathConfig.getBoolean(SKILLS_CONFIG, "outcome_scaling_enabled");
	}

	private static double cfgDouble(String key, double fallback) {
		return LifepathConfig.isLoaded(SKILLS_CONFIG)
				? LifepathConfig.getDouble(SKILLS_CONFIG, key) : fallback;
	}
}
