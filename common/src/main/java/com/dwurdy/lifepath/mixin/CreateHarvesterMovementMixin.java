package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.create.CreateAttendedAutomation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create mechanical-harvester cuts → attended-automation farming XP
 * (NeoForge-only, gated by the {@code lifepath.compat.create.mixins.json}
 * config's {@code requiredMods}). Verified against Create 6.0.10 bytecode:
 * {@code HarvesterMovementBehaviour.visitNewPosition} harvests through
 * {@code BlockHelper.destroyBlockAs(level, pos, null, tool, chance, drop)}
 * — a null player again, so the act is invisible to every player-gated
 * producer. Injecting immediately before that call site lands after
 * Create's own validity checks ({@code isValidCrop}/{@code isValidOther},
 * air/NON_HARVESTABLE early-returns) while the pre-harvest block state is
 * still readable — the one point that is both committed and observable.
 * A TAIL hook would miss: the block is already replaced by the replanted or
 * air state by then, and plain {@code @At("TAIL")} also fires on the
 * early-returns for invalid crops.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour",
		remap = false)
public abstract class CreateHarvesterMovementMixin {

	@Inject(method = "visitNewPosition(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V",
			at = @At(value = "INVOKE",
					target = "Lcom/simibubi/create/foundation/utility/BlockHelper;destroyBlockAs(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;FLjava/util/function/Consumer;)V",
					remap = false),
			remap = false, require = 0)
	private void lifepath$attendedContraptionHarvest(@Coerce Object context, BlockPos pos,
			CallbackInfo ci) {
		CreateAttendedAutomation.onContraptionHarvest(context, pos);
	}
}
