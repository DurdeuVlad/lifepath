package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Skill-unlock gate: binds registry TAG ids to the minimum skill level an
 * action needs. One file per skill under {@code data/<ns>/gate/<name>.json}
 * (id e.g. {@code lifepath:mining}); the file's {@code skill} field is
 * authoritative — the file name is just the registry id.
 *
 * <pre>
 * {
 *   "skill": "lifepath:mining",
 *   "break":    {"minecraft:mineable/pickaxe": 10},
 *   "use":      {"lifepath:steel_tools": 30},
 *   "attack":   {"lifepath:war_bows": 20},
 *   "interact": {"minecraft:skeletons": 40},
 *   "equip":    {"lifepath:armor_tier_diamond": 40}
 * }
 * </pre>
 *
 * <p>The five action maps are optional and independent: {@code break} keys
 * are block tags, {@code use}/{@code attack}/{@code equip} item tags,
 * {@code interact} entity_type tags; values are required levels in [1,100].
 * {@code equip} denies placement into an armour/equipment slot — list the
 * same tags under {@code use} too if right-click-equip should also be
 * refused (the {@code use} gate is what intercepts that path). A key is a
 * <b>tag</b> id, never an entry id — untagged targets are always free, and
 * an unbound tag simply never matches (fail-closed at check time, the same
 * laziness registry-backed refs get in material_gates — vanilla registries
 * are not guaranteed available at datapack-decode time). A file declaring
 * none of the maps is dead content and fails decode.
 */
public record GateDefinition(
		ResourceLocation id,
		ResourceLocation skill,
		Map<ResourceLocation, Integer> breakGates,
		Map<ResourceLocation, Integer> useGates,
		Map<ResourceLocation, Integer> attackGates,
		Map<ResourceLocation, Integer> interactGates,
		Map<ResourceLocation, Integer> equipGates) {

	public static GateDefinition fromFile(ResourceLocation id, GateFile file) {
		if (file.breakGates().isEmpty() && file.useGates().isEmpty()
				&& file.attackGates().isEmpty() && file.interactGates().isEmpty()
				&& file.equipGates().isEmpty()) {
			throw new IllegalArgumentException(
					"gate " + id + " declares no action gates — it can never match");
		}
		return new GateDefinition(id, file.skill(),
				ordered(file.breakGates()), ordered(file.useGates()),
				ordered(file.attackGates()), ordered(file.interactGates()),
				ordered(file.equipGates()));
	}

	/** Unmodifiable but order-preserving — first-match-wins resolution must
	 * see the JSON's declared entry order, not {@code Map.copyOf}'s salted
	 * iteration order, or overlapping tags resolve per-launch. */
	private static Map<ResourceLocation, Integer> ordered(
			Map<ResourceLocation, Integer> map) {
		return java.util.Collections.unmodifiableMap(
				new java.util.LinkedHashMap<>(map));
	}

	/** JSON shape of {@code data/<ns>/gate/<name>.json} (id excluded). */
	public record GateFile(
			ResourceLocation skill,
			Map<ResourceLocation, Integer> breakGates,
			Map<ResourceLocation, Integer> useGates,
			Map<ResourceLocation, Integer> attackGates,
			Map<ResourceLocation, Integer> interactGates,
			Map<ResourceLocation, Integer> equipGates) {

		public static final Codec<GateFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("skill").forGetter(GateFile::skill),
				gateMap().optionalFieldOf("break", Map.of()).forGetter(GateFile::breakGates),
				gateMap().optionalFieldOf("use", Map.of()).forGetter(GateFile::useGates),
				gateMap().optionalFieldOf("attack", Map.of()).forGetter(GateFile::attackGates),
				gateMap().optionalFieldOf("interact", Map.of()).forGetter(GateFile::interactGates),
				gateMap().optionalFieldOf("equip", Map.of()).forGetter(GateFile::equipGates))
				.apply(i, GateFile::new));

		/** {@code {"<tag id>": <required level 1..100>}} — keys decode as plain
		 * {@code ns:path} ids (no {@code #} prefix; position declares them tags). */
		private static Codec<Map<ResourceLocation, Integer>> gateMap() {
			return Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 100));
		}
	}
}
