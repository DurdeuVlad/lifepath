package com.dwurdy.lifepath.compat.butchery;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emit helpers for the Butchery (MCreator) seams. Free of butchery/neoforge
 * types — mixins hand over vanilla signatures only, and the station-slot
 * filter works on class names so a butchery-side refactor degrades to silent
 * non-payment, never wrong-payment.
 *
 * <p>Two seams:
 * <ul>
 *   <li><b>Knife cutup</b> — 138 generated {@code *cutupProcedure.execute}
 *   methods guard on "clicked block is my carcass variant AND held item is a
 *   knife-tag member", then convert the carcass and damage the knife. The
 *   carcass is captured at HEAD (before the conversion rewrites it); the emit
 *   fires on the first {@code ItemStack.hurtAndBreak} the cut commits —
 *   exactly-once per click via ThreadLocal consumption, so variant-branch
 *   layout cannot double-pay.</li>
 *   <li><b>Station output takes</b> — the generated GUI menus expose output
 *   slots as anonymous {@code SlotItemHandler} subclasses (meat grinder,
 *   pestle &amp; mortar, taxidermy station). {@code SlotItemHandler} does
 *   not declare {@code onTake} — the inherited {@code Slot.onTake} is the
 *   single funnel both normal and shift-click takes pass through; the
 *   helper filters on the exact output slot classes.</li>
 * </ul>
 */
public final class ButcheryProcessing {
	private ButcheryProcessing() {
	}

	private static final ResourceLocation TAG_BUTCHERED = LifepathMod.id("butchered");
	private static final ResourceLocation MEAT_GRINDER =
			ResourceLocation.fromNamespaceAndPath("butchery", "meat_grinder");
	private static final ResourceLocation PESTLE =
			ResourceLocation.fromNamespaceAndPath("butchery", "pestle_and_mortar");
	private static final ResourceLocation TAXIDERMY =
			ResourceLocation.fromNamespaceAndPath("butchery", "taxidermy_table");
	private static final ResourceLocation CUTUP =
			ResourceLocation.fromNamespaceAndPath("butchery", "carcass_cutup");

	/**
	 * Butchery 5.2 output-slot classes (verified by bytecode: every other
	 * custom slot's {@code mayPlace} accepts something; only these return
	 * literal false). Renumbered inner classes degrade to a miss.
	 */
	private static final Map<String, ResourceLocation> OUTPUT_SLOTS = Map.of(
			"net.mcreator.butchery.world.inventory.MeatgrinderguiMenu$5", MEAT_GRINDER,
			"net.mcreator.butchery.world.inventory.MeatgrinderguiMenu$7", MEAT_GRINDER,
			"net.mcreator.butchery.world.inventory.PestleandmotarguiMenu$3", PESTLE,
			"net.mcreator.butchery.world.inventory.TaxidermystationguiMenu$5", TAXIDERMY);

	/**
	 * The carcass block seen at execute-HEAD, before the success branch
	 * replaces it with the drained variant. Consumed by the first emitted
	 * cut so a procedure with several marker call sites still pays once.
	 */
	private static final ThreadLocal<BlockState> CUT_CARCASS = new ThreadLocal<>();

	/** HEAD of the public {@code execute(LevelAccessor, DDD, Entity)} wrapper. */
	public static void captureCarcass(LevelAccessor world, double x, double y, double z) {
		try {
			CUT_CARCASS.set(world.getBlockState(BlockPos.containing(x, y, z)));
		} catch (Throwable t) {
			CUT_CARCASS.remove();
		}
	}

	/**
	 * INVOKE {@code ItemStack.hurtAndBreak} inside the cutup body — the knife
	 * pays durability only inside the block-match success branch, so this is
	 * the "a cut was committed" marker. Subject = the captured carcass block.
	 */
	public static void onKnifeCommitted(Entity entity) {
		BlockState carcass = CUT_CARCASS.get();
		if (carcass == null || !(entity instanceof ServerPlayer player)
				|| isAutomation(player)) {
			return;
		}
		ResourceLocation subject = BuiltInRegistries.BLOCK.getKey(carcass.getBlock());
		if (!"butchery".equals(subject.getNamespace())) {
			return; // defensive: a marker firing off a non-carcass click pays nothing
		}
		CUT_CARCASS.remove(); // emit once per execute call
		Set<ResourceLocation> tags = carcass.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		tags.add(TAG_BUTCHERED);
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.CRAFTING,
				subject, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of("station", CUTUP.toString())));
	}

	/** RETURN of the public execute wrapper — ThreadLocal hygiene. */
	public static void clearCut() {
		CUT_CARCASS.remove();
	}

	/**
	 * {@code Slot.onTake} funnel (inherited by every {@code
	 * SlotItemHandler} output slot) — pays only for the generated
	 * output-slot classes. Subject = the taken stack's item id;
	 * the station id rides as a matchable tag.
	 */
	public static void onStationTake(Object slot, Player player, ItemStack stack) {
		if (slot == null || !(player instanceof ServerPlayer serverPlayer)
				|| isAutomation(serverPlayer) || stack.isEmpty()) {
			return;
		}
		ResourceLocation station = OUTPUT_SLOTS.get(slot.getClass().getName());
		if (station == null) {
			return;
		}
		Set<ResourceLocation> tags = stack.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		tags.add(station);
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.CRAFTING,
				itemId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(),
				Map.of("count", Integer.toString(stack.getCount()),
						"station", station.toString())));
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
