package io.github.durdeuvlad.lifepath.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Emits a fishing activity per caught item. Intercepts the
 * {@code LootTable.generateLoot} call inside {@code FishingBobberEntity.use}
 * — the loot list exists only on the successful-catch branch (casts and
 * entity-hook pulls never generate loot). Vanilla already gates that branch
 * on {@code !world.isClient && playerEntity != null}.
 *
 * <p>Why {@code @ModifyExpressionValue} rather than an {@code @Inject} on the
 * {@code ItemEntity} ctor call: plain {@code @Inject} handlers cannot capture
 * an invocation's arguments — the generated handler descriptor only receives
 * the enclosing method's params. MixinExtras (bundled with Fabric Loader)
 * hands us the generated list directly.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

	@Shadow
	public abstract PlayerEntity getPlayerOwner();

	@ModifyExpressionValue(method = "use(Lnet/minecraft/item/ItemStack;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<ItemStack> lifepath$onCatch(ObjectArrayList<ItemStack> loot) {
		PlayerEntity owner = getPlayerOwner();
		for (ItemStack caught : loot) {
			VanillaGameplayProducers.onFishCaught(owner, caught);
		}
		return loot;
	}
}
