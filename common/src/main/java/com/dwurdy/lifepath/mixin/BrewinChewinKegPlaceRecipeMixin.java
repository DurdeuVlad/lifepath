package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Brewin' And Chewin' keg pours → alcohol XP, recipe-book autofill path
 * (NeoForge-only, gated by the {@code lifepath.compat.brewinandchewin
 * .mixins.json} config's {@code requiredMods}). Verified against BnC 4.5.0
 * bytecode: {@code KegPlaceRecipe.handleRecipeClicked} empties the keg tank
 * into the player's containers via {@code KegBlockEntity.extractInGui}
 * (three call sites) while placing a fermenting recipe. The player isn't a
 * parameter — resolved reflectively through the inherited
 * {@code ServerPlaceRecipe.inventory} field. Attributed to the player whose
 * menu is open; unresolvable → no XP.
 */
@Pseudo
@Mixin(targets = "umpaz.brewinandchewin.common.block.entity.container.KegPlaceRecipe",
		remap = false)
public abstract class BrewinChewinKegPlaceRecipeMixin {

	@WrapOperation(method = "handleRecipeClicked", at = @At(value = "INVOKE",
			target = "Lumpaz/brewinandchewin/common/block/entity/KegBlockEntity;"
					+ "extractInGui(Lnet/minecraft/world/item/ItemStack;I)"
					+ "Ljava/util/List;"),
			remap = false, require = 0)
	private List<ItemStack> lifepath$emitOnExtract(@Coerce Object keg, ItemStack container, int count,
			Operation<List<ItemStack>> original) {
		List<ItemStack> produced = original.call(keg, container, count);
		Player player = lifepath$resolvePlayer();
		if (produced != null && player != null) {
			for (ItemStack s : produced) {
				KaleidoscopeTakeSupport.emitTake(player, s);
			}
		}
		return produced;
	}

	/**
	 * {@code ServerPlaceRecipe.inventory} is a protected field on the vanilla
	 * superclass — read it reflectively so a Mojmap rename degrades to a debug
	 * log instead of a hard failure.
	 */
	@Unique
	private Player lifepath$resolvePlayer() {
		Class<?> type = getClass();
		while (type != null) {
			try {
				Field inventory = type.getDeclaredField("inventory");
				inventory.setAccessible(true);
				if (inventory.get(this) instanceof Inventory inv) {
					return inv.player;
				}
				return null;
			} catch (NoSuchFieldException e) {
				type = type.getSuperclass();
			} catch (ReflectiveOperationException | ClassCastException e) {
				LifepathMod.LOGGER.debug("BnC keg recipe-place player lookup failed: {}",
						e.toString());
				return null;
			}
		}
		return null;
	}
}
