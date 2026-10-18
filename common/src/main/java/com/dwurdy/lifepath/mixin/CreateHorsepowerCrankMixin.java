package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create Horsepower harness seam (LP-INT-2), gated by
 * {@code lifepath.compat.createhorsepower.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code HorseCrankBlock.useWithoutItem} verifies a nearby player-leashed
 * mob against config, then {@code LeadItem.bindPlayerMobs(player, level,
 * pos)} knots it onto the crank — the single attended player act in an
 * otherwise fully automated machine (the worker mob then walks the crank
 * via {@code HorseCrankTileEntity.tick} with no further player input, and
 * earns nothing, matching the pack's unattended-automation policy).
 *
 * <p>A consumed bind = harnessed animal(s) → {@code lifepath:cared} on each
 * mob the player had leashed near the crank — the same set
 * {@code bindPlayerMobs} just bound.
 */
@Pseudo
@Mixin(targets = "net.steampn.createhorsepower.blocks.horse_crank.HorseCrankBlock",
		remap = false)
public abstract class CreateHorsepowerCrankMixin {

	@WrapOperation(method = "useWithoutItem",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/LeadItem;bindPlayerMobs(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;"),
			require = 0)
	private InteractionResult lifepath$chpHarnessed(Player player, Level level,
			BlockPos pos, Operation<InteractionResult> original, BlockState state,
			Level enclosingLevel, BlockPos enclosingPos, Player enclosingPlayer,
			BlockHitResult hit) {
		// Snapshot BEFORE the bind: bindPlayerMobs re-knots every leashable
		// it takes to a LeashFenceKnotEntity, so post-call
		// getLeashHolder() == player can never match (the holder is the
		// knot). Mirror leashableInArea's exact +/-7 box and
		// getLeashHolder() == player predicate so the paid set is the bound
		// set — restricted to Mob since the harnessed subjects are animals
		// (vanilla would also re-knot leashed boats; those are not care).
		List<Mob> bound = level.getEntitiesOfClass(Mob.class,
				new AABB(pos.getX() - 7, pos.getY() - 7, pos.getZ() - 7,
						pos.getX() + 7, pos.getY() + 7, pos.getZ() + 7),
				m -> m.getLeashHolder() == player);
		InteractionResult result = original.call(player, level, pos);
		if (result.consumesAction()) {
			for (Mob mob : bound) {
				HusbandryProducers.onMobCared(player, mob);
			}
		}
		return result;
	}
}
