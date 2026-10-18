package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M29 skill gates — equipment-slot placement refusal. {@code mayPlace} is
 * consulted by every armour-slot write path a menu drives (click-place,
 * shift-click auto-equip, hotbar swap), so denying here keeps gated gear
 * out of the slot. Runs both sides like the slot itself — the client
 * refuses the ghost-place the server would reject anyway; only a real
 * {@link ServerPlayer} gets the denial toast. Right-click-equip bypasses
 * this seam (it never asks the slot) — armour tags belong under
 * {@code use} as well when that path should refuse too.
 */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class ArmorSlotGateMixin {
	@Shadow
	@Final
	private LivingEntity owner;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void lifepath$gateEquip(ItemStack stack,
			CallbackInfoReturnable<Boolean> cir) {
		if (!(owner instanceof Player player)) {
			return;
		}
		SkillGates.Denial denial = SkillGates.denialForEquip(player, stack);
		if (denial != null) {
			if (player instanceof ServerPlayer serverPlayer) {
				SkillGates.notifyDenied(serverPlayer, denial);
			}
			cir.setReturnValue(false);
		}
	}
}
