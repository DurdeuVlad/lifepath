package com.dwurdy.lifepath.producer;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Duck interface mixed onto {@code net.minecraft.world.entity.animal.Animal}
 * by {@code AnimalMixin}: the small attribution record husbandry producers
 * need on the animal itself. Two slots:
 *
 * <ul>
 *   <li>{@code lastInteractor} + {@code lastInteractGameTime} — the most
 *       recent real player who completed an interaction with the animal
 *       (feed, care item, milking, shearing). Backs the "last interacting
 *       player within a short window" attribution tier.</li>
 *   <li>{@code birthCredit} — the player credited with a CONCEPTION, stamped
 *       on both parents when {@code spawnChildFromBreeding} resolves. Deferred
 *       births (Animal Husbandry pregnancy completes days later in
 *       {@code aiStep}) read it back; persisted through entity NBT so a
 *       restart mid-pregnancy doesn't lose the credit.</li>
 * </ul>
 *
 * Everything is nullable/absent by design: no recorded player → no player
 * attribution → no husbandry XP (unattended breeding pays nothing).
 */
public interface HusbandryTracked {

	@Nullable
	UUID lifepath$getLastInteractor();

	void lifepath$setLastInteractor(@Nullable UUID playerId);

	long lifepath$getLastInteractGameTime();

	void lifepath$setLastInteractGameTime(long gameTime);

	/** Player credited with conception (deferred-birth attribution). */
	@Nullable
	UUID lifepath$getBirthCredit();

	void lifepath$setBirthCredit(@Nullable UUID playerId);
}
