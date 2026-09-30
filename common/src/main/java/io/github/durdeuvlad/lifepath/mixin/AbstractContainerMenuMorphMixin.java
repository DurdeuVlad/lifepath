package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-5 "paws, not hands" — the extraction seam {@code mayPickup} cannot
 * reach. Vanilla's {@code quickMoveStack} (shift-click) implementations
 * bypass the pickup check entirely: they read the result slot's stack and
 * {@code moveItemStackTo} it into the inventory. {@code clicked} is the
 * single funnel every container interaction passes through on both sides
 * (server packet handling and client prediction alike), so refusing the
 * click here denies pickup, shift-move, swap, throw, and clone on a
 * station output in one place — with identical behavior on both sides, so
 * no ghost item desync.
 *
 * <p>{@code ClickType.PICKUP_ALL} (double-click gather) gets its own
 * guard: it iterates every slot and pulls any whose item matches the
 * carried stack — the clicked slot being innocent proves nothing. The
 * per-slot {@code mayPickup} it consults is bypassed by overridden
 * implementations (anvil/smithing's {@code ItemCombinerMenu$2}) and by
 * slots the classification can't see without menu context (the loom
 * result), so the click is refused up front whenever the sweep would draw
 * from a result slot ({@link MorphDisguise#pickAllWouldTakeResult} —
 * which replays vanilla's own per-slot predicate, so slots the gather
 * would skip anyway don't force a refusal).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMorphMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoResultClick(int slotId, int button,
			ClickType type, Player player, CallbackInfo ci) {
		var menu = (AbstractContainerMenu) (Object) this;
		if (!MorphDisguise.isDisguised(player)) {
			return;
		}
		if (slotId >= 0 && slotId < menu.slots.size()
				&& MorphDisguise.isResultSlot(menu, menu.slots.get(slotId))) {
			ci.cancel();
			return;
		}
		if (type == ClickType.PICKUP_ALL
				&& MorphDisguise.pickAllWouldTakeResult(menu, player)) {
			ci.cancel();
		}
	}
}
