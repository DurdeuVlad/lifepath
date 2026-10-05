package com.dwurdy.lifepath.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.skill.Aptitude;
import com.dwurdy.lifepath.character.PlayerCharacterData.ResourceState;
import com.dwurdy.lifepath.skill.SkillProgress;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CharacterCommandsTest {

	@BeforeEach
	void reset() {
		LifepathCommands.resetForTests();
		CharacterCommands.resetForTests();
	}

	@Test
	void characterTreeHasInspectResetAndConfirm() {
		LifepathCommands.init();
		CharacterCommands.init();

		CommandNode<CommandSourceStack> character = root().getChild("character");
		assertNotNull(character, "missing /lifepath character");
		assertNotNull(character.getCommand(), "bare /lifepath character should print usage");
		assertNotNull(character.getChild("inspect"));
		CommandNode<CommandSourceStack> resetNode = character.getChild("reset");
		assertNotNull(resetNode);
		// reset -> <player arg> -> "confirm" literal; the arg node itself must
		// carry the refuseReset executor or `reset <player>` would fail unexplained.
		CommandNode<CommandSourceStack> playerArg = resetNode.getChild("player");
		assertNotNull(playerArg);
		assertNotNull(playerArg.getCommand(), "reset without confirm must refuse explicitly");
		assertNotNull(playerArg.getChild("confirm"), "reset must require literal confirm");
	}

	@Test
	void characterTreeIsPermissionGated() {
		LifepathCommands.init();
		CharacterCommands.init();

		// The gate predicate dereferences the source (hasPermissionLevel), so a
		// null source throws NPE — proving a real source-dependent gate, not a
		// default s -> true (see LifepathCommandsTest for the same pattern).
		assertThrows(NullPointerException.class, () -> root().getChild("character").canUse(null));
	}

	@Test
	void describePrintsEveryPersistedField() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"));
		data.setSpecializationId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_spec"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "s"),
				new SkillProgress(12.5, 3, 4, 2, Aptitude.A, 1_700_000_000_000L));
		data.addId(PlayerCharacterData.ListKind.TRAITS, ResourceLocation.fromNamespaceAndPath("lifepath", "t1"));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, ResourceLocation.fromNamespaceAndPath("lifepath", "u1"));
		data.setResource(ResourceLocation.fromNamespaceAndPath("lifepath", "mana"), new ResourceState(5.0, 0.0, 10.0));
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "ab"), 1500L);

		List<Component> lines = CharacterCommands.describe(data, 1000L);
		String all = lines.stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);

		assertTrue(all.contains("lifepath:test_species"));
		assertTrue(all.contains("lifepath:test_spec"));
		assertTrue(all.contains("level=3"));
		assertTrue(all.contains("xp=12.5"));
		assertTrue(all.contains("highest=4"));
		assertTrue(all.contains("floor=2"));
		assertTrue(all.contains("aptitude=A"));
		assertTrue(all.contains("lifepath:t1"));
		assertTrue(all.contains("lifepath:u1"));
		assertTrue(all.contains("5.0/0.0-10.0"));
		assertTrue(all.contains("expires in 500ms"));
		assertTrue(all.contains("data_version: " + com.dwurdy.lifepath.LifepathMod.DATA_VERSION));
	}

	@Test
	void describeShowsExpiredCooldownsAsExpired() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "ab"), 500L);

		String all = CharacterCommands.describe(data, 1000L).stream()
				.map(Component::getString).reduce("", (a, b) -> a + "\n" + b);

		assertTrue(all.contains("expired 500ms ago"));
	}

	@Test
	void modelResetRestoresDefaults() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "x"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "s"),
				new SkillProgress(9.0, 2, 2, 1, Aptitude.S, 5L));
		data.setDataVersion(0);

		data.reset();

		assertEquals(PlayerCharacterData.createDefault(), data);
	}

	private CommandNode<CommandSourceStack> root() {
		return new CommandDispatcher<CommandSourceStack>().register(LifepathCommands.buildRoot());
	}
}
