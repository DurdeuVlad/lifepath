package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.commands.Commands.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
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
		// source; reload's gate dereferences the source (hasPermissionLevel) and
		// therefore throws NPE on null — proving a real source-dependent gate,
		// not merely a different predicate instance.
		assertTrue(childNamed("version").canUse(null));
		assertThrows(NullPointerException.class, () -> childNamed("reload").canUse(null));
	}

	@Test
	void contributedSubcommandsJoinTheTree() {
		LifepathCommands.register(literal("alpha"));
		LifepathCommands.register(literal("beta"));

		CommandNode<CommandSourceStack> root = dispatchRoot();
		assertTrue(root.getChild("alpha") != null);
		assertTrue(root.getChild("beta") != null);
	}

	private CommandNode<CommandSourceStack> dispatchRoot() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		return dispatcher.register(LifepathCommands.buildRoot());
	}

	private CommandNode<CommandSourceStack> childNamed(String name) {
		CommandNode<CommandSourceStack> node = dispatchRoot().getChild(name);
		assertNotNull(node, "missing subcommand: " + name);
		return node;
	}
}
