package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blueprint-workbench gate (anti one-man-army): drafting a blueprint requires
 * the smithing level the outcome rule asks for
 * ({@code blueprint_min_level}). Blueprints are the licensed bypass for the
 * forge-time material gates — if anyone could draft their own, the gate would
 * be dead, so creation itself is skilled work.
 *
 * <p>NeoForge-only via {@code lifepath.compat.overgeared.mixins.json}; the
 * menu keeps no player field, so the player is recovered from the registered
 * slots' containers (player-inventory slots carry the {@link Inventory}).
 * An Overgeared refactor degrades to a skipped check, not a crash.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.BlueprintWorkbenchMenu", remap = false)
public abstract class OvergearedBlueprintWorkbenchMixin {

	@Inject(method = "createBlueprint", at = @At("HEAD"), remap = false,
			require = 0, cancellable = true)
	private void lifepath$gateBlueprintCreation(CallbackInfo ci) {
		ServerPlayer player = lifepath$player();
		if (player == null) {
			return;
		}
		for (var rule : LifepathContent.outcomeRules().all().values()) {
			int min = rule.blueprintMinLevel();
			if (min < 0) {
				continue;
			}
			var progress = com.dwurdy.lifepath.skill.SkillService.progress(
					com.dwurdy.lifepath.character.CharacterManager
							.getCharacter(player), rule.skill());
			int level = progress == null ? 0 : progress.level();
			if (level < min) {
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("lifepath.forge.blueprint_gated",
								rule.skill().getPath(), min), true);
				ci.cancel();
				return;
			}
		}
	}

	/** The menu drops the player ref after construction — recover it through
	 * the registered slots: player-inventory slots carry the {@link Inventory}
	 * whose {@code player} is the ServerPlayer doing the drafting. */
	@Unique
	private ServerPlayer lifepath$player() {
		for (var slot : ((net.minecraft.world.inventory.AbstractContainerMenu)
				(Object) this).slots) {
			if (slot.container instanceof Inventory inv
					&& inv.player instanceof ServerPlayer sp) {
				return sp;
			}
		}
		return null;
	}
}
