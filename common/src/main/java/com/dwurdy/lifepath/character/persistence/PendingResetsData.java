package com.dwurdy.lifepath.character.persistence;

import com.dwurdy.lifepath.LifepathMod;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level ledger of player UUIDs whose character data must be reset the
 * next time they join — the offline half of {@code /lifepath character
 * resetall confirm}. Online players are reset live at command time; everyone
 * else who has ever joined (a {@code playerdata/<uuid>.dat} exists) gets a
 * marker here, consumed by {@code CharacterManager.initializeCharacter} on
 * their next login — the reset then runs before the join sync/catalog push,
 * so the returning player lands on fresh defaults with the pickers re-armed.
 *
 * <p>Storage: vanilla {@link SavedData} at
 * {@code <world>/data/lifepath_pending_resets.dat}. The per-player
 * {@code lifepath:character_data} attachment cannot express this state —
 * an offline player's entity never loads, so nothing would ever read or
 * write the marker. A marker survives restarts until consumed; UUIDs that
 * never return simply linger (bounded by total historical players — the
 * same set vanilla playerdata already stores).
 */
public final class PendingResetsData extends SavedData {
	/** Storage filename; also the {@code computeIfAbsent} key. */
	public static final String DATA_NAME = "lifepath_pending_resets";
	private static final String TAG_PENDING = "pending";

	// DataFixTypes can't be null: vanilla's read path unconditionally calls
	// type.update() once the file exists (NeoForge null-guards it, common's
	// vanilla jar does not). COMMAND_STORAGE's fixers only touch a `contents`
	// key, so our `pending` list always passes through untouched — and with
	// the file stamped at the current DataVersion the update is a no-op.
	private static final SavedData.Factory<PendingResetsData> FACTORY =
			new SavedData.Factory<>(PendingResetsData::new, PendingResetsData::load,
					DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Set<UUID> pending = new LinkedHashSet<>();

	/** Server-side accessor — the saved-data store lives on the overworld. */
	public static PendingResetsData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
	}

	/** Package-visible for the save/load round-trip test; the codec itself is the contract. */
	static PendingResetsData load(CompoundTag tag, HolderLookup.Provider registries) {
		PendingResetsData data = new PendingResetsData();
		for (Tag entry : tag.getList(TAG_PENDING, Tag.TAG_STRING)) {
			try {
				data.pending.add(UUID.fromString(entry.getAsString()));
			} catch (IllegalArgumentException e) {
				LifepathMod.LOGGER.warn("skipping malformed pending-reset entry '{}'",
						entry.getAsString());
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (UUID uuid : pending) {
			list.add(StringTag.valueOf(uuid.toString()));
		}
		tag.put(TAG_PENDING, list);
		return tag;
	}

	/** Marks {@code uuid} for a deferred reset at its next login. */
	public void add(UUID uuid) {
		if (pending.add(uuid)) {
			setDirty();
		}
	}

	/**
	 * Removes the marker if present. Returns whether the player was pending —
	 * the caller performs the actual reset, so consumption is one-shot even
	 * if the subsequent reset were to fail.
	 */
	public boolean consume(UUID uuid) {
		if (pending.remove(uuid)) {
			setDirty();
			return true;
		}
		return false;
	}

	public boolean isPending(UUID uuid) {
		return pending.contains(uuid);
	}

	public int size() {
		return pending.size();
	}
}
