package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * {@code /lifepath character} — admin tooling for character inspection and
 * reset (permission level 2+, works from the server console).
 *
 * <ul>
 *   <li>{@code inspect <player>} prints every persisted field of the target's
 *       character data — the same model the cache holds, so output is
 *       authoritative.
 *   <li>{@code reset <player> confirm} restores fresh defaults, persists
 *       immediately, re-syncs the client's mirror, and logs who reset whom.
 *       The literal {@code confirm} is required — bare {@code reset} refuses.
 * </ul>
 *
 * <p>v1 operates on ONLINE players only (entity-selector argument resolves
 * against connected players); offline editing is intentionally out of scope.
 */
public final class CharacterCommands {
	private static boolean initialized;

	private CharacterCommands() {
	}

	/** Contributes the {@code character} subcommand tree. Call during mod init; idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LifepathCommands.register(literal("character")
				.requires(src -> src.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("inspect")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> inspect(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player")))))
				.then(literal("reset")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> refuseReset(ctx.getSource()))
								.then(literal("confirm")
										.executes(ctx -> reset(ctx.getSource(),
												EntityArgumentType.getPlayer(ctx, "player")))))));
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath character inspect <player> | reset <player> confirm"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int inspect(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		source.sendFeedback(() -> Text.literal("Lifepath character: " + target.getName().getString()), false);
		for (Text line : describe(data, System.currentTimeMillis())) {
			source.sendFeedback(() -> line, false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int refuseReset(ServerCommandSource source) {
		source.sendError(Text.literal(
				"refusing: reset destroys all character data — append the literal 'confirm'"));
		return 0;
	}

	private static int reset(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		data.reset();
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} reset character data for {} ({})",
				source.getName(), target.getName().getString(), target.getUuid());
		source.sendFeedback(() -> Text.literal(
				"reset character data for " + target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Readable multi-line dump of every persisted field — kept pure (model +
	 * clock in, lines out) so tests assert exact output.
	 */
	static List<Text> describe(PlayerCharacterData data, long nowMillis) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal("  species: " + orNone(data.speciesId())
				+ "  specialization: " + orNone(data.specializationId())
				+ "  data_version: " + data.dataVersion()));
		if (data.skills().isEmpty()) {
			lines.add(Text.literal("  skills: <none>"));
		} else {
			for (Map.Entry<Identifier, PlayerCharacterData.SkillProgress> skill : data.skills().entrySet()) {
				PlayerCharacterData.SkillProgress p = skill.getValue();
				lines.add(Text.literal("  skill " + skill.getKey()
						+ "  level=" + p.level()
						+ "  xp=" + p.xp()
						+ "  highest=" + p.highestLevel()
						+ "  floor=" + p.protectedFloor()
						+ "  aptitude=" + p.aptitude()
						+ "  last_use=" + formatUse(p.lastMeaningfulUse())));
			}
		}
		lines.add(Text.literal("  traits: " + ids(data.traits())));
		lines.add(Text.literal("  conditions: " + ids(data.conditions())));
		lines.add(Text.literal("  attunements: " + ids(data.attunements())));
		lines.add(Text.literal("  unlocks: " + ids(data.unlocks())));
		if (data.resources().isEmpty()) {
			lines.add(Text.literal("  resources: <none>"));
		} else {
			for (Map.Entry<Identifier, PlayerCharacterData.ResourceState> res : data.resources().entrySet()) {
				PlayerCharacterData.ResourceState s = res.getValue();
				lines.add(Text.literal("  resource " + res.getKey()
						+ "  " + s.current() + "/" + s.min() + "-" + s.max()));
			}
		}
		if (data.cooldowns().isEmpty()) {
			lines.add(Text.literal("  cooldowns: <none>"));
		} else {
			for (Map.Entry<Identifier, Long> cd : data.cooldowns().entrySet()) {
				long remaining = cd.getValue() - nowMillis;
				lines.add(Text.literal("  cooldown " + cd.getKey()
						+ (remaining <= 0
								? "  expired " + (-remaining) + "ms ago"
								: "  expires in " + remaining + "ms")));
			}
		}
		return List.copyOf(lines);
	}

	private static String orNone(@org.jetbrains.annotations.Nullable Identifier id) {
		return id == null ? "<none>" : id.toString();
	}

	/** Test hook: clears the init flag so {@link #init()} re-registers. Not for production use. */
	static void resetForTests() {
		initialized = false;
	}

	private static String ids(List<Identifier> list) {
		return list.isEmpty() ? "<none>" : list.toString();
	}

	private static String formatUse(long epochMillis) {
		return epochMillis <= 0 ? "<never>" : Instant.ofEpochMilli(epochMillis).toString();
	}
}
