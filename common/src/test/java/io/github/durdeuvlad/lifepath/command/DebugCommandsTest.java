package io.github.durdeuvlad.lifepath.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.CooldownService;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** M7-4: tree shape, permission gates, and the cooldown-clear semantics. */
class DebugCommandsTest {

	@BeforeEach
	void reset() {
		LifepathCommands.resetForTests();
		DebugCommands.resetForTests();
	}

	@Test
	void debugTreeHasThreeLeaves() {
		LifepathCommands.init();
		DebugCommands.init();

		CommandNode<CommandSourceStack> debug = root().getChild("debug");
		assertNotNull(debug, "missing /lifepath debug");
		assertNotNull(debug.getCommand(), "bare /lifepath debug should print usage");
		assertNotNull(debug.getChild("character"));
		assertNotNull(debug.getChild("ability"));
		assertNotNull(debug.getChild("skill"));
		// Every leaf requires a player arg.
		assertNotNull(debug.getChild("character").getChild("player"));
		assertNotNull(debug.getChild("ability").getChild("player"));
		assertNotNull(debug.getChild("skill").getChild("player"));
	}

	@Test
	void cooldownClearTreeHasOptionalAbilityArg() {
		LifepathCommands.init();
		DebugCommands.init();

		CommandNode<CommandSourceStack> clear =
				root().getChild("cooldown").getChild("clear");
		assertNotNull(clear);
		CommandNode<CommandSourceStack> playerArg = clear.getChild("player");
		assertNotNull(playerArg);
		assertNotNull(playerArg.getCommand(),
				"/lifepath cooldown clear <player> must clear ALL without an id");
		assertNotNull(playerArg.getChild("ability"),
				"missing optional [ability] arg");
	}

	@Test
	void debugAndCooldownArePermissionGated() {
		LifepathCommands.init();
		DebugCommands.init();
		// Gate predicates dereference the source — NPE proves a real
		// permission check, not a default allow.
		assertThrows(NullPointerException.class,
				() -> root().getChild("debug").canUse(null));
		assertThrows(NullPointerException.class,
				() -> root().getChild("cooldown").canUse(null));
	}

	@Test
	void clearAllRemovesCooldownsButKeepsScheduleMarkers() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "a"), 5000L);
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "b"), 6000L);
		data.setCooldown(CooldownService.scheduleKey(ResourceLocation.fromNamespaceAndPath("lifepath", "p")),
				7000L);

		int n = CooldownService.clearAll(data);

		assertEquals(2, n);
		assertFalse(data.cooldowns().containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "a")));
		assertFalse(data.cooldowns().containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "b")));
		// Schedule markers are engine bookkeeping — a cooldown clear must not
		// reset passive cadence.
		assertTrue(data.cooldowns()
				.containsKey(CooldownService.scheduleKey(ResourceLocation.fromNamespaceAndPath("lifepath", "p"))));
	}

	@Test
	void clearOneRemovesOnlyNamedCooldown() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "a"), 5000L);
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "b"), 6000L);

		assertTrue(CooldownService.clear(data, ResourceLocation.fromNamespaceAndPath("lifepath", "a")));
		assertFalse(CooldownService.clear(data, ResourceLocation.fromNamespaceAndPath("lifepath", "absent")));
		assertFalse(data.cooldowns().containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "a")));
		assertTrue(data.cooldowns().containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "b")));
		// A schedule key must never be clearable as if it were a cooldown.
		ResourceLocation marker =
				CooldownService.scheduleKey(ResourceLocation.fromNamespaceAndPath("lifepath", "p"));
		data.setCooldown(marker, 7000L);
		assertFalse(CooldownService.clear(data, marker));
		assertTrue(data.cooldowns().containsKey(marker));
	}

	@Test
	void modifierPipelineIsVisibleToDebug() {
		// SkillXpService.init registers the built-in chain; modifierIds must
		// expose the effective-modifier list for `debug character`.
		SkillXpService.init();
		assertTrue(SkillXpService.modifierIds()
				.contains(LifepathMod.id("diminishing_returns")));
	}

	private CommandNode<CommandSourceStack> root() {
		return new CommandDispatcher<CommandSourceStack>()
				.register(LifepathCommands.buildRoot());
	}
}
