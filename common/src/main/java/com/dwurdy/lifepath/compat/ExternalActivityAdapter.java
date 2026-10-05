package com.dwurdy.lifepath.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Adapter seam for external mods (M2-5). An integration — e.g. the M7-1
 * Overgeared adapter — implements this interface, registers via
 * {@link ExternalAdapterRegistry#register(ExternalActivityAdapter)}, and in
 * {@link #register()} wires the foreign mod's hooks to
 * {@code ActivityDispatcher.publish(...)} with normalized events.
 *
 * <p><b>Contract for adapters:</b>
 * <ul>
 *   <li>Adapters translate foreign gameplay events into Lifepath
 *       {@code ActivityEvent}s. They must NOT award XP, call
 *       {@code SkillXpService}, or duplicate suppression logic —
 *       xp_source data and the router decide amounts.</li>
 *   <li>An adapter lives in its own integration point (typically
 *       {@code fabric.mod.json} {@code custom} data or a FAPI entrypoint the
 *       mod itself calls). Lifepath never loads mod classes directly —
 *       adapters are provided BY the integrating mod or a dedicated addon,
 *       discovered via {@code FabricLoader.getEntrypoints("lifepath:adapter",
 *       ExternalActivityAdapter.class)}-style lookup (wired in M7-1).</li>
 *   <li>Generic Lifepath services must never check for specific mods —
 *       integration is entirely declarative (tags/data) or adapter-driven.</li>
 *   <li>Emit with {@code ActivityEvent.Cause.PLAYER} only when the foreign
 *       system attributes the action to a real player.</li>
 * </ul>
 */
public interface ExternalActivityAdapter {

	/** Stable adapter id (e.g. the integrating mod's id). Used in logs only. */
	ResourceLocation id();

	/**
	 * Wire the foreign mod's event surface to {@code ActivityDispatcher}.
	 * Called once during Lifepath init, on the mod-loading thread.
	 */
	void register();
}
