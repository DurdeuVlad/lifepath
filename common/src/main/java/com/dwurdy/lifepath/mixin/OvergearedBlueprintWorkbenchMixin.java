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
 * player is resolved reflectively from the menu's input container (the
 * player's {@link Inventory}), so an Overgeared refactor degrades to a debug
 * log, not a crash.
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
			int level = com.dwurdy.lifepath.skill.SkillService.progress(
					com.dwurdy.lifepath.character.CharacterManager
							.getCharacter(player), rule.skill()).level();
			if (level < min) {
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("lifepath.forge.blueprint_gated",
								rule.skill().getPath(), min), true);
				ci.cancel();
				return;
			}
		}
	}

	/** The menu holds no player ref — the input container IS the player's
	 *  inventory in practice; walk fields for any {@link Inventory}. */
	@Unique
	private ServerPlayer lifepath$player() {
		Class<?> cls = getClass();
		while (cls != null) {
			for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
				try {
					f.setAccessible(true);
					Object v = f.get(this);
					if (v instanceof Inventory inv
							&& inv.player instanceof ServerPlayer sp) {
						return sp;
					}
				} catch (Throwable ignored) {
				}
			}
			cls = cls.getSuperclass();
		}
		return null;
	}
}
