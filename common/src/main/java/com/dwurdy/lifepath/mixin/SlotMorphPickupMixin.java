package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.morph.MorphDisguise;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-5 "paws, not hands" — the honest gates for station/crafting output.
 * {@code mayPickup} is the check vanilla consults <b>before</b> an item
 * leaves a slot ({@code AbstractContainerMenu.doClick} gates every
 * extraction branch on it). This is deliberately NOT {@code onTake}: that
 * hook runs after the stack is already carried, and cancelling it would
 * still hand over the item while skipping the post-take bookkeeping —
 * ingredient consumption (crafting), cost payment + offer use-counting
 * (trading), XP/inputs (anvil/smithing) — i.e. a silent dupe. Denying
 * pickup instead makes the slot untakeable without touching the take's
 * side effects.
 *
 * <p>{@code tryRemove} is the take itself: {@code safeTake} delegates to
 * it and {@code doClick}'s pickup paths call it directly. Gating it too
 * covers take paths that consult no pickup check — and result slots whose
 * {@code mayPickup} override ({@code ItemCombinerMenu$2}, anvil/smithing)
 * never reaches the inject above.
 *
 * <p>Shift-click ({@code quickMoveStack}) does not go through
 * {@code mayPickup} in the stock menus, and {@code ClickType.PICKUP_ALL}
 * iterates slots a {@code Slot} mixin can't classify without the menu —
 * both are denied in {@code AbstractContainerMenuMorphMixin}, the only
 * layer that sees the clicked slot AND the menu together.
 */
@Mixin(Slot.class)
public abstract class SlotMorphPickupMixin {
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoResultPickup(Player player,
			CallbackInfoReturnable<Boolean> cir) {
		if (MorphDisguise.isDisguised(player)
				&& MorphDisguise.isResultSlot((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryRemove", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoResultRemove(int count, int decrement,
			Player player, CallbackInfoReturnable<Optional<ItemStack>> cir) {
		if (MorphDisguise.isDisguised(player)
				&& MorphDisguise.isResultSlot((Slot) (Object) this)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
