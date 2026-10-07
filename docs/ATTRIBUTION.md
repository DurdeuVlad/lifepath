# XP Attribution Matrix

Who gets credited for every XP-producing event. The contract: **the player
performing the activity earns the XP** — never a bystander, station placer,
or automation. `player_caused_only` sources additionally refuse non-player
causes; the fake-player check (`isAutomation`) gates every seam upstream.

| Activity | Producer seam | Credited actor | Correct? |
|---|---|---|---|
| `lifepath:combat` (hunting, athletics, generic) | `onEntityKilledOther` | The **killer** (`entity instanceof ServerPlayer`) | ✅ |
| `lifepath:archery` | same kill seam, `IS_PROJECTILE` damage | The killer (projectile kills only) | ✅ |
| `lifepath:defence` | `onLivingDamage` | The **victim** — tanking damage is the activity; `cause = NON_PLAYER` (the attacker is the cause) | ✅ by design |
| `lifepath:mining` → mining/woodcutting/foraging skills | `onBlockBreak` | The **breaker**; player-placed blocks excluded via `PlacedBlockTracker` | ✅ |
| `lifepath:farming` (harvest) | `onBlockBreak` + `onUseBlock` (click-harvest at maturity) | The **harvester** | ✅ |
| `lifepath:farming` (planting) | `BlockItemMixin.place` | The **placer** | ✅ |
| `lifepath:smithing` (vanilla anvil, smithing table) | `AnvilScreenHandlerMixin` / `SmithingScreenHandlerMixin` result take | The **taker** — the player running the station | ✅ |
| `lifepath:smithing` (Overgeared forge) | `OvergearedAnvilMixin.craftItem/craftItemWithBlueprint` | The **session forger** — `getOwnerUUID()` is set by `onUseSmithingHammer` to the hammering player's UUID; a different player hammering is rejected with `anvil_in_use_by_another`; offline owners are cleared (verified in `overgeared-neoforge-1.21.1-1.6.19` bytecode) | ✅ |
| `lifepath:crafting` (generic + engineering/scholarship subjects) | `CraftingResultSlotMixin` result take | The **taker** | ✅ |
| cooking via Farmer's Delight pot/skillet | `FarmersDelightCookingPotMixin` / `FarmersDelightSkilletMixin` food take | The **taker** — whoever pulls the food | ✅ |
| `lifepath:fishing` | `FishingBobberEntityMixin` catch spawn | The **fisher** (bobber owner) | ✅ |

## Known gaps / documented edges

- **Overgeared hopper-fed completion**: items inserted without a hammer
  session leave `ownerUUID == null` → the mixin returns early → no XP.
  Consistent with `player_caused_only`; automation earns nothing.
- **Shared anvil sessions**: Overgeared itself serializes an anvil to one
  forger at a time — there is no "assister" credit by design.
- **Kill attribution** uses vanilla `getLastDamageSource`/killer semantics —
  a kill by your tamed wolf does not count (not `ServerPlayer`). Intended.

## How it's pinned

`AttributionAuditTest` asserts (a) every `ActivityEvents` factory stores the
exact `ServerPlayer` reference it was handed — producers cannot re-resolve or
swap the actor — and (b) shipped `xp_source/*.json` `player_caused_only`
flags match this matrix. The mixin-level seams are runtime-only; rows above
carry the verified call chain as evidence.
