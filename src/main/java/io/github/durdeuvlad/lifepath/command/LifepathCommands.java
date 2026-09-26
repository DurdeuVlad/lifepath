package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.reload.ReloadManager;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * The {@code /lifepath} command tree and its registration framework.
 *
 * <p>Convention: every Lifepath system contributes literal subcommand trees via
 * {@link #register(LiteralArgumentBuilder)} during mod init; {@link #init()}
 * attaches the whole tree under the {@code lifepath} root through Fabric's
 * {@code CommandRegistrationCallback}. Admin subcommands must declare
 * {@code requires(src -> src.hasPermissionLevel(2))} themselves. Handlers only
 * use {@link ServerCommandSource} APIs — never {@code getPlayer()} — so every
 * command also works from the dedicated-server console.
 */
public final class LifepathCommands {
	/** Brigadier admin permission level required by privileged subcommands. */
	public static final int ADMIN_PERMISSION = 2;

	private static final List<LiteralArgumentBuilder<ServerCommandSource>> SUBCOMMANDS = new ArrayList<>();
	private static boolean initialized;

	private LifepathCommands() {
	}

	/** Contributes a subcommand tree under {@code /lifepath}. Registration order is preserved. */
	public static void register(LiteralArgumentBuilder<ServerCommandSource> subcommand) {
		SUBCOMMANDS.add(subcommand);
	}

	/** Registers built-ins and hooks the brigadier root into command registration. Call once at mod init. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		register(literal("version").executes(ctx -> version(ctx.getSource())));
		register(literal("reload")
				.requires(src -> src.hasPermissionLevel(ADMIN_PERMISSION))
				.executes(ctx -> reload(ctx.getSource())));

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> registerRoot(dispatcher));
	}

	private static void registerRoot(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(buildRoot());
	}

	private static int version(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"Lifepath " + LifepathMod.modVersion() + " (data version " + LifepathMod.DATA_VERSION + ")"),
				false);
		return 1;
	}

	private static int reload(ServerCommandSource source) {
		List<ReloadManager.ReloadResult> results = ReloadManager.reloadAll();
		int failures = 0;
		for (ReloadManager.ReloadResult result : results) {
			if (result.success()) {
				source.sendFeedback(() -> Text.literal("reloaded " + result.id()), false);
			} else {
				failures++;
				source.sendError(Text.literal("FAILED " + result.id() + ": " + result.error()));
			}
		}
		int failureCount = failures;
		source.sendFeedback(() -> Text.literal(
				"Lifepath reload finished: " + results.size() + " reloadables, " + failureCount + " failed"),
				false);
		return failureCount == 0 ? 1 : 0;
	}

	/** Test hook: clears contributed subcommands and the init flag. Not for production use. */
	static void resetForTests() {
		SUBCOMMANDS.clear();
		initialized = false;
	}

	/** Test hook: rebuilds the root tree as {@link #registerRoot} would. */
	static LiteralArgumentBuilder<ServerCommandSource> buildRoot() {
		LiteralArgumentBuilder<ServerCommandSource> root = literal("lifepath");
		for (LiteralArgumentBuilder<ServerCommandSource> subcommand : SUBCOMMANDS) {
			root.then(subcommand);
		}
		return root;
	}
}
