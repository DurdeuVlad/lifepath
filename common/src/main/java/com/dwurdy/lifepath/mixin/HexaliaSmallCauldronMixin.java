package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.hexalia.HexaliaTakeSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hexalia small-cauldron scooping → brewing activity (NeoForge-only, gated by
 * the {@code lifepath.compat.hexalia.mixins.json} config's
 * {@code requiredMods}). Verified against hexalia 1.3.7 bytecode: {@code
 * SmallCauldronContents.tryScoopBottle(ServerLevel, double, double, double,
 * Player, InteractionHand, ItemStack)} fills the held rustic bottle with a
 * copy of the private {@code mixtureResult} field, decrements {@code servings}
 * and {@code resetToEmpty()}s at zero — so the result snapshot is taken at
 * HEAD and the activity emitted only on RETURN-true. Attributed to the
 * scooping player.
 */
@Pseudo
@Mixin(targets = "net.astralya.hexalia.gameplay.smallcauldron.SmallCauldronContents",
		remap = false)
public abstract class HexaliaSmallCauldronMixin {

	@Unique
	private ItemStack lifepath$pendingMixture = ItemStack.EMPTY;

	@Inject(method = "tryScoopBottle", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$captureMixture(ServerLevel level, double x, double y,
			double z, Player player, InteractionHand hand, ItemStack held,
			CallbackInfoReturnable<Boolean> cir) {
		lifepath$pendingMixture = HexaliaTakeSupport.readFieldStack(this, "mixtureResult");
	}

	@Inject(method = "tryScoopBottle", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emitScoop(ServerLevel level, double x, double y,
			double z, Player player, InteractionHand hand, ItemStack held,
			CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			HexaliaTakeSupport.emitBrewedTake(player, lifepath$pendingMixture);
		}
		lifepath$pendingMixture = ItemStack.EMPTY;
	}
}
