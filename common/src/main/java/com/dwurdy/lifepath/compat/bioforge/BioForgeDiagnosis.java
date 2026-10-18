package com.dwurdy.lifepath.compat.bioforge;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helper for the BioForge microscope seam. The identify button id is a
 * public constant on the block entity but resolved reflectively — a BioForge
 * renumber degrades to "no button matches" instead of paying the wrong act.
 * The sample item (subject) is read out of the entity's private itemHandler
 * on the same basis; unreadable → generic {@code bioforge:microscope} subject.
 */
public final class BioForgeDiagnosis {
	private BioForgeDiagnosis() {
	}

	private static volatile int identifyButton = Integer.MIN_VALUE;

	/** Called from the mixin on every successful {@code handleButton}. */
	public static void onButton(Player player, Object blockEntity, int buttonId) {
		if (buttonId != identifyButton()
				|| !(player instanceof ServerPlayer serverPlayer)
				|| isAutomation(serverPlayer)) {
			return;
		}
		ResourceLocation subject = sampleOf(blockEntity);
		ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.DIAGNOSIS,
				subject, Set.of(), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of()));
	}

	/** Reflective read of {@code MicroscopeBlockEntity.IDENTIFY_GENE_BUTTON} (currently 0). */
	private static int identifyButton() {
		int v = identifyButton;
		if (v == Integer.MIN_VALUE) {
			try {
				Field f = Class.forName("net.jenkimods.bioforge.world.microscope.MicroscopeBlockEntity")
						.getField("IDENTIFY_GENE_BUTTON");
				v = f.getInt(null);
				identifyButton = v;
			} catch (Throwable t) {
				// Don't cache the failure — a transient class-load hiccup would
				// otherwise permanently dead the seam for this JVM.
				return -1;
			}
		}
		return v;
	}

	private static ResourceLocation sampleOf(Object blockEntity) {
		try {
			Field handler = blockEntity.getClass().getDeclaredField("itemHandler");
			handler.setAccessible(true);
			Object inv = handler.get(blockEntity);
			Object stack = inv.getClass().getMethod("getStackInSlot", int.class).invoke(inv, 0);
			if (stack instanceof net.minecraft.world.item.ItemStack s && !s.isEmpty()) {
				return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
			}
		} catch (Throwable t) {
			// fall through to the generic subject
		}
		return ResourceLocation.fromNamespaceAndPath("bioforge", "microscope");
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
