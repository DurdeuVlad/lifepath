package com.dwurdy.lifepath.producer;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.skill.SkillXpService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Level-banded vein-mining: when a player breaks a {@code lifepath:vein_mineable}
 * block with a correct tool, connected same-block ores are flood-filled
 * (26-neighborhood — real ore veins snake diagonally) and broken up to a cap
 * set by mining level: &lt;20 → none, 20–39 → 4, 40–59 → 8, 60–79 → 16,
 * 80+ → 32.
 *
 * <p>Economy invariants preserved:
 * <ul>
 *   <li>Player-placed blocks are skipped (origin and vein members) — the
 *       re-mine exploit guard is the same one the XP path uses.</li>
 *   <li>Extra drops go through {@code Block.getDrops} with the player as
 *       entity — {@code BlockDropOutcomeMixin} scales them by mining band
 *       exactly like the primary block's drops.</li>
 *   <li>Tool durability pays 1 per extra block; breaking the tool ends the
 *       vein.</li>
 *   <li>Blocks the tool can't harvest (stone-level pick on diamond ore)
 *       yield nothing — {@code getDrops} loot conditions already enforce it,
 *       and the whole pass is gated on the primary break being harvestable.</li>
 * </ul>
 *
 * <p>XP: vein members emit no extra activity — one attended break = one
 * mining event; the yield difference is outcome-side as designed.
 */
public final class VeinMiner {

	private VeinMiner() {
	}

	private static final TagKey<Block> VEIN_MINEABLE =
			TagKey.create(Registries.BLOCK, LifepathMod.id("vein_mineable"));
	private static final ResourceLocation MINING = LifepathMod.id("mining");
	private static final int MAX_SCAN = 512;

	static int capForLevel(int level) {
		if (level >= 80) return 32;
		if (level >= 60) return 16;
		if (level >= 40) return 8;
		if (level >= 20) return 4;
		return 0;
	}

	public static void onBlockBroken(ServerPlayer player, ServerLevel level,
			BlockPos origin, BlockState originState, boolean originWasPlayerPlaced) {
		if (originWasPlayerPlaced || !originState.is(VEIN_MINEABLE)
				|| !player.hasCorrectToolForDrops(originState)) {
			return;
		}
		int cap;
		try {
			cap = capForLevel(SkillXpService.getLevel(
					CharacterManager.getCharacter(player), MINING));
		} catch (Throwable t) {
			return;
		}
		if (cap <= 0) {
			return;
		}
		Block veinBlock = originState.getBlock();
		ItemStack tool = player.getMainHandItem();
		List<BlockPos> vein = collectVein(level, origin, veinBlock, cap);
		for (BlockPos pos : vein) {
			BlockState veinState = level.getBlockState(pos);
			if (!veinState.is(veinBlock) || PlacedBlockTracker.contains(level, pos)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(pos);
			List<ItemStack> drops = Block.getDrops(veinState, level, pos, be, player, tool);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			for (ItemStack drop : drops) {
				Block.popResource(level, pos, drop);
			}
			veinState.spawnAfterBreak(level, pos, tool, true);
			tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			if (tool.isEmpty()) {
				break;
			}
		}
	}

	private static List<BlockPos> collectVein(ServerLevel level, BlockPos origin,
			Block veinBlock, int cap) {
		List<BlockPos> found = new ArrayList<>(cap);
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(origin);
		visited.add(origin);
		while (!queue.isEmpty() && found.size() < cap && visited.size() < MAX_SCAN) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos next = cur.offset(dx, dy, dz);
						if (!visited.add(next)) {
							continue;
						}
						if (level.getBlockState(next).is(veinBlock)) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		return found;
	}
}
