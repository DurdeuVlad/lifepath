package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M29 skill gates — dispenser-equip refusal. {@code dispenseArmor} is the
 * single funnel every vanilla equip-on-dispense path runs through: armour,
 * elytra and shields via {@code ArmorItem.DISPENSE_ITEM_BEHAVIOR}, heads
 * and carved pumpkins via the {@code DispenseItemBehavior} bootstrap
 * entries — all end at the same "first equip-willing {@link LivingEntity}
 * in the face block takes one item" write, which never consults
 * {@code mayPlace} and so slips past {@link ArmorSlotGateMixin}. Returning
 * {@code true} without touching the stack makes the caller's
 * {@code execute} hand it back to the dispenser inventory unchanged — the
 * denied item stays inside instead of ejecting as a drop. Only
 * {@link Player} targets are gated; mobs and armour stands standing in
 * front of a dispenser keep vanilla behaviour. {@code BlockSource} only
 * ever carries a {@code ServerLevel}, so a player target is a real
 * {@link ServerPlayer} and the denial toast always lands.
 */
@Mixin(ArmorItem.class)
public abstract class DispenserEquipGateMixin {
	/**
	 * Re-runs vanilla's own "who's in front" query — a one-block AABB scan
	 * that costs a second lookup per trigger at most — so the gate judges
	 * the same first-match target the equip write would land on.
	 */
	@Inject(method = "dispenseArmor", at = @At("HEAD"), cancellable = true)
	private static void lifepath$gateDispenseEquip(BlockSource source,
			ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		BlockPos front = source.pos()
				.relative(source.state().getValue(DispenserBlock.FACING));
		List<LivingEntity> targets = source.level().getEntitiesOfClass(
				LivingEntity.class, new AABB(front),
				EntitySelector.NO_SPECTATORS
						.and(new EntitySelector.MobCanWearArmorEntitySelector(stack)));
		if (targets.isEmpty() || !(targets.get(0) instanceof Player player)) {
			return;
		}
		SkillGates.Denial denial = SkillGates.denialForEquip(player, stack);
		if (denial != null) {
			if (player instanceof ServerPlayer serverPlayer) {
				SkillGates.notifyDenied(serverPlayer, denial);
			}
			cir.setReturnValue(true);
		}
	}
}
