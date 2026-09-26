package io.github.durdeuvlad.lifepath.event;

import java.util.Map;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Factories for the named activity shapes (M2-3). Each fixes the type id and
 * the documented attribute keys so all producers (vanilla hooks now, compat
 * adapters later) emit identical normalized events:
 *
 * <ul>
 *   <li>{@code mining}: sourceId = block id; attrs {@code tier} (optional tool/material tier)</li>
 *   <li>{@code farming}: sourceId = crop block/item id; attrs {@code mature}=true/false</li>
 *   <li>{@code smithing}: sourceId = recipe id; attrs {@code output}, {@code material_tier}, {@code workstation}</li>
 *   <li>{@code fishing}: sourceId = loot item id; attrs {@code rarity}</li>
 *   <li>{@code crafting}: sourceId = recipe/output id (stub shape)</li>
 *   <li>{@code combat}: sourceId = entity type id (stub shape)</li>
 * </ul>
 */
public final class ActivityEvents {
	private ActivityEvents() {
	}

	public static ActivityEvent mining(ServerPlayerEntity player, Identifier blockId,
			@Nullable Identifier oreTier) {
		return new ActivityEvent(player, ActivityTypes.MINING, blockId,
				tags(oreTier), ActivityEvent.Cause.PLAYER, now(),
				oreTier == null ? Map.of() : Map.of("tier", oreTier.toString()));
	}

	public static ActivityEvent farming(ServerPlayerEntity player, Identifier cropId, boolean mature) {
		return new ActivityEvent(player, ActivityTypes.FARMING, cropId,
				mature ? Set.of(Identifier.of("lifepath", "mature")) : Set.of(),
				ActivityEvent.Cause.PLAYER, now(), Map.of("mature", Boolean.toString(mature)));
	}

	public static ActivityEvent smithing(ServerPlayerEntity player, Identifier recipeId,
			@Nullable Identifier outputId, @Nullable String materialTier,
			@Nullable Identifier workstationId) {
		Map<String, String> attrs = new java.util.LinkedHashMap<>();
		if (outputId != null) {
			attrs.put("output", outputId.toString());
		}
		if (materialTier != null) {
			attrs.put("material_tier", materialTier);
		}
		if (workstationId != null) {
			attrs.put("workstation", workstationId.toString());
		}
		return new ActivityEvent(player, ActivityTypes.SMITHING, recipeId,
				workstationId == null ? Set.of() : Set.of(workstationId),
				ActivityEvent.Cause.PLAYER, now(), attrs);
	}

	public static ActivityEvent fishing(ServerPlayerEntity player, Identifier lootId,
			@Nullable String rarity) {
		return new ActivityEvent(player, ActivityTypes.FISHING, lootId, Set.of(),
				ActivityEvent.Cause.PLAYER, now(),
				rarity == null ? Map.of() : Map.of("rarity", rarity));
	}

	public static ActivityEvent crafting(ServerPlayerEntity player, Identifier recipeOrOutputId) {
		return new ActivityEvent(player, ActivityTypes.CRAFTING, recipeOrOutputId, Set.of(),
				ActivityEvent.Cause.PLAYER, now(), Map.of());
	}

	public static ActivityEvent combat(ServerPlayerEntity player, Identifier entityTypeId) {
		return new ActivityEvent(player, ActivityTypes.COMBAT, entityTypeId, Set.of(),
				ActivityEvent.Cause.PLAYER, now(), Map.of());
	}

	private static Set<Identifier> tags(@Nullable Identifier extra) {
		return extra == null ? Set.of() : Set.of(extra);
	}

	private static long now() {
		return System.currentTimeMillis();
	}
}
