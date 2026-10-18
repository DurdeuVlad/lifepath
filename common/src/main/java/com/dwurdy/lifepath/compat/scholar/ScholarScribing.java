package com.dwurdy.lifepath.compat.scholar;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Emit helpers for Scholar's book-authoring seams. Scholar replaces the book
 * UX with its own spread editor but in-hand edits still travel over vanilla's
 * {@code ServerboundEditBookPacket} — only lectern editing uses Scholar's own
 * {@code LecternEditBookC2SP} record. The record's {@code title()} accessor is
 * read reflectively so this class loads without the mod; the vanilla packet is
 * read directly.
 *
 * <p>Subjects are the produced book ids: signing (a title is present) turns a
 * writable book into {@code minecraft:written_book}, page edits keep
 * {@code minecraft:writable_book} — the scholarly item tag routes them.
 */
public final class ScholarScribing {
	private ScholarScribing() {
	}

	private static final ResourceLocation WRITABLE_BOOK =
			ResourceLocation.withDefaultNamespace("writable_book");
	private static final ResourceLocation WRITTEN_BOOK =
			ResourceLocation.withDefaultNamespace("written_book");

	/**
	 * Lectern writing/signing — {@code LecternEditBookC2SP.handle} returned
	 * true, meaning a lectern block entity was found and the (async) text
	 * filter accepted the work. Subject = written book when the packet signs,
	 * writable book for a plain edit.
	 */
	public static void emitLecternEdit(Object packet, Player player, boolean handled) {
		if (!handled || !(player instanceof ServerPlayer serverPlayer)
				|| isAutomation(serverPlayer)) {
			return;
		}
		emit(serverPlayer, hasTitle(packet) ? WRITTEN_BOOK : WRITABLE_BOOK,
				Map.of("station", "lectern"));
	}

	/**
	 * In-hand book edit/sign — vanilla {@code handleEditBook} receipt, the
	 * channel Scholar's in-hand editor uses. Mirrors vanilla's own guard (the
	 * target slot must actually hold a writable book) so a forged packet for a
	 * non-book slot earns nothing.
	 */
	public static void emitBookEdit(ServerPlayer player, ServerboundEditBookPacket packet) {
		if (isAutomation(player) || !player.getInventory().getItem(packet.slot())
				.is(Items.WRITABLE_BOOK)) {
			return;
		}
		emit(player, packet.title().isPresent() ? WRITTEN_BOOK : WRITABLE_BOOK,
				Map.of("station", "hand"));
	}

	private static void emit(ServerPlayer player, ResourceLocation bookId,
			Map<String, String> meta) {
		ActivityDispatcher.publish(ActivityEvents.crafting(player, bookId,
				itemTags(bookId), meta, ActivityEvent.Cause.PLAYER));
	}

	/** Item-tag set the scholarly tag routes on — same surface onCraftOutput emits. */
	private static Set<ResourceLocation> itemTags(ResourceLocation itemId) {
		Item item = BuiltInRegistries.ITEM.get(itemId);
		if (item == null || item == Items.AIR) {
			return Set.of();
		}
		return new ItemStack(item).getTags().map(TagKey::location)
				.collect(Collectors.toSet());
	}

	private static boolean hasTitle(Object packet) {
		try {
			Object title = packet.getClass().getMethod("title").invoke(packet);
			return title instanceof Optional<?> o && o.isPresent();
		} catch (Throwable t) {
			return false;
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
