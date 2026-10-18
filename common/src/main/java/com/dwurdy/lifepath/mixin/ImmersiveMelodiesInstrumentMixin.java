package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.immersivemelodies.ImmersiveMelodiesMusic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Music XP seam for Immersive Melodies: injects into the funnel
 * {@code play(ItemStack, ResourceLocation, long, Entity)} overload that every
 * melody start (use, resume, track refresh) lands in. The target class is a
 * string — it never loads when the mod is absent (compat config is
 * {@code requiredMods}-gated and every injector is {@code defaultRequire: 0}).
 * Handler params are vanilla types only, so the class compiles without the
 * mod as a dependency.
 */
@Pseudo
@Mixin(targets = "immersive_melodies.item.InstrumentItem", remap = false)
public abstract class ImmersiveMelodiesInstrumentMixin {

	@Inject(method = "play(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;JLnet/minecraft/world/entity/Entity;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onPlay(ItemStack stack, ResourceLocation melody, long startTime,
			Entity entity, CallbackInfo ci) {
		try {
			ImmersiveMelodiesMusic.emitPlay(entity, stack, melody);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"immersive_melodies instrument seam failed", t);
		}
	}
}
