package com.dwurdy.lifepath.compat.esquisse;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helpers for Esquisse's sketchbook seams. The mod's payload handler
 * queues the real work into {@code enqueueWork} lambdas — the mixins hook the
 * lambda heads and this helper re-derives the same guards the lambda body
 * applies, all reflectively (the payloads, the {@code IPayloadContext} and
 * {@code ModDataComponents} are foreign types): a sketchbook must actually be
 * in a hand, the page index must exist, and a save must have spent pixels —
 * the payload's own pencil/eraser pixel counts — so a forged or no-op packet
 * earns nothing.
 *
 * <p>Subjects: a page save produces the filled {@code esquissemod:sketchbook};
 * tearing out a drawn page produces {@code esquissemod:sketched_page}. Tearing
 * a blank page yields {@code esquissemod:empty_page} in the mod — not
 * scholarship, so it emits nothing here.
 */
public final class EsquisseSketching {
	private EsquisseSketching() {
	}

	private static final ResourceLocation SKETCHBOOK =
			ResourceLocation.fromNamespaceAndPath("esquissemod", "sketchbook");
	private static final ResourceLocation SKETCHED_PAGE =
			ResourceLocation.fromNamespaceAndPath("esquissemod", "sketched_page");

	/** Cached {@code ModDataComponents.SKETCHBOOK_PAGES} component type. */
	private static volatile DataComponentType<?> pagesType;
	private static volatile boolean pagesTypeResolved;

	/**
	 * Sketchbook page save ({@code lambda$handleData$0} head). The payload
	 * carries the page index and how many pencil/eraser pixels the edit
	 * touched — zero pixels is an open/close, not a sketch.
	 */
	public static void emitSketchSave(Object ctx, Object payload) {
		if (!(playerOf(ctx) instanceof ServerPlayer player) || isAutomation(player)) {
			return;
		}
		ItemStack book = sketchbookInHand(player);
		int pixels = intCall(payload, "pencilPixels") + intCall(payload, "eraserPixels");
		if (book.isEmpty() || pixels <= 0 || !pageExists(book, intCall(payload, "pageIndex"))) {
			return;
		}
		emit(player, SKETCHBOOK, Map.of("pixels", Integer.toString(pixels)));
	}

	/**
	 * Page tear-out ({@code lambda$handleTearPage$1} head) — pays only when
	 * the torn page actually holds a sketch ({@code SketchData.isEmpty()}
	 * mirrors the mod's own empty→{@code empty_page} branch).
	 */
	public static void emitTearPage(Object ctx, Object payload) {
		if (!(playerOf(ctx) instanceof ServerPlayer player) || isAutomation(player)) {
			return;
		}
		ItemStack book = sketchbookInHand(player);
		int pageIndex = intCall(payload, "pageIndex");
		List<?> pages = book.isEmpty() ? null : pages(book);
		if (pages == null || pageIndex < 0 || pageIndex >= pages.size()
				|| isEmptySketch(pages.get(pageIndex))) {
			return;
		}
		emit(player, SKETCHED_PAGE, Map.of());
	}

	private static void emit(ServerPlayer player, ResourceLocation itemId,
			Map<String, String> meta) {
		ActivityDispatcher.publish(ActivityEvents.crafting(player, itemId,
				itemTags(itemId), meta, ActivityEvent.Cause.PLAYER));
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

	/** The held {@code esquissemod:sketchbook} — main hand first, like the mod. */
	private static ItemStack sketchbookInHand(Player player) {
		ItemStack main = player.getMainHandItem();
		if (isSketchbook(main)) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		return isSketchbook(off) ? off : ItemStack.EMPTY;
	}

	private static boolean isSketchbook(ItemStack stack) {
		return !stack.isEmpty()
				&& SKETCHBOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static boolean pageExists(ItemStack book, int pageIndex) {
		List<?> pages = pages(book);
		return pages != null && pageIndex >= 0 && pageIndex < pages.size();
	}

	/**
	 * {@code SKETCHBOOK_PAGES} component read — the public static
	 * {@code Supplier<DataComponentType<List<SketchData>>>} field on
	 * {@code ModDataComponents}, resolved once and cached.
	 */
	@Nullable
	private static List<?> pages(ItemStack book) {
		if (!pagesTypeResolved) {
			synchronized (EsquisseSketching.class) {
				if (!pagesTypeResolved) {
					try {
						Object supplier = Class.forName(
								"net.avizvul.esquissemod.component.ModDataComponents")
								.getField("SKETCHBOOK_PAGES").get(null);
						if (supplier instanceof Supplier<?> s
								&& s.get() instanceof DataComponentType<?> type) {
							pagesType = type;
						}
					} catch (Throwable t) {
						// stays unresolved — emits degrade to silence
					}
					pagesTypeResolved = true;
				}
			}
		}
		DataComponentType<?> type = pagesType;
		if (type == null) {
			return null;
		}
		try {
			Object value = book.getOrDefault(type, null);
			return value instanceof List<?> list ? list : null;
		} catch (Throwable t) {
			return null;
		}
	}

	/** {@code SketchData.isEmpty()} — reflective; unreadable → not a sketch. */
	private static boolean isEmptySketch(@Nullable Object page) {
		if (page == null) {
			return true;
		}
		try {
			Object empty = page.getClass().getMethod("isEmpty").invoke(page);
			return !(empty instanceof Boolean b) || b;
		} catch (Throwable t) {
			return true;
		}
	}

	private static int intCall(Object owner, String accessor) {
		try {
			Object v = owner.getClass().getMethod(accessor).invoke(owner);
			return v instanceof Integer i ? i : 0;
		} catch (Throwable t) {
			return 0;
		}
	}

	private static Set<ResourceLocation> itemTags(ResourceLocation itemId) {
		Item item = BuiltInRegistries.ITEM.get(itemId);
		if (item == null || item == Items.AIR) {
			return Set.of();
		}
		return new ItemStack(item).getTags().map(TagKey::location)
				.collect(Collectors.toSet());
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
