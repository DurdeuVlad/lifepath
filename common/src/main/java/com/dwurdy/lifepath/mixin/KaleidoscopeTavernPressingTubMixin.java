package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kaleidoscope Tavern pressing-tub fills → alcohol XP (NeoForge-only, gated by
 * the {@code lifepath.compat.kaleidoscopetavern.mixins.json} config's
 * {@code requiredMods}). Verified against kaleidoscopetavern 1.2.0 bytecode:
 * {@code PressingTubBlockEntity.getResult(LivingEntity, ItemStack)} drains
 * 1000&nbsp;mB of pressed fluid into the taker's held container via
 * {@code FluidUtils.fillItem}, which hands the filled container to the entity
 * and returns {@code true}. The produced stack is predicted at HEAD by
 * replaying {@code fillItem}'s simulate pass (probe copy → simulated transfer
 * → {@code getContainer()}), all reflectively since common has no NeoForge
 * types. Attributed to the taker; non-players earn nothing.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.PressingTubBlockEntity",
		remap = false)
public abstract class KaleidoscopeTavernPressingTubMixin {

	private static final int LIFEPATH_PRESSING_TUB_FILL_AMOUNT = 1000;

	@Unique
	private ItemStack lifepath$pendingResult = ItemStack.EMPTY;

	@Inject(method = "getResult", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$captureResult(LivingEntity taker, ItemStack container,
			CallbackInfoReturnable<Boolean> cir) {
		lifepath$pendingResult = lifepath$predictFill(container);
	}

	@Inject(method = "getResult", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emit(LivingEntity taker, ItemStack container,
			CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			KaleidoscopeTakeSupport.emitTake(taker, lifepath$pendingResult);
		}
		lifepath$pendingResult = ItemStack.EMPTY;
	}

	/**
	 * Replays {@code FluidUtils.fillItem}'s simulated pass on a probe copy of
	 * the held container: {@code FluidUtil.getFluidHandler(probe)}, a
	 * non-committing {@code tryFluidTransfer(probeHandler, tank, 1000, false)}
	 * out of the tub's {@code getFluid()} tank, then
	 * {@code IFluidHandlerItem.getContainer()} — the stack the taker receives.
	 * Any reflective failure degrades to {@link ItemStack#EMPTY}.
	 */
	@Unique
	private ItemStack lifepath$predictFill(ItemStack container) {
		try {
			ItemStack probe = container.copyWithCount(1);
			Class<?> fluidUtil = Class.forName("net.neoforged.neoforge.fluids.FluidUtil");
			Object handler = fluidUtil.getMethod("getFluidHandler", ItemStack.class)
					.invoke(null, probe);
			if (!(handler instanceof Optional<?> opt) || opt.isEmpty()) {
				return ItemStack.EMPTY;
			}
			Object itemHandler = opt.get();
			Object tank = getClass().getMethod("getFluid").invoke(this);
			Class<?> iFluidHandler = Class
					.forName("net.neoforged.neoforge.fluids.capability.IFluidHandler");
			Object moved = fluidUtil.getMethod("tryFluidTransfer", iFluidHandler,
					iFluidHandler, int.class, boolean.class)
					.invoke(null, itemHandler, tank, LIFEPATH_PRESSING_TUB_FILL_AMOUNT, false);
			if (moved == null || Boolean.TRUE
					.equals(moved.getClass().getMethod("isEmpty").invoke(moved))) {
				return ItemStack.EMPTY;
			}
			Class<?> iFluidHandlerItem = Class.forName(
					"net.neoforged.neoforge.fluids.capability.IFluidHandlerItem");
			Object filled = iFluidHandlerItem.getMethod("getContainer").invoke(itemHandler);
			return filled instanceof ItemStack stack ? stack : ItemStack.EMPTY;
		} catch (ReflectiveOperationException | ClassCastException e) {
			LifepathMod.LOGGER.debug("Tavern pressing-tub fill prediction failed: {}",
					e.toString());
			return ItemStack.EMPTY;
		}
	}
}
