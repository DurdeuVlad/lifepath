package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sheep shearing seam (husbandry): on loaders where {@code mobInteract}
 * still handles shears in-place (vanilla/Fabric), the
 * {@code shear(SoundSource)} invoke is the act — {@code readyForShearing}
 * already passed upstream. {@code require=0} because NeoForge removes that
 * call: its {@code IShearable} pipeline routes shears through
 * {@code ShearsItem.interactLivingEntity} instead, which
 * {@link ShearsItemMixin} pays — exactly one emit on either loader.
 * Dispenser shearing never reaches {@code mobInteract} either way.
 */
@Mixin(Sheep.class)
public abstract class SheepMixin {

	@WrapOperation(method = "mobInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/animal/Sheep;shear(Lnet/minecraft/sounds/SoundSource;)V"),
			require = 0)
	private void lifepath$sheared(Sheep self, SoundSource source,
			Operation<Void> original, Player player, InteractionHand hand) {
		original.call(self, source);
		HusbandryProducers.onSheared(player, (Animal) (Object) this);
	}
}
