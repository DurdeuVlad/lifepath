package com.dwurdy.lifepath.compat.firstaid;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helpers for the First Aid (New) treatment seams. Free of firstaid and
 * neoforge types — the mixins hand over vanilla signatures and reflectively
 * accessed packet internals only, so this class loads without the mod.
 */
public final class FirstAidMedical {
	private FirstAidMedical() {
	}

	/**
	 * Right-click use of any {@code ItemHealing} (bandage, plaster, morphine,
	 * painkillers, adrenaline, injectors). Subject = the medicine item.
	 */
	public static void emitHealingUse(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || hand == null
				|| isAutomation(serverPlayer)) {
			return;
		}
		emit(serverPlayer, player.getItemInHand(hand), null);
	}

	/**
	 * Body-part GUI apply ({@code MessageApplyHealingItem} on the server).
	 * {@code msg} carries private {@code part}/{@code hand} fields and
	 * {@code ctx} is an {@code IPayloadContext} — both reached reflectively so
	 * nothing foreign links here. Subject = {@code firstaid:<part>} when the
	 * part resolves (head surgery rates above a foot bandage); falls back to
	 * the held medicine item.
	 */
	public static void emitHealingApply(Object msg, Object ctx) {
		Player player = playerOf(ctx);
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(serverPlayer)) {
			return;
		}
		ResourceLocation part = partOf(msg);
		ItemStack medicine = ItemStack.EMPTY;
		Object hand = fieldOf(msg, "hand");
		if (hand instanceof InteractionHand h) {
			medicine = serverPlayer.getItemInHand(h);
		}
		emit(serverPlayer, medicine, part);
	}

	/**
	 * The rescue item held at {@code completeRescue}-entry, before the method
	 * shrinks/damages it — the rescue-XP subject needs the pre-consumption
	 * identity (bandage stabilise vs. adrenaline shot vs. defibrillator).
	 */
	private static final ThreadLocal<ResourceLocation> RESCUE_ITEM = new ThreadLocal<>();

	/**
	 * HEAD of {@code EventHandler.completeRescue}: reads the {@code
	 * InteractionTarget} record's {@code hand} component reflectively and
	 * stores the rescuer's held item id. Only called while a rescue item is
	 * still equipped — the method itself validates that before committing.
	 * The slot is cleared unconditionally first: the RETURN-side cleanup in
	 * {@link #clearRescue} does not run when {@code completeRescue} exits
	 * exceptionally, and the early returns below must not leave a stale
	 * capture from a previous call readable by {@link #onRescueSuccess}.
	 */
	public static void captureRescueItem(Player rescuer, Object target) {
		RESCUE_ITEM.remove();
		if (!(rescuer instanceof ServerPlayer)) {
			return;
		}
		Object hand = fieldOf(target, "hand");
		if (hand instanceof InteractionHand h) {
			ItemStack held = rescuer.getItemInHand(h);
			if (!held.isEmpty()) {
				RESCUE_ITEM.set(net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getKey(held.getItem()));
			}
		}
	}

	/**
	 * Emitted at the rescuer's own {@code displayClientMessage} inside
	 * {@code completeRescue} — that call site sits behind the {@code
	 * rescueFromCriticalState}/{@code *RescueFromCriticalState} success
	 * boolean, so reaching it means the patient actually got back up.
	 * The rescuer (the acting player) earns; the victim is never paid.
	 */
	public static void onRescueSuccess(Player rescuer) {
		ResourceLocation item = RESCUE_ITEM.get();
		if (!(rescuer instanceof ServerPlayer serverPlayer)
				|| isAutomation(serverPlayer)) {
			return;
		}
		if (item == null) {
			return; // HEAD capture missed — silent miss beats a wrong subject
		}
		RESCUE_ITEM.remove(); // one rescue = one emit, both messages or not
		emit(serverPlayer, ItemStack.EMPTY, item);
	}

	/** RETURN of {@code completeRescue} — ThreadLocal hygiene. */
	public static void clearRescue() {
		RESCUE_ITEM.remove();
	}

	/**
	 * Self-defibrillation ({@code attemptSelfDefibrillator}) — emitted at the
	 * method's success message. The method only proceeds with a defibrillator
	 * equipped, so the subject is fixed.
	 */
	public static void onSelfDefib(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(serverPlayer)) {
			return;
		}
		emit(serverPlayer, ItemStack.EMPTY,
				ResourceLocation.fromNamespaceAndPath("firstaid", "defibrillator"));
	}

	private static void emit(ServerPlayer player, ItemStack medicine,
			@Nullable ResourceLocation part) {
		ResourceLocation subject = part != null ? part
				: medicine.isEmpty() ? ResourceLocation.fromNamespaceAndPath("firstaid", "healing")
						: net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(medicine.getItem());
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.TREATMENT,
				subject, Set.of(), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of()));
	}

	@Nullable
	private static Player playerOf(Object ctx) {
		if (ctx == null) {
			return null;
		}
		try {
			Object p = ctx.getClass().getMethod("player").invoke(ctx);
			return p instanceof Player pl ? pl : null;
		} catch (Throwable t) {
			return null;
		}
	}

	@Nullable
	private static ResourceLocation partOf(Object msg) {
		Object part = fieldOf(msg, "part");
		if (part instanceof Enum<?> e) {
			return ResourceLocation.fromNamespaceAndPath("firstaid", e.name().toLowerCase(java.util.Locale.ROOT));
		}
		return null;
	}

	@Nullable
	private static Object fieldOf(Object owner, String name) {
		if (owner == null) {
			return null;
		}
		try {
			Field f = owner.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(owner);
		} catch (Throwable t) {
			return null;
		}
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
