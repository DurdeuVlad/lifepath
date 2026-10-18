package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taming seam on {@link TamableAnimal#tame(Player)} (LP-INT-2). The method is
 * the single funnel every tamable mob resolves through — vanilla wolves,
 * cats, parrots and modded pets alike (Naturalist's bears/ducks/ostriches/
 * lizards call {@code tame(player)} inside their own {@code mobInteract}
 * overrides, below the point {@code Animal.mobInteract}'s TAIL can observe).
 *
 * <p>Taming is emitted as {@code lifepath:cared} — the vocabulary has no
 * {@code tamed} marker, and no item is consumed so there is no fed/cared
 * discrimination to make. The signature takes a {@link Player}, so every
 * call is player-initiated; FakePlayer/automation filtering happens in
 * {@link HusbandryProducers#onMobTamed} like everywhere else.
 *
 * <p>No double-emit with the vanilla seams: a successful tame branch never
 * delegates to {@code Animal.mobInteract} afterwards, so the feed/care TAIL
 * there cannot also fire for the same click.
 */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {

	@Inject(method = "tame(Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("RETURN"))
	private void lifepath$tamed(Player player, CallbackInfo ci) {
		HusbandryProducers.onMobTamed(player, (Animal) (Object) this);
	}
}
