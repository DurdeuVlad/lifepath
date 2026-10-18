package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.create.CreateAssemblySupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create minecart-contraption deploys → engineering XP (NeoForge-only,
 * gated by the {@code lifepath.compat.create.mixins.json} config's
 * {@code requiredMods}). Verified against Create 6.0.10 bytecode:
 * {@code MinecartContraptionItem.useOn(ctx)} returns
 * {@code InteractionResult.FAIL} on non-rail clicks; on the server it
 * creates the minecart, attaches the contraption via
 * {@code addContraptionToMinecart}, shrinks the stack, and returns
 * {@code InteractionResult.SUCCESS} — the only post-commit return, so a
 * RETURN inject on SUCCESS fires exactly once per deployed contraption.
 * All three item variants ({@code minecart_contraption},
 * {@code furnace_minecart_contraption}, {@code chest_minecart_contraption})
 * share this one method and emit under the canonical
 * {@code create:minecart_contraption} source id; the held stack is not
 * re-read at RETURN since the shrink can leave it empty.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem",
		remap = false)
public abstract class CreateMinecartContraptionMixin {

	@Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onContraptionDeployed(UseOnContext context,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue() != InteractionResult.SUCCESS
				|| context.getLevel().isClientSide()
				|| !(context.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		try {
			CreateAssemblySupport.onDeploy(player, context.getClickedPos(),
					ResourceLocation.fromNamespaceAndPath("create",
							"minecart_contraption"),
					"minecart");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("create minecart contraption seam failed", t);
		}
	}
}
