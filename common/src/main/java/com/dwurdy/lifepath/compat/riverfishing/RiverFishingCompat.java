package com.dwurdy.lifepath.compat.riverfishing;

/**
 * Presence marker for the RiverFishing native emit. Jars that ship
 * {@code RiverFishingManagerMixin} also ship this class, so the pack's
 * KubeJS bridge can detect the native {@code lifepath:fishing} publish via
 * {@code Java.loadClass("com.dwurdy.lifepath.compat.riverfishing.RiverFishingCompat")}
 * and skip its own (mixin classes themselves are not loadable from KubeJS).
 * With the native emit active the bridge keeps only its
 * {@code OutcomeService.apply} half — one XP award per catch.
 */
public final class RiverFishingCompat {

	private RiverFishingCompat() {
	}

	/** Native {@code lifepath:fishing} emit on {@code giveFish} exists in this jar. */
	public static final boolean EMITS_FISHING = true;
}
