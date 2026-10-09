package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.overgeared.OvergearedCompat;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overgeared forge completion → Smithing XP (NeoForge-only, gated by the
 * {@code lifepath.compat.overgeared.mixins.json} config's
 * {@code requiredMods}). Verified
 * against Overgeared's bytecode: {@code craftItem()} drops the forged result
 * straight into the world — it never passes a result slot, so no
 * {@code ItemCraftedEvent} exists to republish. Every concrete anvil tier
 * delegates to {@code AbstractSmithingAnvilBlockEntity.craftItem()}/
 * {@code craftItemWithBlueprint()}, so injecting here catches all of them.
 *
 * <p>The target class is never referenced — the mixin applies via a string
 * target and reads {@code getOwnerUUID()}/{@code getCurrentRecipe()}/
 * {@code getResultItem(provider)} reflectively, so an Overgeared update that
 * renames members degrades to a debug log, not a crash.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity", remap = false)
public abstract class OvergearedAnvilMixin {

	/**
	 * Steel-tier anvils run {@code super.craftItem()} then
	 * {@code super.craftItemWithBlueprint()} in one completion — award at most
	 * once per game tick so the second invocation can't double-count.
	 */
	@Unique
	private long lifepath$lastForgeAwardTick = Long.MIN_VALUE;

	@Inject(method = {"craftItem", "craftItemWithBlueprint"},
			at = @At("HEAD"), remap = false, require = 0, cancellable = true)
	private void lifepath$awardForgeXp(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (level == null || level.isClientSide()) {
			return;
		}
		long tick = level.getGameTime();
		if (tick == lifepath$lastForgeAwardTick) {
			return;
		}
		try {
			Class<?> beClass = self.getClass();
			Object owner = beClass.getMethod("getOwnerUUID").invoke(self);
			if (!(owner instanceof UUID ownerId)) {
				return;
			}
			MinecraftServer server = level.getServer();
			if (server == null) {
				return;
			}
			// Offline forgers earn nothing — XP needs an online recipient.
			ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
			if (player == null) {
				return;
			}
			Object recipe = ((Optional<?>) beClass.getMethod("getCurrentRecipe")
					.invoke(self)).orElse(null);
			if (recipe == null) {
				return;
			}
			Method getResultItem = recipe.getClass()
					.getMethod("getResultItem", HolderLookup.Provider.class);
			Object result = getResultItem.invoke(recipe, level.registryAccess());
			if (!(result instanceof ItemStack stack) || stack.isEmpty()) {
				return;
			}
			// Material gate (anti one-man-army): tier-tagged outputs need the
			// skill level — OR a blueprint in the anvil's blueprint slot, the
			// tradeable bypass. Denial cancels the craft BEFORE XP/scaling.
			if (lifepath$gateDenied(self, player, stack, tick)) {
				ci.cancel();
				return;
			}
			lifepath$lastForgeAwardTick = tick;
			lifepath$pendingOutcomePlayer = player;
			lifepath$pendingOutcomeItem = stack.getItem();
			VanillaGameplayProducers.onForgeOutput(player, stack,
					OvergearedCompat.FORGE_WORKSTATION);
		} catch (ReflectiveOperationException e) {
			LifepathMod.LOGGER.debug("Overgeared anvil XP hook degraded: {}", e.toString());
		}
	}

	/**
	 * M22 outcome seam: {@code craftItem} drops the forged stack straight into
	 * the world — there is no result slot to mutate. At RETURN, find the
	 * just-spawned ItemEntity (age 0, same item as the recipe result) at the
	 * anvil and scale it in place. Misses degrade silently — the XP award in
	 * HEAD already fired.
	 */
	@Inject(method = {"craftItem", "craftItemWithBlueprint"},
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$scaleForgeOutput(CallbackInfo ci) {
		ServerPlayer player = lifepath$pendingOutcomePlayer;
		net.minecraft.world.item.Item expected = lifepath$pendingOutcomeItem;
		lifepath$pendingOutcomePlayer = null;
		lifepath$pendingOutcomeItem = null;
		if (player == null || expected == null) {
			return;
		}
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (level == null || level.isClientSide()) {
			return;
		}
		// Newer Overgeared writes the result into OUTPUT_SLOT instead of
		// dropping it — check the slot first, fall back to the world drop.
		ItemStack produced = lifepath$outputStack(self, expected);
		if (produced == null) {
			var box = new net.minecraft.world.phys.AABB(self.getBlockPos()).inflate(1.5);
			for (var entity : level.getEntitiesOfClass(
					net.minecraft.world.entity.item.ItemEntity.class, box)) {
				ItemStack stack = entity.getItem();
				if (stack.is(expected) && entity.getAge() <= 1) {
					produced = stack;
					break;
				}
			}
		}
		if (produced == null) {
			return;
		}
		java.util.Set<net.minecraft.resources.ResourceLocation> tags =
				new java.util.HashSet<>();
		produced.getTags().map(net.minecraft.tags.TagKey::location)
				.forEach(tags::add);
		tags.add(OvergearedCompat.FORGE_WORKSTATION);
		tags.add(LifepathMod.id("smithing_workstations"));
		var applied = com.dwurdy.lifepath.skill.OutcomeService.apply(player,
				com.dwurdy.lifepath.event.ActivityTypes.SMITHING,
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(expected),
				tags, produced);
		lifepath$biasForgingQuality(produced, applied.effectiveQualityTier());
	}

	/** Fresh result in the anvil's output slot — only accepted when the stack
	 * was placed there this craft (matches {@code expected}). */
	@Unique
	private static ItemStack lifepath$outputStack(BlockEntity self,
			net.minecraft.world.item.Item expected) {
		if (!(self instanceof net.minecraft.world.Container c)) {
			return null;
		}
		Class<?> cls = self.getClass();
		while (cls != null) {
			try {
				var f = cls.getDeclaredField("OUTPUT_SLOT");
				f.setAccessible(true);
				ItemStack s = c.getItem(f.getInt(null));
				return s.is(expected) ? s : null;
			} catch (ReflectiveOperationException e) {
				cls = cls.getSuperclass();
			}
		}
		return null;
	}

	/**
	 * Maps our {@code quality_tier} onto Overgeared's native
	 * {@code ForgingQuality} component (durability/speed/price multipliers —
	 * the real "low quality starters, recognized experts" lever). Invoked
	 * reflectively so an Overgeared refactor degrades to a debug log. A
	 * {@code null}/{@code "standard"} tier leaves the minigame-rolled quality
	 * untouched.
	 */
	@Unique
	private static void lifepath$biasForgingQuality(ItemStack stack,
			@org.jetbrains.annotations.Nullable String tier) {
		if (tier == null || "standard".equals(tier)) {
			return;
		}
		// Same tier table as ExternalQualityBridge: the anvil's native quality
		// must agree with what the bridge writes elsewhere.
		String qualityName = switch (tier) {
			case "crude" -> "POOR";
			case "poor" -> "WELL";
			case "fine" -> "PERFECT";
			case "masterwork" -> "MASTER";
			default -> null;
		};
		if (qualityName == null) {
			return;
		}
		try {
			Class<?> qualityEnum = Class.forName("net.stirdrem.overgeared.ForgingQuality");
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object quality = Enum.valueOf((Class) qualityEnum, qualityName);
			Class.forName("net.stirdrem.overgeared.util.ForgingQualityHelper")
					.getMethod("applyQuality", ItemStack.class, qualityEnum)
					.invoke(null, stack, quality);
		} catch (ReflectiveOperationException | ClassCastException e) {
			LifepathMod.LOGGER.debug("Overgeared quality bias degraded: {}", e.toString());
		}
	}

	/**
	 * Forge-time material gate. The blueprint slot bypasses the level check —
	 * a blueprint bought from a smith IS the licensed path. Messages are
	 * throttled because {@code craftItem} retries every tick while progress
	 * stays finished.
	 */
	@Unique
	private static boolean lifepath$gateDenied(BlockEntity self,
			ServerPlayer player, ItemStack stack, long tick) {
		var denial = com.dwurdy.lifepath.skill.MaterialGates.denialFor(player,
				stack, lifepath$blueprintPresent(self));
		if (denial == null) {
			return false;
		}
		if (tick - lifepath$lastGateDenyTick >= 60) {
			lifepath$lastGateDenyTick = tick;
			com.dwurdy.lifepath.skill.MaterialGates.notifyDenied(player, denial);
		}
		return true;
	}

	/** Blueprint slot non-empty → the craft is licensed, gate bypassed. */
	@Unique
	private static boolean lifepath$blueprintPresent(BlockEntity self) {
		if (!(self instanceof net.minecraft.world.Container c)) {
			return false;
		}
		Class<?> cls = self.getClass();
		while (cls != null) {
			try {
				java.lang.reflect.Field slot = cls.getDeclaredField("BLUEPRINT_SLOT");
				slot.setAccessible(true);
				return !c.getItem(slot.getInt(null)).isEmpty();
			} catch (NoSuchFieldException e) {
				cls = cls.getSuperclass();
			} catch (ReflectiveOperationException e) {
				return false;
			}
		}
		return false;
	}

	@Unique
	private ServerPlayer lifepath$pendingOutcomePlayer;

	@Unique
	private net.minecraft.world.item.Item lifepath$pendingOutcomeItem;

	@Unique
	private static long lifepath$lastGateDenyTick = Long.MIN_VALUE;
}
