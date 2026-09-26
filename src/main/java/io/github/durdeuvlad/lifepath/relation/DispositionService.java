package io.github.durdeuvlad.lifepath.relation;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.RelationDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Generic mob-disposition gate (M5-4): a species' optional
 * {@code mob_dispositions} def decides whether a mob's target predicate may
 * select this character. Consulted inside {@code TargetPredicate.test} — the
 * single predicate behind goal-driven targeting (active target selection,
 * revenge, defend). No rules → vanilla behavior.
 */
public final class DispositionService {
	private DispositionService() {
	}

	/** The species' relation def, or null when none is declared/loaded. */
	@Nullable
	public static RelationDefinition relationOf(@Nullable PlayerCharacterData data) {
		if (data == null || data.speciesId() == null) {
			return null;
		}
		SpeciesDefinition species = LifepathContent.species().get(data.speciesId());
		if (species == null || species.mobDispositions().isEmpty()) {
			return null;
		}
		return LifepathContent.relations().get(species.mobDispositions().get());
	}

	/**
	 * Should {@code mobType} be barred from targeting this character?
	 * {@code NEUTRAL}/{@code FRIENDLY} suppress; {@code HOSTILE} and
	 * unmatched rules leave vanilla targeting alone.
	 */
	public static boolean blocksTargeting(@Nullable PlayerCharacterData data,
			EntityType<?> mobType) {
		RelationDefinition def = relationOf(data);
		if (def == null) {
			return false;
		}
		Identifier typeId = Registries.ENTITY_TYPE.getId(mobType);
		var entry = Registries.ENTITY_TYPE.getEntry(mobType);
		var disposition = def.dispositionFor(typeId, entry);
		return disposition != null && disposition.suppressesTargeting();
	}
}
