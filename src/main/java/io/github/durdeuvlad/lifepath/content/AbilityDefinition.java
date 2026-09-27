package io.github.durdeuvlad.lifepath.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One data-driven ability (M4-1, GAMEDESIGN §12): a pure composition of
 * {@code trigger + conditions + target + actions + cost? + cooldown? +
 * resource interactions}. There is NO ability-specific Java — new mechanics
 * extend the condition/action/target vocabularies, not this record.
 *
 * <p>Spec nodes ({@link SpecNode}) keep their raw JsonObject so M4-2/M4-3
 * vocabularies can declare arbitrary fields without schema churn here.
 */
public record AbilityDefinition(
		ResourceLocation id,
		String displayName,
		boolean enabled,
		Trigger trigger,
		ConditionSet conditions,
		SpecNode target,
		List<SpecNode> actions,
		Optional<Cost> cost,
		Optional<Cooldown> cooldown,
		List<ResourceInteraction> resourceInteractions,
		Optional<ResourceLocation> icon) {

	/** Back-compatible constructor for call sites predating {@code icon} (M12-1). */
	public AbilityDefinition(ResourceLocation id, String displayName, boolean enabled,
			Trigger trigger, ConditionSet conditions, SpecNode target,
			List<SpecNode> actions, Optional<Cost> cost,
			Optional<Cooldown> cooldown, List<ResourceInteraction> resourceInteractions) {
		this(id, displayName, enabled, trigger, conditions, target, actions,
				cost, cooldown, resourceInteractions, Optional.empty());
	}

	public static AbilityDefinition fromFile(ResourceLocation id, AbilityFile file) {
		return new AbilityDefinition(id, file.displayName(), file.enabled(),
				file.trigger(), file.conditions(), file.target(), file.actions(),
				file.cost(), file.cooldown(), file.resourceInteractions(),
				file.icon().map(raw -> IconRef.resolve("ability", id, raw)));
	}

	/** Trigger kinds — all four are load-bearing, see {@link Kind}. */
	public enum Kind {
		/** Re-evaluated at {@code interval_ticks} per player. */
		PASSIVE,
		/** Player-triggered via keybind → C2S; server re-validates everything. */
		ACTIVE,
		/** Fires when an {@link #events} activity type is dispatched. */
		EVENT,
		/**
		 * Incoming-damage modifier (M5-2): evaluated synchronously inside the
		 * player's {@code damage()} entry — conditions see the attacker/source/
		 * base amount via {@code EvalContext.damage()}, and passing conditions
		 * scale the amount by {@code multiplier}. Target/actions/cost/cooldown/
		 * resource_interactions do not apply (warned at load) — re-running
		 * actions mid-damage risks recursion.
		 */
		DAMAGE_TAKEN;

		public static final Codec<Kind> CODEC = Codec.STRING.xmap(
				s -> Kind.valueOf(s.toUpperCase(Locale.ROOT)),
				k -> k.name().toLowerCase(Locale.ROOT));
	}

	public record Trigger(Kind kind, int intervalTicks, List<ResourceLocation> events,
			double multiplier) {
		public static final Codec<Trigger> CODEC = RecordCodecBuilder.create(i -> i.group(
				Kind.CODEC.fieldOf("type").forGetter(Trigger::kind),
				Codec.intRange(1, Integer.MAX_VALUE)
						.optionalFieldOf("interval_ticks", 20).forGetter(Trigger::intervalTicks),
				ResourceLocation.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Trigger::events),
				Codec.doubleRange(0.0, 100.0)
						.optionalFieldOf("multiplier", 1.0).forGetter(Trigger::multiplier)
		).apply(i, Trigger::new));
	}

	/** All-of AND any-of composition: every {@code all} plus at least one {@code any} (when present). */
	public record ConditionSet(List<SpecNode> all, List<SpecNode> any) {
		public static final ConditionSet NONE = new ConditionSet(List.of(), List.of());

		public static final Codec<ConditionSet> CODEC = RecordCodecBuilder.create(i -> i.group(
				SpecNode.CODEC.listOf().optionalFieldOf("all", List.of()).forGetter(ConditionSet::all),
				SpecNode.CODEC.listOf().optionalFieldOf("any", List.of()).forGetter(ConditionSet::any)
		).apply(i, ConditionSet::new));
	}

	/**
	 * A typed spec node: {@code {"type": "<id>", ...params}}. The raw object is
	 * preserved verbatim so condition/action/target evaluators read their own
	 * parameters — the engine never interprets content.
	 */
	public record SpecNode(ResourceLocation type, JsonObject raw) {
		public static final Codec<SpecNode> CODEC = Codec.PASSTHROUGH.flatXmap(
				dyn -> {
					JsonElement el = dyn.convert(JsonOps.INSTANCE).getValue();
					if (!el.isJsonObject()) {
						return DataResult.error(() -> "spec node must be an object");
					}
					JsonObject raw = el.getAsJsonObject();
					JsonElement typeEl = raw.get("type");
					if (typeEl == null || !typeEl.isJsonPrimitive()
							|| !typeEl.getAsJsonPrimitive().isString()) {
						return DataResult.error(() -> "spec node needs a string 'type' field");
					}
					ResourceLocation id = ResourceLocation.tryParse(typeEl.getAsString());
					if (id == null) {
						return DataResult.error(() -> "bad spec node type " + typeEl);
					}
					return DataResult.success(new SpecNode(id, raw));
				},
				node -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, node.raw())));
	}

	/**
	 * Finite-positive double — {@code Codec.doubleRange} alone admits NaN
	 * (every comparison fails), which would poison a persisted resource.
	 */
	private static final Codec<Double> POSITIVE_DOUBLE = Codec.DOUBLE.validate(
			d -> Double.isFinite(d) && d > 0
					? DataResult.success(d)
					: DataResult.error(() -> "value must be finite and > 0"));
	private static final Codec<Double> FINITE_DOUBLE = Codec.DOUBLE.validate(
			d -> Double.isFinite(d)
					? DataResult.success(d)
					: DataResult.error(() -> "value must be finite"));

	/** Resource gate: {@code current >= amount} required, then spent. */
	public record Cost(ResourceLocation resource, double amount) {
		public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("resource").forGetter(Cost::resource),
				POSITIVE_DOUBLE.fieldOf("amount").forGetter(Cost::amount)
		).apply(i, Cost::new));
	}

	/** Cooldown in seconds (must be > 0; omit the field for no cooldown). */
	public record Cooldown(double seconds) {
		public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
				POSITIVE_DOUBLE.fieldOf("seconds").forGetter(Cooldown::seconds)
		).apply(i, Cooldown::new));
	}

	/** Passive resource drain/regen seam (applied per passive evaluation). */
	public record ResourceInteraction(ResourceLocation resource, double perSecond) {
		public static final Codec<ResourceInteraction> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("resource").forGetter(ResourceInteraction::resource),
				FINITE_DOUBLE.fieldOf("per_second").forGetter(ResourceInteraction::perSecond)
		).apply(i, ResourceInteraction::new));
	}

	/**
	 * JSON shape of {@code data/<ns>/ability/<name>.json} (id excluded).
	 * {@code enabled=false} keeps the definition registered — references
	 * resolve, ownership counts — but the engine returns DISABLED on every
	 * trigger path. Full schema: {@code docs/ABILITIES.md}.
	 */
	public record AbilityFile(
			String displayName,
			boolean enabled,
			Trigger trigger,
			ConditionSet conditions,
			SpecNode target,
			List<SpecNode> actions,
			Optional<Cost> cost,
			Optional<Cooldown> cooldown,
			List<ResourceInteraction> resourceInteractions,
			Optional<String> icon) {

		public static final Codec<AbilityFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("display_name").forGetter(AbilityFile::displayName),
				Codec.BOOL.optionalFieldOf("enabled", true).forGetter(AbilityFile::enabled),
				Trigger.CODEC.fieldOf("trigger").forGetter(AbilityFile::trigger),
				ConditionSet.CODEC.optionalFieldOf("conditions", ConditionSet.NONE)
						.forGetter(AbilityFile::conditions),
				SpecNode.CODEC.fieldOf("target").forGetter(AbilityFile::target),
				SpecNode.CODEC.listOf().optionalFieldOf("actions", List.of())
						.forGetter(AbilityFile::actions),
				Cost.CODEC.optionalFieldOf("cost").forGetter(AbilityFile::cost),
				Cooldown.CODEC.optionalFieldOf("cooldown").forGetter(AbilityFile::cooldown),
				ResourceInteraction.CODEC.listOf()
						.optionalFieldOf("resource_interactions", List.of())
						.forGetter(AbilityFile::resourceInteractions),
				Codec.STRING.optionalFieldOf("icon").forGetter(AbilityFile::icon)
		).apply(i, AbilityFile::new));
	}
}
