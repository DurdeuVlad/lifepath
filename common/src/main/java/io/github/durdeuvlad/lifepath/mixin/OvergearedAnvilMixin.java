package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.compat.overgeared.OvergearedCompat;
import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overgeared forge completion → Smithing XP (NeoForge-only, gated by the
 * {@code lifepath.compat.overgeared.mixins.json} config's
 * {@code requiredMods}). Verified
 * against Overgeared's bytecode: {@code craftItem()} drops the forged result
 * straight into the world — it never passes a result slot, so no
 * {@code ItemCraftedEvent} exists to republish. Every concrete anvil tier
 * delegates to {@code AbstractSmithingAnvilBlockEntity.craftItem()}/
 * {@code craftItemWithBlueprint()}, so injecting here catches all of them.
 *
 * <p>The target class is never referenced — the mixin applies via a string
 * target and reads {@code getOwnerUUID()}/{@code getCurrentRecipe()}/
 * {@code getResultItem(provider)} reflectively, so an Overgeared update that
 * renames members degrades to a debug log, not a crash.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity", remap = false)
public abstract class OvergearedAnvilMixin {

	/**
	 * Steel-tier anvils run {@code super.craftItem()} then
	 * {@code super.craftItemWithBlueprint()} in one completion — award at most
	 * once per game tick so the second invocation can't double-count.
	 */
	@Unique
	private long lifepath$lastForgeAwardTick = Long.MIN_VALUE;

	@Inject(method = {"craftItem", "craftItemWithBlueprint"},
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$awardForgeXp(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (level == null || level.isClientSide()) {
			return;
		}
		long tick = level.getGameTime();
		if (tick == lifepath$lastForgeAwardTick) {
			return;
		}
		try {
			Class<?> beClass = self.getClass();
			Object owner = beClass.getMethod("getOwnerUUID").invoke(self);
			if (!(owner instanceof UUID ownerId)) {
				return;
			}
			MinecraftServer server = level.getServer();
			if (server == null) {
				return;
			}
			// Offline forgers earn nothing — XP needs an online recipient.
			ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
			if (player == null) {
				return;
			}
			Object recipe = ((Optional<?>) beClass.getMethod("getCurrentRecipe")
					.invoke(self)).orElse(null);
			if (recipe == null) {
				return;
			}
			Method getResultItem = recipe.getClass()
					.getMethod("getResultItem", HolderLookup.Provider.class);
			Object result = getResultItem.invoke(recipe, level.registryAccess());
			if (!(result instanceof ItemStack stack) || stack.isEmpty()) {
				return;
			}
			lifepath$lastForgeAwardTick = tick;
			VanillaGameplayProducers.onForgeOutput(player, stack,
					OvergearedCompat.FORGE_WORKSTATION);
		} catch (ReflectiveOperationException e) {
			LifepathMod.LOGGER.debug("Overgeared anvil XP hook degraded: {}", e.toString());
		}
	}
}
