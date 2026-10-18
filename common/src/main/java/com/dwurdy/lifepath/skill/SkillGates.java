package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.GateDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Skill-unlock gate check shared by the vanilla action seams (block break,
 * item use, attack damage, entity interact). Each
 * {@code data/<ns>/gate/*.json} file binds registry tag ids to a minimum
 * level in the file's skill; an action on a tagged target is denied while
 * the player's level in that skill is below the gate. An untagged target is
 * always free — as are creative/spectator players and players whose
 * character can't be resolved.
 *
 * <p>The server is authoritative. Client calls resolve the synced character
 * snapshot via the source installed by
 * {@link #setClientCharacterSource} purely to PREVIEW the denial —
 * {@link #breakSpeedFactor} feeds the client's own mining-speed math so
 * feedback doesn't lag a round-trip; the server re-checks every action.
 */
public final class SkillGates {

	private SkillGates() {
	}

	/**
	 * A failed gate: the skill the file demands, the level it requires, and
	 * which action was checked ({@code "break"}, {@code "use"},
	 * {@code "attack"}, {@code "interact"}).
	 */
	public record Denial(ResourceLocation skill, int requiredLevel, String action) {
	}

	/** Mining-speed multiplier applied while a {@code break} gate denies the block. */
	public static final float BREAK_SPEED_FACTOR = 0.35f;

	/** Damage cap for a swing whose weapon an {@code attack} gate denies. */
	public static final float FEEBLE_DAMAGE_CAP = 1.0f;

	/** The first {@code break} gate this block fails, or {@code null} when
	 * mining it is allowed (untagged block, sufficient level, or nothing to
	 * enforce against). */
	@Nullable
	public static Denial denialForBreak(@Nullable Player player, @Nullable BlockState state) {
		return state == null ? null
				: scan(player, "break", GateDefinition::breakGates,
						tag -> state.is(TagKey.create(Registries.BLOCK, tag)));
	}

	/** {@link #BREAK_SPEED_FACTOR} while a break gate applies, else 1.0. */
	public static float breakSpeedFactor(@Nullable Player player, @Nullable BlockState state) {
		return denialForBreak(player, state) != null ? BREAK_SPEED_FACTOR : 1.0f;
	}

	/** The first {@code use} gate this stack fails, or {@code null} when the
	 * use is allowed. */
	@Nullable
	public static Denial denialForUse(@Nullable Player player, @Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null
				: scan(player, "use", GateDefinition::useGates,
						tag -> stack.is(TagKey.create(Registries.ITEM, tag)));
	}

	/** The first {@code attack} gate this stack fails, or {@code null} when
	 * the swing lands normally — the caller clamps the damage to
	 * {@link #FEEBLE_DAMAGE_CAP} on a denial. */
	@Nullable
	public static Denial denialForAttack(@Nullable Player player, @Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null
				: scan(player, "attack", GateDefinition::attackGates,
						tag -> stack.is(TagKey.create(Registries.ITEM, tag)));
	}

	/** The first {@code interact} gate this entity type fails, or
	 * {@code null} when the interaction is allowed. */
	@Nullable
	public static Denial denialForInteract(@Nullable Player player, @Nullable Entity target) {
		return target == null ? null
				: scan(player, "interact", GateDefinition::interactGates,
						tag -> target.getType().builtInRegistryHolder()
								.is(TagKey.create(Registries.ENTITY_TYPE, tag)));
	}

	/** The first {@code equip} gate this stack fails, or {@code null} when
	 * slot placement is allowed. Right-click-equip is a separate path —
	 * datapacks should also list armour tags under {@code use}. */
	@Nullable
	public static Denial denialForEquip(@Nullable Player player, @Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null
				: scan(player, "equip", GateDefinition::equipGates,
						tag -> stack.is(TagKey.create(Registries.ITEM, tag)));
	}

	/**
	 * First-match-wins scan over every registered gate file's {@code map}:
	 * the first tag the target matches resolves the player's level in that
	 * file's skill — below it, the denial lands; at or above it, the action
	 * is allowed (same contract as {@code MaterialGates}: a sufficient
	 * level licenses the action, later gates are never consulted).
	 */
	@Nullable
	private static Denial scan(@Nullable Player player, String action,
			Function<GateDefinition, Map<ResourceLocation, Integer>> map,
			Predicate<ResourceLocation> tagHit) {
		if (unenforceable(player)) {
			return null;
		}
		for (GateDefinition gate : LifepathContent.gates().all().values()) {
			for (var entry : map.apply(gate).entrySet()) {
				if (tagHit.test(entry.getKey())) {
					int required = entry.getValue();
					return levelOf(player, gate.skill()) < required
							? new Denial(gate.skill(), required, action) : null;
				}
			}
		}
		return null;
	}

	/** The character's level in {@code skill}; absent progress is level 0. */
	private static int levelOf(Player player, ResourceLocation skill) {
		PlayerCharacterData data = characterFor(player);
		if (data == null) {
			return 0;
		}
		SkillProgress progress = SkillService.progress(data, skill);
		return progress == null ? 0 : progress.level();
	}

	/**
	 * The player's character data, or {@code null} when none resolves:
	 * server players read the live model via {@link CharacterManager};
	 * client players read the synced snapshot mirror (once wired by the
	 * client bootstrap); anything else — a non-ServerPlayer on the server,
	 * an automation actor — is unresolvable.
	 */
	@Nullable
	private static PlayerCharacterData characterFor(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			return CharacterManager.getCharacter(serverPlayer);
		}
		if (player.level().isClientSide()) {
			return clientCharacter.get();
		}
		return null;
	}

	/** Gates never apply to no player, to creative/spectator mode, or to a
	 * character that can't be resolved — fail-open, the server re-checks. */
	private static boolean unenforceable(@Nullable Player player) {
		return player == null || player.isCreative() || player.isSpectator()
				|| characterFor(player) == null;
	}

	/**
	 * Client-side character source: main can't see the client sourceset's
	 * synced snapshot, so the client bootstrap installs it — wired once to
	 * {@code ClientCharacterState::snapshot}. Unset (dedicated server) it
	 * resolves nothing, and client-side checks are simply never gated.
	 */
	private static volatile Supplier<PlayerCharacterData> clientCharacter = () -> null;

	public static void setClientCharacterSource(Supplier<PlayerCharacterData> source) {
		clientCharacter = source;
	}

	private static final long DENY_MESSAGE_COOLDOWN_TICKS = 40;
	private static final Map<UUID, Long> denyMessageTicks = new HashMap<>();

	/** Rate-limited per player so per-tick checks — hold-to-mine, repeated
	 * swings — can't spam the actionbar. */
	public static void notifyDenied(ServerPlayer player, Denial denial) {
		long now = player.level().getGameTime();
		Long last = denyMessageTicks.get(player.getUUID());
		if (last != null && now - last < DENY_MESSAGE_COOLDOWN_TICKS) {
			return;
		}
		if (denyMessageTicks.size() > 256) {
			denyMessageTicks.clear();
		}
		denyMessageTicks.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable("lifepath.gate.denied",
				denial.skill().getPath(), denial.requiredLevel(), denial.action()), true);
	}
}
