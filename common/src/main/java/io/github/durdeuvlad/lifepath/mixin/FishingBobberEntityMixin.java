package io.github.durdeuvlad.lifepath.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Emits a fishing activity per caught item. Intercepts the
 * {@code LootTable.getRandomItems} call inside {@code FishingHook.retrieve}
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
@Mixin(FishingHook.class)
public abstract class FishingBobberEntityMixin {

	@Shadow
	public abstract Player getPlayerOwner();

	@ModifyExpressionValue(method = "retrieve(Lnet/minecraft/world/item/ItemStack;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<ItemStack> lifepath$onCatch(ObjectArrayList<ItemStack> loot) {
		Player owner = getPlayerOwner();
		for (ItemStack caught : loot) {
			VanillaGameplayProducers.onFishCaught(owner, caught);
		}
		return loot;
	}
}
