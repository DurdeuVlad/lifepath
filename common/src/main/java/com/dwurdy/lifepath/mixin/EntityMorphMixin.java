package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.morph.MorphDisguised;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-4 morph dims refresh: when the synced morph flag lands (or clears)
 * the entity's bounding box must be recomputed — vanilla only does that
 * on pose changes. Fires on both sides: clients get it when the flag
 * replicates, the server when {@code MorphService} writes it.
 */
@Mixin(Entity.class)
public abstract class EntityMorphMixin {
	@Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V",
			at = @At("TAIL"))
	private void lifepath$morphDimsRefresh(EntityDataAccessor<?> key,
			CallbackInfo ci) {
		if ((Object) this instanceof MorphDisguised d
				&& d.lifepath$isMorphDataKey(key)) {
			((Entity) (Object) this).refreshDimensions();
		}
	}
}
