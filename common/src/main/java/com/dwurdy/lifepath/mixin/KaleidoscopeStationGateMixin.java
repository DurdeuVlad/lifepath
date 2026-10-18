package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.dwurdy.lifepath.skill.MaterialGates;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Material-gate denial for all five Kaleidoscope Cookery stations
 * (NeoForge-only, gated by the {@code lifepath.compat.kaleidoscope.mixins.json}
 * config's {@code requiredMods}). Verified against KC 1.6.0 bytecode (class
 * bytes md5-identical on 1.5.1 except the chopping board, whose seam is
 * unchanged): every take is dispatched from the block's
 * {@code useItemOn(ItemStack, BlockState, Level, BlockPos, Player,
 * InteractionHand, BlockHitResult)} via a direct {@code invokeinterface} on
 * {@code IPot}/{@code IStockpot}/{@code ISteamer}/{@code IChoppingBoard}/
 * {@code IShawarmaSpit} — no Forge event fires on the path.
 *
 * <p>The gate runs at block level, ahead of the whole dispatch, rather than at
 * the BE take methods: a {@code false} take return there only falls through to
 * KC's next branch (the wok's "need_lit_stove" tip, ingredient insertion), so
 * cancelling the interaction outright with {@link ItemInteractionResult#FAIL}
 * is both cleaner and guarantees no follow-on mutation. Pending outputs are
 * read exactly as the XP mixins do — {@code getResult()}, {@code
 * getCurrentCutStack()}, {@code getItems()}, and the spit's public
 * {@code cookedItem} field — so the gate and the XP seam evaluate the same
 * stack.
 */
@Pseudo
@Mixin(targets = {
		"com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock",
		"com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock",
		"com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock",
		"com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.ChoppingBoardBlock",
		"com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.ShawarmaSpitBlock"
}, remap = false)
public abstract class KaleidoscopeStationGateMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"), remap = false,
			require = 0, cancellable = true)
	private void lifepath$gateStationTake(ItemStack heldStack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return;
		}
		for (ItemStack pending : lifepath$pendingOutputs(be)) {
			MaterialGates.Denial denial =
					MaterialGates.denialFor(serverPlayer, pending, false);
			if (denial != null) {
				MaterialGates.notifyDenied(serverPlayer, denial);
				cir.setReturnValue(ItemInteractionResult.FAIL);
				return;
			}
		}
	}

	/**
	 * The stacks the station's take method would hand over right now — the
	 * wok/stockpot finished dish, the chopping board's cut result, every
	 * takable steamer slot ({@code takeFood} empties all of them, so any gated
	 * dish denies), or the spit's finished {@code cookedItem}. Dispatched on
	 * the block entity's class name so one mixin covers all five blocks; an
	 * unrelated BE yields no candidates.
	 */
	@Unique
	private static List<ItemStack> lifepath$pendingOutputs(BlockEntity be) {
		String name = be.getClass().getName();
		if (name.endsWith(".kitchen.PotBlockEntity")
				|| name.endsWith(".kitchen.StockpotBlockEntity")) {
			return List.of(KaleidoscopeTakeSupport.readStack(be, "getResult"));
		}
		if (name.endsWith(".kitchen.ChoppingBoardBlockEntity")) {
			return List.of(KaleidoscopeTakeSupport.readStack(be,
					"getCurrentCutStack"));
		}
		if (name.endsWith(".kitchen.SteamerBlockEntity")) {
			return KaleidoscopeTakeSupport.readStacks(be, "getItems");
		}
		if (name.endsWith(".kitchen.ShawarmaSpitBlockEntity")) {
			return lifepath$shawarmaResult(be);
		}
		return List.of();
	}

	/**
	 * The spit's takeable output: the public {@code cookedItem} field, but only
	 * once {@code cookTime} hits zero — before that, {@code onTakeCookedItem}
	 * returns the raw {@code cookingItem} (an input retrieval) which is left
	 * ungated. Mirrors {@link KaleidoscopeShawarmaSpitMixin}'s take preview.
	 */
	@Unique
	private static List<ItemStack> lifepath$shawarmaResult(BlockEntity be) {
		try {
			Class<?> cls = be.getClass();
			if (cls.getField("cookTime").getInt(be) <= 0) {
				Object cooked = cls.getField("cookedItem").get(be);
				if (cooked instanceof ItemStack stack && !stack.isEmpty()) {
					return List.of(stack.copy());
				}
			}
		} catch (ReflectiveOperationException | ClassCastException e) {
			LifepathMod.LOGGER.debug("KC gate: ShawarmaSpitBlockEntity field "
					+ "read failed: {}", e.toString());
		}
		return List.of();
	}
}
