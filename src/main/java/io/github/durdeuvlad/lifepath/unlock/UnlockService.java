package io.github.durdeuvlad.lifepath.unlock;

import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.UnlockDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Unlock grants (M9-4): evaluates {@link UnlockDefinition} sources and writes
 * the granted content ids into {@code PlayerCharacterData.unlocks[]}.
 * Unlockable content (today: {@code selection:"unlocked"} species) reads that
 * list at selection time — the engine never special-cases the ids.
 *
 * <p>Seams: {@link #onUseItem} (ritual items), {@link #onActivity} (world
 * events via the dispatcher), {@link #onAdvancement} (vanilla advancement
 * completion, the quest hook), admin grants via command. Fail-closed —
 * unknown unlock ids/content are no-ops; every grant is idempotent and
 * only syncs when state actually changed.
 */
public final class UnlockService {
	private UnlockService() {
	}

	/**
	 * Grants one content id. Idempotent; false if already unlocked.
	 * The id needs no def of its own — unlocks are opaque access tokens.
	 */
	public static boolean grant(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier contentId) {
		if (!data.addId(PlayerCharacterData.ListKind.UNLOCKS, contentId)) {
			return false;
		}
		mark(player);
		return true;
	}

	/** Revokes one content id. False if not held. */
	public static boolean revoke(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier contentId) {
		if (!data.removeId(PlayerCharacterData.ListKind.UNLOCKS, contentId)) {
			return false;
		}
		mark(player);
		return true;
	}

	/** Fires an unlock def — grants every id it names; returns count granted. */
	private static int fire(UnlockDefinition def, PlayerCharacterData data,
			@Nullable ServerPlayerEntity player) {
		int granted = 0;
		for (Identifier content : def.unlocks()) {
			if (grant(data, player, content)) {
				granted++;
			}
		}
		return granted;
	}

	/** {@code type:item} sources on a used stack; consumes on grant. */
	public static void onUseItem(ServerPlayerEntity player, ItemStack stack, long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null || stack.isEmpty()) {
			return;
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		for (UnlockDefinition def : LifepathContent.unlocks().all().values()) {
			UnlockDefinition.SourceRule rule = itemSourceFor(def, itemId);
			if (rule == null) {
				continue;
			}
			if (fire(def, data, player) > 0 && rule.consume()) {
				stack.decrement(1);
			}
		}
	}

	/**
	 * The {@code item} rule on {@code def} that matches {@code itemId}, or null.
	 * Separated from the stack/player plumbing so unit tests cover the match.
	 */
	static UnlockDefinition.@Nullable SourceRule itemSourceFor(
			UnlockDefinition def, Identifier itemId) {
		for (UnlockDefinition.SourceRule rule : def.sources()) {
			if ("item".equals(rule.type())
					&& rule.item().map(itemId::equals).orElse(false)) {
				return rule;
			}
		}
		return null;
	}

	/**
	 * {@code type:event} sources — each matching activity event rolls chance.
	 * {@code subject} narrows to the event's {@code sourceId} (e.g. the killed
	 * entity type); {@code tag} requires membership in the event's tag set.
	 */
	public static void onActivity(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			ActivityEvent event, long nowMs) {
		for (UnlockDefinition def : LifepathContent.unlocks().all().values()) {
			for (UnlockDefinition.SourceRule rule : def.sources()) {
				if (!"event".equals(rule.type())
						|| !rule.event().map(event.type()::equals).orElse(false)
						|| !rule.subject().map(event.sourceId()::equals).orElse(true)
						|| !rule.tag().map(t -> event.tags().contains(t)).orElse(true)) {
					continue;
				}
				Random random = player != null ? player.getWorld().getRandom()
						: Random.create();
				if (random.nextDouble() < rule.chance()) {
					fire(def, data, player);
				}
				break;
			}
		}
	}

	/** {@code type:advancement} sources — completing the advancement grants. */
	public static void onAdvancement(ServerPlayerEntity player, Identifier advancementId,
			long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		for (UnlockDefinition def : LifepathContent.unlocks().all().values()) {
			for (UnlockDefinition.SourceRule rule : def.sources()) {
				if ("advancement".equals(rule.type())
						&& rule.advancement().map(advancementId::equals).orElse(false)) {
					fire(def, data, player);
					break;
				}
			}
		}
	}

	/**
	 * Selection gate for {@code selection:"unlocked"} content — the read the
	 * picker/command must make before granting restricted content.
	 */
	public static boolean isUnlocked(PlayerCharacterData data, Identifier contentId) {
		return data.unlocks().contains(contentId);
	}

	private static void mark(@Nullable ServerPlayerEntity player) {
		if (player != null) {
			CharacterManager.changed(player);
		}
	}
}
