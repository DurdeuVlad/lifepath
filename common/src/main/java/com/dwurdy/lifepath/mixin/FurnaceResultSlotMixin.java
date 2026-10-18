package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace/smoker/blast-furnace output takes. Routed through
 * {@link VanillaGameplayProducers#onFurnaceCookOutput}, which gates to
 * edible outputs so smelting ores doesn't feed crafting/cooking XP while
 * cooked food does — closing the furnace gap in the cooking loop.
 *
 * <p>Alchimiae's {@code BoilerMenu} also lands here — its result slot is a
 * vanilla {@code FurnaceResultSlot} (container index 2 on {@code
 * BoilerBlockEntity}) — but deliberately emits nothing: {@code
 * FurnaceResultSlot.onTake} delegates to {@code Slot.onTake}, where
 * {@code BrewingStandSlotMixin} already pays the take as a dedicated
 * alchemy-stage brewing event. A second emit here would double-pay, so
 * non-food boiler outputs simply fall through {@code onFurnaceCookOutput}'s
 * food gate.
 *
 * <p>Overgeared widening (NeoForge): the casting smelter's output slot is a
 * {@code FurnaceResultSlot} subclass whose {@code onTake} calls
 * {@code super.onTake} ({@code CastingSmelterMenu$2}), and Overgeared's
 * {@code cast_smelting}/{@code cast_blasting}/heated-metal recipes run in
 * vanilla furnaces — every one lands here. Their outputs (tool casts,
 * blades/heads, alloyed ingots) carry no FOOD component either, so the food
 * gate dropped them. Detected by stack content — the {@code overgeared*}
 * namespace (covers the {@code overgearedspartan}/{@code
 * overgeared_epic_knights} addon namespaces too) or the datapack-extensible
 * {@code #lifepath:forged_outputs} tag — and emitted as a craft output, so
 * the Overgeared adapter republishes it as smithing activity.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

	/** Datapack-driven forged-output marker — same tag the Overgeared
	 * adapter republishes on. */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item>
			FORGED_OUTPUTS = net.minecraft.tags.TagKey.create(
					net.minecraft.core.registries.Registries.ITEM,
					com.dwurdy.lifepath.LifepathMod.id("forged_outputs"));

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lifepath$furnaceOutput(Player player, ItemStack stack, CallbackInfo ci) {
		if (!stack.isEmpty()) {
			String namespace = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getKey(stack.getItem()).getNamespace();
			if (namespace.startsWith("overgeared") || stack.is(FORGED_OUTPUTS)) {
				VanillaGameplayProducers.onCraftOutput(player, stack);
				return;
			}
		}
		VanillaGameplayProducers.onFurnaceCookOutput(player, stack);
	}
}
