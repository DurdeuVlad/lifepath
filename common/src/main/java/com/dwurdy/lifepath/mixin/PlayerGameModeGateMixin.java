package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
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
 * M29 skill gates — the server-authoritative item-use refusals.
 * {@code useItem} covers held-item activation (food, bows, wands);
 * {@code useItemOn} covers block interaction, which is also what places
 * block items (construction wands, Create components, premium blocks) and
 * opens station UIs. FAIL resyncs the client honestly. Melee is
 * unaffected — {@code attack} never passes through here.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerGameModeGateMixin {
	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void lifepath$gateUseItem(ServerPlayer player, Level level,
			ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		SkillGates.Denial denial = SkillGates.denialForUse(player, stack);
		if (denial != null) {
			SkillGates.notifyDenied(player, denial);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void lifepath$gateUseItemOn(ServerPlayer player, Level level,
			ItemStack stack, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		SkillGates.Denial denial = SkillGates.denialForUse(player, stack);
		if (denial != null) {
			SkillGates.notifyDenied(player, denial);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
