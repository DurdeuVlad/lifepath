package com.dwurdy.lifepath.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-condition runtime state (M9-1): the current stage index, when that
 * stage was entered (epoch millis — drives {@code advance_after_seconds}),
 * and progress toward the stage's {@code advance_count} event requirement.
 *
 * <p>Persisted inside {@code PlayerCharacterData.conditions} as a
 * condition-id → state map; unknown/absent state reads as stage 0.
 */
public record ConditionState(int stage, long stageStartedAtMs, int eventProgress) {

	public static final Codec<ConditionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("stage", 0).forGetter(ConditionState::stage),
			Codec.LONG.optionalFieldOf("stage_started_at", 0L).forGetter(ConditionState::stageStartedAtMs),
			Codec.INT.optionalFieldOf("event_progress", 0).forGetter(ConditionState::eventProgress)
	).apply(instance, ConditionState::new));

	public static ConditionState fresh(long nowMs) {
		return new ConditionState(0, nowMs, 0);
	}

	ConditionState advanced(long nowMs) {
		return new ConditionState(stage + 1, nowMs, 0);
	}

	ConditionState eventSeen() {
		return new ConditionState(stage, stageStartedAtMs, eventProgress + 1);
	}
}
