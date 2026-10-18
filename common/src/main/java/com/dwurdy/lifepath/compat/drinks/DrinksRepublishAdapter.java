package com.dwurdy.lifepath.compat.drinks;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.ExternalActivityAdapter;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.event.ActivityTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * Drinks → Brewing republish adapter. Drink take-outs from Kaleidoscope
 * Tavern (shaker / pressing tub / barrel tap), Let's Do Vinery (fermentation
 * barrel), and Brewin' And Chewin' (keg) are emitted by their take-out mixins
 * as canonical {@code lifepath:crafting} events, which xp_source pays as
 * cooking XP. This adapter mirrors the Overgeared crafting → smithing
 * pattern: it watches normalized crafting events and republishes drink
 * outputs as canonical {@code lifepath:brewing} activity so the brewing
 * skill earns a secondary trickle ({@code xp_source/brewing.json}'s
 * {@code base_xp} — its {@code per_tag} potion keys simply never match).
 *
 * <p><b>Matching is data-driven:</b> an event qualifies when its tag set
 * contains {@code lifepath:brewable_drinks}. Every drink emitter publishes
 * the taken stack's real item tags ({@code output.getTags()} mapped to
 * locations — see {@code VanillaGameplayProducers.onCraftOutput}), and item
 * tag membership includes datapack-loaded tags, so a drink listed in
 * {@code data/lifepath/tags/item/brewable_drinks.json} already carries the
 * marker on its crafting event. No emitter changes are needed and the
 * adapter never resolves registries — the event's tag set is the whole
 * contract, so pack data owns which outputs count as brewed drinks.
 *
 * <p><b>No mod gate:</b> unlike OvergearedCompat there is no
 * {@code Platform.isModLoaded} early-return — matching is purely
 * tag-driven, so with every drink mod absent the tag is empty, the listener
 * stays a cheap no-op, and the datapack remains free to tag vanilla or
 * other mods' drinks without a code change.
 *
 * <p><b>No self-trigger:</b> republished events are typed
 * {@code lifepath:brewing}, not {@code lifepath:crafting}, so the listener's
 * own output can never re-enter its {@code CRAFTING} filter.
 *
 * <p><b>Automation:</b> the upstream crafting emitters already drop
 * automation takes before publishing; the republish reuses the event's
 * player + cause verbatim, and {@code player_caused_only} in xp_source is
 * the second line of defense. No marker attribute is stamped:
 * {@link ActivityEvents#brewing} fixes the canonical shape
 * (player / sourceId / tags / cause) with no attrs map — the
 * {@code lifepath:brewable_drinks} tag riding the republished event is the
 * traceable marker instead.
 */
public final class DrinksRepublishAdapter implements ExternalActivityAdapter {
	/**
	 * The datapack tag marker — a crafting event whose tag set contains this
	 * is republished as brewing. Data file:
	 * {@code data/lifepath/tags/item/brewable_drinks.json}.
	 */
	public static final ResourceLocation BREWABLE_DRINKS =
			LifepathMod.id("brewable_drinks");

	/** Stable listener instance — method refs aren't interned. */
	private static final ActivityDispatcher.Listener LISTENER =
			DrinksRepublishAdapter::onActivity;

	/**
	 * Instantiated by the {@code lifepath:adapter} entrypoint /
	 * {@code META-INF/services} discovery — declarative, so Lifepath core
	 * never references this class.
	 */
	public DrinksRepublishAdapter() {
	}

	@Override
	public ResourceLocation id() {
		return LifepathMod.id("drinks");
	}

	/**
	 * Registers unconditionally — see class javadoc: with no drink mod
	 * present the {@code brewable_drinks} tag matches nothing and the
	 * listener is a no-op.
	 */
	@Override
	public void register() {
		wire();
	}

	/** The actual wiring — separated for tests. */
	void wire() {
		ActivityDispatcher.registerAny(LISTENER);
		LifepathMod.LOGGER.info("Drink crafting → Brewing XP bridge active");
	}

	/**
	 * Republishes a normalized crafting event as canonical brewing activity
	 * when the produced item is a tagged brewed drink. Source id, tags,
	 * player, and cause pass through unchanged — only the activity type
	 * changes, so repetition signatures stay keyed on the produced item
	 * under the {@code brewing} bucket.
	 */
	static void onActivity(ActivityEvent event) {
		if (event.type() != ActivityTypes.CRAFTING) {
			return;
		}
		if (!event.tags().contains(BREWABLE_DRINKS)) {
			return;
		}
		ActivityDispatcher.publish(ActivityEvents.brewing(event.player(),
				event.sourceId(), event.tags(), event.cause()));
	}

	/** Test hook: removes the dispatcher subscription. */
	static void resetForTests() {
		ActivityDispatcher.unregisterAny(LISTENER);
	}
}
