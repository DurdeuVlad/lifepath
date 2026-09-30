package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-5 "paws, not hands" — the server-authoritative gates. Three refusals,
 * all keyed on the synced disguise flag:
 *
 * <ul>
 *   <li>{@code destroyBlock} — belt-and-suspenders next to the zero dig
 *       speed; creative mode's instant-break calls this directly and would
 *       otherwise skip the progress path entirely.</li>
 *   <li>{@code useItem} — item use (food, bows, buckets, tridents).</li>
 *   <li>{@code useItemOn} — block interaction, which is also what opens
 *       every station UI (crafting table, anvil, furnace, …) and places
 *       blocks. FAIL resyncs the client honestly.</li>
 * </ul>
 *
 * Melee bite is unaffected — {@code attack} never passes through here.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerGameModeMorphMixin {
	@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	protected ServerPlayer player;

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoBreak(BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (MorphDisguise.isDisguised(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoUse(ServerPlayer player, Level level,
			ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (MorphDisguise.isDisguised(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoUseOn(ServerPlayer player, Level level,
			ItemStack stack, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (MorphDisguise.isDisguised(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
