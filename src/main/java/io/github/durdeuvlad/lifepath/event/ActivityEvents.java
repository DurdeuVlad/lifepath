package io.github.durdeuvlad.lifepath.event;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Factories for the named activity shapes (M2-3). Each fixes the type id and
 * the documented attribute keys so all producers (vanilla hooks now, compat
 * adapters later) emit identical normalized events.
 *
 * <p><b>Facet contract:</b> {@code tags} are the MATCHABLE channel — xp_source
 * {@code required_tags} filter on them; {@code attributes} are descriptive
 * payload for consumers (UI, later anti-exploit detail), never matched.
 *
 * <ul>
 *   <li>{@code mining}: sourceId = block id; tag+attr {@code tier} (optional)</li>
 *   <li>{@code farming}: sourceId = crop id; tag {@code lifepath:mature} or {@code lifepath:immature}; attr {@code mature}=true/false</li>
 *   <li>{@code smithing}: sourceId = recipe id; tag = workstation id; attrs {@code output}, {@code tier}, {@code workstation}</li>
 *   <li>{@code fishing}: sourceId = loot item id; attr {@code rarity}</li>
 *   <li>{@code crafting}: sourceId = recipe/output id (stub shape)</li>
 *   <li>{@code combat}: sourceId = entity type id (stub shape)</li>
 * </ul>
 *
 * Every factory defaults {@code cause = PLAYER} (they take the acting player);
 * non-player producers use the {@code cause}-overloaded variants.
 */
public final class ActivityEvents {
	private ActivityEvents() {
	}

	public static ActivityEvent mining(ServerPlayerEntity player, Identifier blockId,
			@Nullable Identifier oreTier) {
		return mining(player, blockId, oreTier, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent mining(ServerPlayerEntity player, Identifier blockId,
			@Nullable Identifier oreTier, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.MINING, blockId,
				tags(oreTier), cause, now(),
				oreTier == null ? Map.of() : Map.of("tier", oreTier.toString()));
	}

	public static ActivityEvent farming(ServerPlayerEntity player, Identifier cropId, boolean mature) {
		return farming(player, cropId, mature, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent farming(ServerPlayerEntity player, Identifier cropId,
			boolean mature, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.FARMING, cropId,
				Set.of(LifepathMod.id(mature ? "mature" : "immature")),
				cause, now(), Map.of("mature", Boolean.toString(mature)));
	}

	public static ActivityEvent smithing(ServerPlayerEntity player, Identifier recipeId,
			@Nullable Identifier outputId, @Nullable String materialTier,
			@Nullable Identifier workstationId) {
		return smithing(player, recipeId, outputId, materialTier, workstationId,
				ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent smithing(ServerPlayerEntity player, Identifier recipeId,
			@Nullable Identifier outputId, @Nullable String materialTier,
			@Nullable Identifier workstationId, ActivityEvent.Cause cause) {
		Map<String, String> attrs = new LinkedHashMap<>();
		if (outputId != null) {
			attrs.put("output", outputId.toString());
		}
		if (materialTier != null) {
			attrs.put("tier", materialTier);
		}
		if (workstationId != null) {
			attrs.put("workstation", workstationId.toString());
		}
		return new ActivityEvent(player, ActivityTypes.SMITHING, recipeId,
				workstationId == null ? Set.of() : Set.of(workstationId),
				cause, now(), attrs);
	}

	public static ActivityEvent fishing(ServerPlayerEntity player, Identifier lootId,
			@Nullable String rarity) {
		return fishing(player, lootId, rarity, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent fishing(ServerPlayerEntity player, Identifier lootId,
			@Nullable String rarity, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.FISHING, lootId, Set.of(),
				cause, now(), rarity == null ? Map.of() : Map.of("rarity", rarity));
	}

	public static ActivityEvent crafting(ServerPlayerEntity player, Identifier recipeOrOutputId) {
		return crafting(player, recipeOrOutputId, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent crafting(ServerPlayerEntity player, Identifier recipeOrOutputId,
			ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.CRAFTING, recipeOrOutputId, Set.of(),
				cause, now(), Map.of());
	}

	public static ActivityEvent combat(ServerPlayerEntity player, Identifier entityTypeId) {
		return combat(player, entityTypeId, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent combat(ServerPlayerEntity player, Identifier entityTypeId,
			ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.COMBAT, entityTypeId, Set.of(),
				cause, now(), Map.of());
	}

	private static Set<Identifier> tags(@Nullable Identifier extra) {
		return extra == null ? Set.of() : Set.of(extra);
	}

	private static long now() {
		return System.currentTimeMillis();
	}
}
