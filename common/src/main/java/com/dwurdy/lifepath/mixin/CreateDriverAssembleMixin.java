package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.create.CreateAssemblySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create contraption-driver "assemble now" clicks → engineering XP
 * (NeoForge-only, gated by the {@code lifepath.compat.create.mixins.json}
 * config's {@code requiredMods}). Verified against Create 6.0.10 bytecode:
 * every driver block implements the same
 * {@code useItemOn(stack, state, level, pos, player, hand, hit)} — the
 * {@code mayBuild()}/{@code isShiftKeyDown()} (shift disassembles) and
 * non-empty-hand checks bail early with {@code FAIL} or
 * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} depending on the block, then
 * on the server the empty-hand path calls
 * {@code withBlockEntityDo(level, pos, be -> be.assembleNextTick = true)}
 * and returns {@code ItemInteractionResult.SUCCESS}. One mixin over all
 * seven drivers covers every manual contraption assembly — mechanical,
 * windmill and clockwork bearings, piston, pulley, gantry carriage and
 * elevator pulley.
 *
 * <p>{@code SUCCESS} is only reachable after Create's own gates passed, so
 * the RETURN check needs no mayBuild/shift re-check — the residual
 * conditions are the client side (Create also returns {@code SUCCESS}
 * there, without arming anything) and the empty-hand requirement. The
 * click only ARMS the block entity, so the emit is debounced inside the
 * support: spam-clicks inside 40 ticks pay once, a real disassemble →
 * reassemble cycle pays each time. The source id is the actual driver
 * block ({@code create:mechanical_bearing}, {@code create:gantry_carriage},
 * …) so xp_source data can weight surfaces differently if it wants.
 */
@Pseudo
@Mixin(targets = {
		"com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock",
		"com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock",
		"com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlock",
		"com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock",
		"com.simibubi.create.content.contraptions.pulley.PulleyBlock",
		"com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock",
		"com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlock"},
		remap = false)
public abstract class CreateDriverAssembleMixin {

	@Inject(method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onDriverAssemble(ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (cir.getReturnValue() != ItemInteractionResult.SUCCESS
				|| level.isClientSide() || !stack.isEmpty()
				|| !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		try {
			CreateAssemblySupport.onAssembleRequest(serverPlayer, pos,
					BuiltInRegistries.BLOCK.getKey(state.getBlock()), "driver");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("create driver assemble seam failed", t);
		}
	}
}
