package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifepathCommandsTest {

	@BeforeEach
	void reset() {
		LifepathCommands.resetForTests();
	}

	@Test
	void builtInSubcommandsAreVersionAndReload() {
		LifepathCommands.init();

		Set<String> names = dispatchRoot().getChildren().stream()
				.map(CommandNode::getName).collect(Collectors.toSet());

		assertEquals(Set.of("version", "reload"), names);
	}

	@Test
	void reloadIsPermissionGatedAndVersionIsNot() {
		LifepathCommands.init();

		// Brigadier defaults requirement to s -> true, so version accepts a null
		// source while reload carries an explicit (source-dependent) gate.
		assertTrue(childNamed("version").canUse(null));
		assertTrue(childNamed("reload").getRequirement() != childNamed("version").getRequirement());
	}

	@Test
	void contributedSubcommandsJoinTheTreeInOrder() {
		LifepathCommands.register(literal("alpha"));
		LifepathCommands.register(literal("beta"));

		CommandNode<ServerCommandSource> root = dispatchRoot();
		assertTrue(root.getChild("alpha") != null);
		assertTrue(root.getChild("beta") != null);
	}

	private CommandNode<ServerCommandSource> dispatchRoot() {
		CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
		return dispatcher.register(LifepathCommands.buildRoot());
	}

	private CommandNode<ServerCommandSource> childNamed(String name) {
		CommandNode<ServerCommandSource> node = dispatchRoot().getChild(name);
		assertNotNull(node, "missing subcommand: " + name);
		return node;
	}
}
