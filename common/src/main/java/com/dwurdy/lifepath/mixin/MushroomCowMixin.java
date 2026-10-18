package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mooshroom seams (husbandry): the bowl→stew swap (4-arg
 * {@code createFilledResult} — the milked family), plus shearing on loaders
 * where {@code mobInteract} still owns it (vanilla/Fabric — the
 * {@code shear} emit fires BEFORE the call because {@code shear()}
 * discards the entity into a plain cow). NeoForge removes that call:
 * its {@code IShearable} pipeline routes shears through
 * {@code ShearsItem.interactLivingEntity} instead, which
 * {@link ShearsItemMixin} pays — {@code require=0} keeps the wrap a
 * silent no-op there so exactly one emit happens per loader.
 * Plain bucket milking delegates to {@code Cow.mobInteract} and emits
 * through {@link CowMixin} instead.
 */
@Mixin(MushroomCow.class)
public abstract class MushroomCowMixin {

	@WrapOperation(method = "mobInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack lifepath$stewed(ItemStack held, Player player, ItemStack result,
			boolean flag, Operation<ItemStack> original,
			Player interactingPlayer, InteractionHand hand) {
		ItemStack filled = original.call(held, player, result, flag);
		HusbandryProducers.onStewTaken(interactingPlayer, (Animal) (Object) this);
		return filled;
	}

	@WrapOperation(method = "mobInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/animal/MushroomCow;shear(Lnet/minecraft/sounds/SoundSource;)V"),
			require = 0)
	private void lifepath$sheared(MushroomCow self, SoundSource source,
			Operation<Void> original, Player player, InteractionHand hand) {
		// shear() discards this entity — the emit must precede the call.
		HusbandryProducers.onSheared(player, (Animal) (Object) this);
		original.call(self, source);
	}

}
