package io.github.durdeuvlad.lifepath.compat.overgeared;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.compat.ExternalActivityAdapter;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityEvents;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import java.util.Map;
import java.util.Set;
import io.github.durdeuvlad.lifepath.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Overgeared adapter (M7-1). Overgeared has no stable public Fabric event API
 * (and ships no Fabric build for 1.21.1 at all), so the adapter works at the
 * declarative level — TIMELINE §10 priority levels 2–3 (tags + normalized
 * events), no foreign classes are ever loaded:
 *
 * <ul>
 *   <li>Smithing-anvil forging is covered by {@code mixin.OvergearedAnvilMixin}
 *       (NeoForge-only, {@code requiredMods}-gated): its block entity drops the
 *       result via {@code Containers.dropItemStack}, bypassing every vanilla
 *       slot/event path, so the mixin reads the owner + recipe result
 *       reflectively at completion and publishes canonical smithing activity.</li>
 *   <li>This adapter watches normalized {@code lifepath:crafting} events whose
 *       result id is {@code overgeared:*} (or carries the datapack-extensible
 *       {@code #lifepath:forged_outputs} tag) and republishes them as
 *       canonical {@code lifepath:smithing} activity — covering any Overgeared
 *       craft paths (casting, alloy furnace outputs taken from menus) that do
 *       reuse vanilla result slots.</li>
 *   <li>Overgeared workstations/hammers are recognized through
 *       {@code #lifepath:smithing_workstations}/{@code #lifepath:smithing_tools}
 *       tag entries (data, not code).</li>
 *   <li>A future Overgeared Fabric port or bridge mod can emit forging events
 *       directly through the {@code lifepath:adapter} entrypoint seam instead
 *       — this adapter only covers the declarative slice.</li>
 * </ul>
 *
 * <p>Absent Overgeared → {@link #init()} registers nothing; the class still
 * loads because it references no foreign classes.
 */
public final class OvergearedCompat implements ExternalActivityAdapter {
	/** The external mod id — the only place it appears in Java (compat/). */
	public static final String MOD_ID = "overgeared";
	/** Pseudo workstation id stamped on translated forging events. */
	public static final ResourceLocation FORGE_WORKSTATION =
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "smithing_anvil");
	/** Marker attr so logs/debug can trace adapter-emitted events. */
	public static final String ATTR = "compat";
	public static final String ATTR_VALUE = "overgeared";

	/** Stable listener instance — method refs aren't interned. */
	private static final ActivityDispatcher.Listener LISTENER =
			OvergearedCompat::onActivity;

	/**
	 * Instantiated by the {@code lifepath:adapter} entrypoint in
	 * {@code fabric.mod.json} — discovery is declarative, so Lifepath core
	 * never references this class (the grep-clean compat boundary).
	 */
	public OvergearedCompat() {
	}

	@Override
	public ResourceLocation id() {
		return LifepathMod.id("overgeared");
	}

	/** Self-gates: absent Overgeared → nothing wired, one debug line. */
	@Override
	public void register() {
		if (!Platform.get().isModLoaded(MOD_ID)) {
			LifepathMod.LOGGER.debug("Overgeared absent — forging adapter skipped");
			return;
		}
		wire();
	}

	/** The actual wiring — separated from the presence gate for tests. */
	void wire() {
		ActivityDispatcher.registerAny(LISTENER);
		LifepathMod.LOGGER.info("Overgeared forging → Smithing XP bridge active");
	}

	/**
	 * Translates a normalized crafting event into a canonical smithing event
	 * when the result is a forged item: {@code overgeared:*} output id or the
	 * {@code lifepath:forged_outputs} tag. Never re-fires on its own output
	 * (type is {@code smithing}, not {@code crafting}).
	 */
	static void onActivity(ActivityEvent event) {
		if (event.type() != ActivityTypes.CRAFTING) {
			return;
		}
		ResourceLocation output = event.sourceId();
		boolean forged = output.getNamespace().equals(MOD_ID)
				|| event.tags().contains(LifepathMod.id("forged_outputs"));
		if (!forged) {
			return;
		}
		ActivityDispatcher.publish(ActivityEvents.smithing(event.player(), output,
				event.tags(), FORGE_WORKSTATION, Map.of(ATTR, ATTR_VALUE),
				event.cause()));
	}

	/** Test hook: removes the dispatcher subscription. */
	static void resetForTests() {
		ActivityDispatcher.unregisterAny(LISTENER);
	}
}
