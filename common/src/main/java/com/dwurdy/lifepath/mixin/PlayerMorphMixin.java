package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.morph.MorphDisguised;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-4 morph sync channel: one extra synced-data slot on {@link Player}
 * carrying the disguise's {@code entity_type} id ("" = not morphed).
 * Entity data is the right transport — it replicates to every tracking
 * client on spawn, on change, on respawn and on chunk reload with zero
 * bespoke packets, and the server side reads the same slot for the real
 * collision box.
 */
@Mixin(Player.class)
public abstract class PlayerMorphMixin implements MorphDisguised {
	@Unique
	private static final EntityDataAccessor<String> lifepath$MORPH_ENTITY_TYPE =
			SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void lifepath$defineMorphData(SynchedEntityData.Builder builder,
			CallbackInfo ci) {
		builder.define(lifepath$MORPH_ENTITY_TYPE, "");
	}

	/** Parse cache for {@link #lifepath$morphTypeId()} — the raw synced
	 *  string the cached id was parsed from. */
	@Unique
	private String lifepath$typeIdParsedFrom = "";
	@Unique
	private net.minecraft.resources.ResourceLocation lifepath$typeIdParsed;

	@Override
	public String lifepath$morphEntityType() {
		// Entity's constructor may reach getDimensions (→ this read) before
		// entityData is assigned — a null there means "not morphed".
		var entityData = ((Entity) (Object) this).getEntityData();
		return entityData == null ? "" : entityData.get(lifepath$MORPH_ENTITY_TYPE);
	}

	@Override
	public net.minecraft.resources.ResourceLocation lifepath$morphTypeId() {
		String raw = lifepath$morphEntityType();
		if (raw.isEmpty()) {
			lifepath$typeIdParsedFrom = "";
			lifepath$typeIdParsed = null;
			return null;
		}
		// Memoize on the raw string — the render path asks per frame.
		if (!raw.equals(lifepath$typeIdParsedFrom)) {
			lifepath$typeIdParsedFrom = raw;
			lifepath$typeIdParsed = net.minecraft.resources.ResourceLocation.tryParse(raw);
		}
		return lifepath$typeIdParsed;
	}

	@Override
	public void lifepath$setMorphEntityType(String entityTypeId) {
		((Entity) (Object) this).getEntityData().set(lifepath$MORPH_ENTITY_TYPE,
				entityTypeId);
	}

	@Override
	public boolean lifepath$isMorphDataKey(EntityDataAccessor<?> key) {
		return key == lifepath$MORPH_ENTITY_TYPE;
	}
}
