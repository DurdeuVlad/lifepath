package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kaleidoscope Tavern barrel-tap pours → alcohol XP (NeoForge-only, gated by
 * the {@code lifepath.compat.kaleidoscopetavern.mixins.json} config's
 * {@code requiredMods}). Verified against kaleidoscopetavern 1.2.0 bytecode:
 * {@code BarrelBlockEntity.doTapExtract(Level, BlockPos)} is player-less — it
 * decrements the {@code output} handler and drops/places the filled carrier
 * below the tap. Attribution therefore comes from the behavior side:
 * {@code onStartExtract} carries the player who opened the tap (pack policy
 * D4 — the attending operator), recorded keyed by tap position; the later
 * {@code onEndExtract} → {@code doTapExtract} pour (scheduled ~30 ticks out)
 * emits the output-handler delta to that starter. Redstone-triggered opens
 * pass a {@code null} player, and an unresolvable starter emits nothing —
 * unattended pours pay nothing.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopetavern.game.tap.impl.BarrelTapBehavior",
		remap = false)
public abstract class KaleidoscopeTavernBarrelTapMixin {

	/** tap position → the player who opened that tap; consumed on the pour. */
	@Unique
	private Map<GlobalPos, UUID> lifepath$starters = new HashMap<>();

	@Inject(method = "onStartExtract", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$recordStarter(Level level, Player player, BlockPos tapPos,
			BlockState tapState, BlockState sourceState, BlockState belowState,
			CallbackInfoReturnable<?> cir) {
		if (player == null || level.isClientSide()) {
			return;
		}
		if (lifepath$starters.size() > 64) {
			lifepath$starters.clear();
		}
		lifepath$starters.put(GlobalPos.of(level.dimension(), tapPos.immutable()),
				player.getUUID());
	}

	@WrapOperation(method = "onEndExtract", at = @At(value = "INVOKE",
			target = "Lcom/github/ysbbbbbb/kaleidoscopetavern/blockentity/brew/BarrelBlockEntity;"
					+ "doTapExtract(Lnet/minecraft/world/level/Level;"
					+ "Lnet/minecraft/core/BlockPos;)V"),
			remap = false, require = 0)
	private void lifepath$emitOnPour(@Coerce Object barrel, Level level, BlockPos tapPos,
			Operation<Void> original) {
		List<ItemStack> before = KaleidoscopeTakeSupport.readStacks(barrel, "getOutput");
		original.call(barrel, level, tapPos);
		UUID starterId = lifepath$starters.remove(GlobalPos.of(level.dimension(), tapPos));
		Player starter = starterId == null ? null : level.getPlayerByUUID(starterId);
		if (starter != null) {
			KaleidoscopeTakeSupport.emitDelta(starter, before,
					KaleidoscopeTakeSupport.readStacks(barrel, "getOutput"));
		}
	}
}
