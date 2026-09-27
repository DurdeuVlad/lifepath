package io.github.durdeuvlad.lifepath.attunement;

import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AttunementDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime service for attunements (M9-2) — flat acquired affinities. Owns
 * all mutation of {@code PlayerCharacterData.attunements()} plus the
 * gameplay seams that evaluate acquisition/removal rules:
 * <ul>
 *   <li>{@link #onUseItem} — {@code type:item} acquisition (ritual
 *       right-click) and {@code type:item} removal.
 *   <li>{@link #onDamaged} — {@code type:damage} rules (matches the damage
 *       source type, e.g. a lightning strike with no attacker) and
 *       {@code type:attack} rules (matches the attacker entity).
 *   <li>{@link #onActivity} — {@code type:event} rules roll {@code chance}
 *       per matching activity event.
 * </ul>
 *
 * <p>Fail-closed throughout: unknown attunement ids, missing rules, or
 * absent content are no-ops — never throw for data problems.
 */
public final class AttunementService {
	private AttunementService() {
	}

	/** Grants the attunement. Idempotent; false on unknown id or already held. */
	public static boolean attune(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier attunementId) {
		if (LifepathContent.attunements().get(attunementId) == null
				|| data.attunements().contains(attunementId)) {
			return false;
		}
		data.addId(PlayerCharacterData.ListKind.ATTUNEMENTS, attunementId);
		mark(player);
		return true;
	}

	/** Removes the attunement. False on unknown id or not held. */
	public static boolean unattune(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier attunementId) {
		if (LifepathContent.attunements().get(attunementId) == null
				|| !data.attunements().contains(attunementId)) {
			return false;
		}
		data.removeId(PlayerCharacterData.ListKind.ATTUNEMENTS, attunementId);
		mark(player);
		return true;
	}

	/** Every ability id the held attunements grant. */
	public static List<Identifier> activeAbilities(PlayerCharacterData data) {
		List<Identifier> out = new java.util.ArrayList<>();
		for (Identifier id : data.attunements()) {
			AttunementDefinition def = LifepathContent.attunements().get(id);
			if (def != null) { // unloaded content — held id survives a reload
				out.addAll(def.abilities());
			}
		}
		return out;
	}

	/**
	 * {@code type:item} rules on a used (right-clicked) stack: removal first
	 * for held attunements, then acquisition for un-held ones. {@code
	 * consume} decrements the stack on a successful acquisition only.
	 */
	public static void onUseItem(ServerPlayerEntity player, ItemStack stack,
			long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null || stack.isEmpty()) {
			return;
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		for (Identifier id : List.copyOf(data.attunements())) {
			AttunementDefinition def = LifepathContent.attunements().get(id);
			if (def != null && def.removal().stream().anyMatch(r ->
					"item".equals(r.type()) && r.item().map(itemId::equals).orElse(false))) {
				unattune(data, player, id);
			}
		}
		for (AttunementDefinition def : LifepathContent.attunements().all().values()) {
			if (data.attunements().contains(def.id())) {
				continue;
			}
			for (AttunementDefinition.AcquisitionRule rule : def.acquisition()) {
				if (!"item".equals(rule.type())
						|| !rule.item().map(itemId::equals).orElse(false)) {
					continue;
				}
				if (attune(data, player, def.id()) && rule.consume()) {
					stack.decrement(1);
				}
				break;
			}
		}
	}

	/**
	 * Damage-source rules: {@code type:damage} matches the source type (id or
	 * {@code #tag} — covers attacker-less sources like lightning), then
	 * {@code type:attack} matches a living attacker's entity id/tag.
	 */
	public static void onDamaged(ServerPlayerEntity victim, DamageSource source,
			Random random, long nowMs) {
		PlayerCharacterData data = CharacterManager.getCharacter(victim);
		if (data == null) {
			return;
		}
		Entity attacker = source.getAttacker();
		for (AttunementDefinition def : LifepathContent.attunements().all().values()) {
			if (data.attunements().contains(def.id())) {
				continue;
			}
			for (AttunementDefinition.AcquisitionRule rule : def.acquisition()) {
				boolean hit = switch (rule.type()) {
					case "damage" -> rule.damageType().isPresent()
							&& damageMatches(source, rule.damageType().get());
					case "attack" -> rule.entity().isPresent()
							&& attacker instanceof LivingEntity
							&& entityMatches(attacker, rule.entity().get());
					default -> false;
				};
				if (hit && random.nextDouble() < rule.chance()) {
					attune(data, victim, def.id());
					break;
				}
			}
		}
	}

	/** {@code type:event} rules — each matching activity event rolls chance. */
	public static void onActivity(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			ActivityEvent event, long nowMs) {
		for (AttunementDefinition def : LifepathContent.attunements().all().values()) {
			if (data.attunements().contains(def.id())) {
				continue;
			}
			for (AttunementDefinition.AcquisitionRule rule : def.acquisition()) {
				if ("event".equals(rule.type())
						&& rule.event().map(event.type()::equals).orElse(false)) {
					Random random = player != null ? player.getWorld().getRandom()
							: Random.create();
					if (random.nextDouble() < rule.chance()) {
						attune(data, player, def.id());
					}
					break;
				}
			}
		}
	}

	private static boolean damageMatches(DamageSource source, String idOrTag) {
		if (idOrTag.startsWith("#")) {
			Identifier tagId = Identifier.tryParse(idOrTag.substring(1));
			return tagId != null && source.isIn(
					TagKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE, tagId));
		}
		Identifier id = Identifier.tryParse(idOrTag);
		return id != null && source.getTypeRegistryEntry().matchesId(id);
	}

	private static boolean entityMatches(Entity entity, String idOrTag) {
		if (idOrTag.startsWith("#")) {
			Identifier tagId = Identifier.tryParse(idOrTag.substring(1));
			return tagId != null && entity.getType().getRegistryEntry()
					.isIn(TagKey.of(Registries.ENTITY_TYPE.getKey(), tagId));
		}
		Identifier id = Identifier.tryParse(idOrTag);
		return id != null && Registries.ENTITY_TYPE.getId(entity.getType()).equals(id);
	}

	private static void mark(@Nullable ServerPlayerEntity player) {
		if (player != null) {
			CharacterManager.changed(player);
		}
	}
}
