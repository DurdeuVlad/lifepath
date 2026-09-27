package io.github.durdeuvlad.lifepath.hud;

import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Pure read-model for the M6-3 HUD overlay. Computes the visible rows from the
 * synced character snapshot + server-resolved identity display info; performs
 * no mutation and knows nothing about Minecraft rendering.
 *
 * <p>Relevance contract: an idle resource (value at rest and sitting in its
 * rest band) produces no row; a cooldown at or below zero produces no row. An
 * empty {@link View} means the renderer draws nothing — no permanent clutter.
 */
public final class HudModel {
	/** Resource values within this epsilon of the default count as "at rest". */
	static final double REST_EPSILON = 0.001;
	static final int MAX_COOLDOWN_ROWS = 8;
	static final int MAX_STATE_ROWS = 4;

	/**
	 * One visible resource row. {@code bandName} may be empty (unnamed band);
	 * {@code icon} is the def's icon ref ("" = none) for the row's badge.
	 */
	public record ResourceRow(String id, String label, double value, double min,
			double max, double fraction, String bandName, String icon) {
	}

	/**
	 * One visible cooldown row; {@code secondsLeft} is rounded up for display;
	 * {@code icon} is the ability's icon ref ("" = none).
	 */
	public record CooldownRow(String abilityId, String label,
			double secondsLeft, String icon) {
	}

	/**
	 * The full HUD view for one frame. {@code states} are the same
	 * {@code Entry} (id + name + icon) records the payload carries, so the
	 * renderer can badge each condition without re-resolving anything.
	 */
	public record View(List<ResourceRow> resources, List<CooldownRow> cooldowns,
			List<IdentitySummaryPayload.Entry> states) {
		public static final View EMPTY = new View(List.of(), List.of(), List.of());

		public boolean isEmpty() {
			return resources.isEmpty() && cooldowns.isEmpty() && states.isEmpty();
		}
	}

	private HudModel() {
	}

	/**
	 * @param identity server-resolved display info (never null — use
	 *        {@link IdentitySummaryPayload#empty()} before first sync)
	 * @param snapshot the synced character snapshot, or null pre-sync
	 * @param bandIndices resource id → current band index (-1/absent = none)
	 * @param nowMs wall-clock ms matching the cooldown expiry epoch-ms
	 */
	public static View compute(IdentitySummaryPayload identity,
			PlayerCharacterData snapshot,
			Map<Identifier, Integer> bandIndices, long nowMs) {
		if (snapshot == null || identity == null) {
			return View.EMPTY;
		}
		return new View(resourceRows(identity, snapshot, bandIndices),
				cooldownRows(identity, snapshot, nowMs),
				stateRows(identity));
	}

	private static List<ResourceRow> resourceRows(IdentitySummaryPayload identity,
			PlayerCharacterData snapshot, Map<Identifier, Integer> bandIndices) {
		List<ResourceRow> rows = new ArrayList<>();
		for (IdentitySummaryPayload.ResourceDisplay rd : identity.resourceDisplays()) {
			Identifier id;
			try {
				id = Identifier.of(rd.id());
			} catch (Exception e) {
				continue; // malformed id in payload — skip, never crash the HUD
			}
			PlayerCharacterData.ResourceState st = snapshot.resources().get(id);
			if (st == null) {
				continue; // resource defined but not materialized for this player
			}
			int band = bandIndices.getOrDefault(id, -1);
			boolean atRest = Math.abs(st.current() - rd.defaultValue()) <= REST_EPSILON
					&& (band < 0 || band == rd.restBandIndex());
			if (atRest) {
				continue;
			}
			double span = st.max() - st.min();
			double fraction = span > 0
					? clamp((st.current() - st.min()) / span) : 0;
			String bandName = band >= 0 && band < rd.bandNames().size()
					? rd.bandNames().get(band) : "";
			String label = rd.name().isEmpty() ? id.getPath() : rd.name();
			rows.add(new ResourceRow(rd.id(), label, st.current(), st.min(),
					st.max(), fraction, bandName, rd.icon()));
		}
		return rows;
	}

	private static List<CooldownRow> cooldownRows(IdentitySummaryPayload identity,
			PlayerCharacterData snapshot, long nowMs) {
		List<CooldownRow> rows = new ArrayList<>();
		for (Map.Entry<Identifier, Long> e : snapshot.cooldowns().entrySet()) {
			double left = (e.getValue() - nowMs) / 1000.0;
			if (left <= 0) {
				continue;
			}
			String idStr = e.getKey().toString();
			IdentitySummaryPayload.AbilityEntry ability =
					identity.abilities().get(idStr);
			String label = ability != null ? ability.name() : e.getKey().getPath();
			rows.add(new CooldownRow(idStr, label, left,
					ability != null ? ability.icon() : ""));
		}
		rows.sort(Comparator.comparingDouble(CooldownRow::secondsLeft));
		return rows.size() > MAX_COOLDOWN_ROWS
				? rows.subList(0, MAX_COOLDOWN_ROWS) : rows;
	}

	private static List<IdentitySummaryPayload.Entry> stateRows(
			IdentitySummaryPayload identity) {
		List<IdentitySummaryPayload.Entry> conditions = identity.sections()
				.getOrDefault(IdentitySummary.SECTION_CONDITIONS, List.of());
		return conditions.size() > MAX_STATE_ROWS
				? conditions.subList(0, MAX_STATE_ROWS) : conditions;
	}

	private static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
