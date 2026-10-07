package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.skill.OutcomeService;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skill-scaled anvil XP cost (#192): after vanilla {@code createResult}
 * computes {@code cost}, the smithing outcome rule's {@code anvil_cost_mult}
 * for the player's band scales it. Server-side only — the synced DataSlot
 * propagates the discounted value to the client display, so the number the
 * player sees is the number they pay.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuCostMixin extends ItemCombinerMenu {

	protected AnvilMenuCostMixin(MenuType<?> type, int syncId, Inventory inventory,
			ContainerLevelAccess access) {
		super(type, syncId, inventory, access);
	}

	@Shadow
	@Final
	private DataSlot cost;

	@Inject(method = "createResult", at = @At("RETURN"))
	private void lifepath$scaleRepairCost(CallbackInfo ci) {
		int vanilla = this.cost.get();
		if (vanilla <= 0 || !(this.player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ItemStack result = this.resultSlots.getItem(0);
		if (result.isEmpty()) {
			return;
		}
		Set<ResourceLocation> tags = result.getTags()
				.map(TagKey::location).collect(java.util.stream.Collectors.toCollection(HashSet::new));
		tags.add(LifepathMod.id("anvil"));
		tags.add(LifepathMod.id("smithing_workstations"));
		double mult = OutcomeService.resolveForPlayer(serverPlayer,
				ActivityTypes.SMITHING,
				BuiltInRegistries.ITEM.getKey(result.getItem()), tags).anvilCostMult();
		if (mult == 1.0) {
			return;
		}
		this.cost.set(Math.max(1, (int) Math.round(vanilla * mult)));
	}
}
