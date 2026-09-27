package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Data definition of an attunement (M9-2, GAMEDESIGN §16) — an elemental
 * affinity ACQUIRED in play, architecturally distinct from Species (what you
 * ARE). Attunements are flat: no stages, no counters, no cure chains — the
 * persisted model is the plain {@code List<Identifier>} the character data
 * already carries.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code abilities} — ability refs owned while attuned.
 *   <li>{@code acquisition} — {@code {type:item, item, consume?, chance?}} |
 *       {@code {type:damage, damage_type:<id|#tag>, chance?}} |
 *       {@code {type:attack, entity:<id|#tag>, chance?}} |
 *       {@code {type:event, event:<activity-type>, chance?}} |
 *       {@code {type:admin}}.
 *   <li>{@code removal} — {@code {type:item, item}} | {@code {type:admin}}.
 *       Admin removal is always allowed via command even when unlisted.
 * </ul>
 */
public record AttunementDefinition(
		Identifier id,
		String displayName,
		Optional<String> description,
		List<Identifier> abilities,
		List<AcquisitionRule> acquisition,
		List<RemovalRule> removal) {

	public AttunementDefinition {
		abilities = List.copyOf(abilities);
		acquisition = List.copyOf(acquisition);
		removal = List.copyOf(removal);
	}

	/**
	 * How the attunement is gained. {@code type}: {@code item} (ritual
	 * right-click with the item, optionally consumed), {@code damage} (take
	 * damage of a matching type), {@code attack} (hostile hit from a matching
	 * entity), {@code event} (each matching activity event rolls {@code
	 * chance}), {@code admin} (command only).
	 */
	public record AcquisitionRule(String type, Optional<Identifier> item,
			Optional<String> damageType, Optional<String> entity,
			Optional<Identifier> event, double chance, boolean consume) {

		public static final Codec<AcquisitionRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(AcquisitionRule::type),
				Identifier.CODEC.optionalFieldOf("item").forGetter(AcquisitionRule::item),
				Codec.STRING.optionalFieldOf("damage_type").forGetter(AcquisitionRule::damageType),
				Codec.STRING.optionalFieldOf("entity").forGetter(AcquisitionRule::entity),
				Identifier.CODEC.optionalFieldOf("event").forGetter(AcquisitionRule::event),
				Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(AcquisitionRule::chance),
				Codec.BOOL.optionalFieldOf("consume", true).forGetter(AcquisitionRule::consume)
		).apply(instance, AcquisitionRule::new));
	}

	/** How the attunement is lost. {@code type}: {@code item} | {@code admin}. */
	public record RemovalRule(String type, Optional<Identifier> item) {

		public static final Codec<RemovalRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(RemovalRule::type),
				Identifier.CODEC.optionalFieldOf("item").forGetter(RemovalRule::item)
		).apply(instance, RemovalRule::new));
	}

	public static AttunementDefinition fromFile(Identifier id, AttunementFile file) {
		return new AttunementDefinition(id, file.displayName(), file.description(),
				file.abilities(), file.acquisition(), file.removal());
	}

	/** JSON shape of {@code data/<ns>/attunement/<name>.json} (id excluded). */
	public record AttunementFile(
			String displayName,
			Optional<String> description,
			List<Identifier> abilities,
			List<AcquisitionRule> acquisition,
			List<RemovalRule> removal) {

		public static final Codec<AttunementFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(AttunementFile::displayName),
				Codec.STRING.optionalFieldOf("description").forGetter(AttunementFile::description),
				Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(AttunementFile::abilities),
				AcquisitionRule.CODEC.listOf().optionalFieldOf("acquisition", List.of()).forGetter(AttunementFile::acquisition),
				RemovalRule.CODEC.listOf().optionalFieldOf("removal", List.of()).forGetter(AttunementFile::removal)
		).apply(instance, AttunementFile::new));
	}
}
