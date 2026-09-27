package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Data definition of an unlock grant (M9-4, GAMEDESIGN §4.1/§16): when any
 * {@code source} rule fires, every id in {@code unlocks} lands in the
 * character's {@code unlocks[]} — the ids unlockable content checks (today:
 * species with {@code selection: "unlocked"}).
 *
 * <p>Sources:
 * <ul>
 *   <li>{@code {type:item, item, consume?, chance?}} — right-click ritual
 *       item (the {@code UseItemCallback} seam); consumed only on grant.
 *   <li>{@code {type:event, event:<activity-type>, chance?}} — each matching
 *       activity event rolls the chance.
 *   <li>{@code {type:advancement, advancement:<vanilla id>}} — completing the
 *       advancement grants (quest/narrative hook).
 *   <li>{@code {type:admin}} — command only.
 * </ul>
 */
public record UnlockDefinition(
		Identifier id,
		String displayName,
		Optional<String> description,
		List<Identifier> unlocks,
		List<SourceRule> sources) {

	public UnlockDefinition {
		unlocks = List.copyOf(unlocks);
		sources = List.copyOf(sources);
	}

	/**
	 * One way the grant fires. Optional fields apply per {@code type}:
	 * {@code item} + {@code consume} for {@code item}; {@code event} +
	 * {@code subject} (the event's {@code sourceId}, e.g. a killed entity
	 * type) + {@code tag} (must be in the event's tag set) + {@code chance}
	 * for {@code event}; {@code advancement} for {@code advancement}.
	 */
	public record SourceRule(String type, Optional<Identifier> item,
			Optional<Identifier> event, Optional<Identifier> subject,
			Optional<Identifier> tag, Optional<Identifier> advancement,
			double chance, boolean consume) {

		public static final Codec<SourceRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(SourceRule::type),
				Identifier.CODEC.optionalFieldOf("item").forGetter(SourceRule::item),
				Identifier.CODEC.optionalFieldOf("event").forGetter(SourceRule::event),
				Identifier.CODEC.optionalFieldOf("subject").forGetter(SourceRule::subject),
				Identifier.CODEC.optionalFieldOf("tag").forGetter(SourceRule::tag),
				Identifier.CODEC.optionalFieldOf("advancement").forGetter(SourceRule::advancement),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(SourceRule::chance),
				Codec.BOOL.optionalFieldOf("consume", true).forGetter(SourceRule::consume)
		).apply(instance, SourceRule::new));
	}

	public static UnlockDefinition fromFile(Identifier id, UnlockFile file) {
		return new UnlockDefinition(id, file.displayName(), file.description(),
				file.unlocks(), file.sources());
	}

	/** JSON shape of {@code data/<ns>/unlock/<name>.json} (id excluded). */
	public record UnlockFile(
			String displayName,
			Optional<String> description,
			List<Identifier> unlocks,
			List<SourceRule> sources) {

		public static final Codec<UnlockFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(UnlockFile::displayName),
				Codec.STRING.optionalFieldOf("description").forGetter(UnlockFile::description),
				Identifier.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(UnlockFile::unlocks),
				SourceRule.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(UnlockFile::sources)
		).apply(instance, UnlockFile::new));
	}
}
