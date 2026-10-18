package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brewing-stand potion takes → brewing activity (M24 Brewer). Vanilla brewing
 * has no result-slot seam of its own — {@code Slot.onTake} covers the three
 * potion output slots (indices 0–2) of the brewing stand's container, so the
 * guard is "slot lives in a BrewingStandBlockEntity + slot is an output slot
 * + stack is a potion". Inserting-then-taking your own potions pays the
 * trickle XP any take does — the same approximation vanilla furnaces make,
 * and diminishing-returns throttles churn.
 *
 * <p>Alchimiae widening (NeoForge): {@code AlembicMenu$AlembicPotionSlot}
 * extends the vanilla {@code BrewingStandMenu$PotionSlot} but sits on an
 * {@code AlembicBlockEntity} container (potion outputs = container indices
 * 2–3), so the brewing-stand guard rejected it. Alembic {@code mix()} uses
 * vanilla {@code PotionBrewing.mix}, so outputs carry POTION_CONTENTS and
 * pass {@link VanillaGameplayProducers#onBrewingOutput}'s potion gate. The
 * container is matched by class name — common has no Alchimiae classes on
 * its compile classpath; when the mod is absent the check never matches.
 */
@Mixin(Slot.class)
public abstract class BrewingStandSlotMixin {
	@Shadow
	@Final
	public net.minecraft.world.Container container;

	@Shadow
	@Final
	public int slot;

	/** {@code com.ssblur.alchimiae.blockentity.AlembicBlockEntity}. */
	private static final String ALCHIMIAE_ALEMBIC_BE =
			"com.ssblur.alchimiae.blockentity.AlembicBlockEntity";

	/** {@code com.ssblur.alchimiae.blockentity.BoilerBlockEntity} — its
	 * {@code BoilerMenu} uses a vanilla {@code FurnaceResultSlot} (container
	 * index 2) for concentrate output, which funnels through this
	 * {@code Slot.onTake} seam. Concentrate carries no POTION_CONTENTS, so it
	 * goes through {@link VanillaGameplayProducers#onAlchemyStage} instead of
	 * the potion-gated brew path. */
	private static final String ALCHIMIAE_BOILER_BE =
			"com.ssblur.alchimiae.blockentity.BoilerBlockEntity";

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lifepath$brewingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		if (container instanceof BrewingStandBlockEntity) {
			if (slot <= 2) {
				VanillaGameplayProducers.onBrewingOutput(player, stack);
			}
			return;
		}
		if ((slot == 2 || slot == 3)
				&& ALCHIMIAE_ALEMBIC_BE.equals(container.getClass().getName())) {
			VanillaGameplayProducers.onBrewingOutput(player, stack);
			return;
		}
		if (slot == 2 && ALCHIMIAE_BOILER_BE.equals(container.getClass().getName())) {
			VanillaGameplayProducers.onAlchemyStage(player, stack);
		}
	}
}
