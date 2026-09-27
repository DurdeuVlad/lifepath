package io.github.durdeuvlad.lifepath.event;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>{@code smithing}: sourceId = <b>output item id</b> (repetition signature keys on
 *       the produced item); tags = output item tags + workstation id +
 *       {@code lifepath:smithing_workstations}; attr {@code workstation} + extras</li>
 *   <li>{@code fishing}: sourceId = loot item id; tags = loot item tags
 *       (rarity classified by tags like {@code lifepath:fishing_treasure})</li>
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

	public static ActivityEvent mining(ServerPlayer player, ResourceLocation blockId,
			@Nullable ResourceLocation oreTier) {
		return mining(player, blockId, oreTier, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent mining(ServerPlayer player, ResourceLocation blockId,
			@Nullable ResourceLocation oreTier, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.MINING, blockId,
				tags(oreTier), cause, now(),
				oreTier == null ? Map.of() : Map.of("tier", oreTier.toString()));
	}

	public static ActivityEvent farming(ServerPlayer player, ResourceLocation cropId, boolean mature) {
		return farming(player, cropId, mature, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent farming(ServerPlayer player, ResourceLocation cropId,
			boolean mature, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.FARMING, cropId,
				Set.of(LifepathMod.id(mature ? "mature" : "immature")),
				cause, now(), Map.of("mature", Boolean.toString(mature)));
	}

	/**
	 * Canonical smithing event — the SAME shape vanilla producers and compat
	 * adapters must emit: sourceId = output item id, tags = output item tags
	 * + workstation id + {@code lifepath:smithing_workstations} marker (so
	 * xp_source files can gate on the workstation family). Extra descriptive
	 * attributes merge over the {@code workstation} attr.
	 */
	public static ActivityEvent smithing(ServerPlayer player, ResourceLocation outputId,
			Set<ResourceLocation> outputItemTags, ResourceLocation workstationId,
			Map<String, String> extraAttrs) {
		return smithing(player, outputId, outputItemTags, workstationId,
				extraAttrs, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent smithing(ServerPlayer player, ResourceLocation outputId,
			Set<ResourceLocation> outputItemTags, ResourceLocation workstationId,
			Map<String, String> extraAttrs, ActivityEvent.Cause cause) {
		Set<ResourceLocation> tags = new java.util.HashSet<>(outputItemTags);
		tags.add(workstationId);
		tags.add(LifepathMod.id("smithing_workstations"));
		Map<String, String> attrs = new LinkedHashMap<>(extraAttrs);
		attrs.put("workstation", workstationId.toString());
		return new ActivityEvent(player, ActivityTypes.SMITHING, outputId,
				Set.copyOf(tags), cause, now(), attrs);
	}

	/**
	 * Canonical fishing event — sourceId = caught item id, tags = caught item
	 * tags (rarity classification comes from tags like
	 * {@code lifepath:fishing_treasure}, not a string attr). Extras merge into
	 * attributes.
	 */
	public static ActivityEvent fishing(ServerPlayer player, ResourceLocation lootId,
			Set<ResourceLocation> lootTags, Map<String, String> extraAttrs) {
		return fishing(player, lootId, lootTags, extraAttrs, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent fishing(ServerPlayer player, ResourceLocation lootId,
			Set<ResourceLocation> lootTags, Map<String, String> extraAttrs,
			ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.FISHING, lootId,
				Set.copyOf(lootTags), cause, now(), Map.copyOf(extraAttrs));
	}

	public static ActivityEvent crafting(ServerPlayer player, ResourceLocation recipeOrOutputId) {
		return crafting(player, recipeOrOutputId, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent crafting(ServerPlayer player, ResourceLocation recipeOrOutputId,
			ActivityEvent.Cause cause) {
		return crafting(player, recipeOrOutputId, Set.of(), Map.of(), cause);
	}

	public static ActivityEvent crafting(ServerPlayer player, ResourceLocation recipeOrOutputId,
			Set<ResourceLocation> outputTags, Map<String, String> extraAttrs,
			ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.CRAFTING, recipeOrOutputId,
				Set.copyOf(outputTags), cause, now(), Map.copyOf(extraAttrs));
	}

	public static ActivityEvent combat(ServerPlayer player, ResourceLocation entityTypeId) {
		return combat(player, entityTypeId, ActivityEvent.Cause.PLAYER);
	}

	public static ActivityEvent combat(ServerPlayer player, ResourceLocation entityTypeId,
			ActivityEvent.Cause cause) {
		return combat(player, entityTypeId, Set.of(), cause);
	}

	/**
	 * Canonical kill event (M8-1) — sourceId = killed entity type id, tags =
	 * the victim's entity-type tags ({@code minecraft:skeletons},
	 * {@code lifepath:undead}, …) so xp_source data can weight by family.
	 */
	public static ActivityEvent combat(ServerPlayer player, ResourceLocation entityTypeId,
			Set<ResourceLocation> entityTags, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.COMBAT, entityTypeId,
				Set.copyOf(entityTags), cause, now(), Map.of());
	}

	/**
	 * Projectile kill (M8-3) — emitted in addition to {@link #combat} when the
	 * killing blow's damage source is a projectile. Same payload shape.
	 */
	public static ActivityEvent archery(ServerPlayer player, ResourceLocation entityTypeId,
			Set<ResourceLocation> entityTags, ActivityEvent.Cause cause) {
		return new ActivityEvent(player, ActivityTypes.ARCHERY, entityTypeId,
				Set.copyOf(entityTags), cause, now(), Map.of());
	}

	/**
	 * Hostile hit survived (M8-3) — sourceId/tags describe the ATTACKER's
	 * entity type; the event's player is the victim. Cause is
	 * {@code NON_PLAYER} — the damage was mob-caused, not player action.
	 */
	public static ActivityEvent defence(ServerPlayer victim, ResourceLocation attackerTypeId,
			Set<ResourceLocation> attackerTags, ActivityEvent.Cause cause) {
		return new ActivityEvent(victim, ActivityTypes.DEFENCE, attackerTypeId,
				Set.copyOf(attackerTags), cause, now(), Map.of());
	}

	private static Set<ResourceLocation> tags(@Nullable ResourceLocation extra) {
		return extra == null ? Set.of() : Set.of(extra);
	}

	private static long now() {
		return System.currentTimeMillis();
	}
}
