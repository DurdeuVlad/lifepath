package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.reload.ReloadManager;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * The {@code /lifepath} command tree and its registration framework.
 *
 * <p>Convention: every Lifepath system contributes literal subcommand trees via
 * {@link #register(LiteralArgumentBuilder)} during mod init; {@link #init()}
 * attaches the whole tree under the {@code lifepath} root through Fabric's
 * {@code CommandRegistrationCallback}. Admin subcommands must declare
 * {@code requires(src -> src.hasPermissionLevel(2))} themselves. Handlers only
 * use {@link CommandSourceStack} APIs — never {@code getPlayer()} — so every
 * command also works from the dedicated-server console.
 */
public final class LifepathCommands {
	/** Brigadier admin permission level required by privileged subcommands. */
	public static final int ADMIN_PERMISSION = 2;

	private static final List<LiteralArgumentBuilder<CommandSourceStack>> SUBCOMMANDS = new ArrayList<>();
	private static boolean initialized;

	private LifepathCommands() {
	}

	/**
	 * Contributes a subcommand tree under {@code /lifepath}. Registration order is preserved.
	 *
	 * @throws IllegalArgumentException if another subcommand already claims the same
	 *         literal name (brigadier would silently merge them)
	 */
	public static void register(LiteralArgumentBuilder<CommandSourceStack> subcommand) {
		String name = subcommand.getLiteral();
		for (LiteralArgumentBuilder<CommandSourceStack> existing : SUBCOMMANDS) {
			if (existing.getLiteral().equals(name)) {
				throw new IllegalArgumentException("duplicate /lifepath subcommand literal: " + name);
			}
		}
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
				.requires(src -> src.hasPermission(ADMIN_PERMISSION))
				.executes(ctx -> reload(ctx.getSource())));

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> registerRoot(dispatcher));
	}

	private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(buildRoot());
	}

	private static int version(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"Lifepath " + LifepathMod.modVersion() + " (data version " + LifepathMod.DATA_VERSION + ")"),
				false);
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(CommandSourceStack source) {
		List<ReloadManager.ReloadResult> results =
				ReloadManager.reloadAll(source.getServer().getResourceManager());
		int failures = 0;
		for (ReloadManager.ReloadResult result : results) {
			if (result.success()) {
				source.sendSuccess(() -> Component.literal("reloaded " + result.id()), false);
			} else {
				failures++;
				source.sendFailure(Component.literal("FAILED " + result.id() + ": " + result.error()));
			}
		}
		int failureCount = failures;
		source.sendSuccess(() -> Component.literal(
				"Lifepath reload finished: " + results.size() + " reloadables, " + failureCount + " failed"),
				true);
		// M7-5: the content_validation reloader ran last — surface its grouped
		// report to the command source, not just the log.
		var report = io.github.durdeuvlad.lifepath.registry.LifepathContent
				.lastValidationReport();
		source.sendSuccess(() -> Component.literal(report.summaryLine()), true);
		for (String line : report.detailLines()) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return failureCount == 0 && !report.hasErrors() ? Command.SINGLE_SUCCESS : 0;
	}

	/** Test hook: clears contributed subcommands and the init flag. Not for production use. */
	static void resetForTests() {
		SUBCOMMANDS.clear();
		initialized = false;
	}

	/** Builds the root tree; bare {@code /lifepath} defaults to version output. */
	static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
		LiteralArgumentBuilder<CommandSourceStack> root = literal("lifepath")
				.executes(ctx -> version(ctx.getSource()));
		for (LiteralArgumentBuilder<CommandSourceStack> subcommand : new ArrayList<>(SUBCOMMANDS)) {
			root.then(subcommand);
		}
		return root;
	}
}
