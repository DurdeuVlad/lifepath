# Lifepath Save/Data Migrations (M10-2)

How old saves reach the current schema — the designed path, the rules, and
how to add the next step without reverse-engineering anything.

## The pipeline

Every character load runs the same pipeline in `CharacterPersistence`:

```
raw NBT blob
  -> CharacterMigrations.migrate(blob)      // schema upgrades, in order
  -> codec decode (PlayerCharacterData)     // current shape
  -> stamp data_version = DATA_VERSION
  -> sanitize against ContentIndex          // unknown/dead refs drop+warn
```

- **Attachment**: `lifepath:character_attachments` on the player entity;
  failed loads write the raw blob to `<backupDir>/corrupt/<uuid>-<nanos>.snbt`
  for manual recovery before defaults apply.
- **`data_version`**: integer field on the blob; absent = `0`
  (pre-versioning data). `LifepathMod.DATA_VERSION` is the current target —
  bump it by exactly **1** per schema change.
- **Forward-compat**: a blob *newer* than the mod's target loads as-is with
  a WARN — never rewrite what we don't understand.
- **Corrupt/unreadable**: `loadSafe` falls back to fresh defaults + writes a
  `.snbt` backup of the raw blob + ERROR — never corrupt further, never crash.

## The chain — `CharacterMigrations`

One ordered `MigrationChain`; each `DataMigration` bumps exactly one version
(`fromVersion -> fromVersion+1` — the builder rejects skips and duplicates).
**Append only; never edit an old step.**

| Version | Change | Migration |
|---|---|---|
| 0 → 1 | Pre-versioning normalization. v1 introduced the field layout (`species_id`, `skills{}`, `traits[]`, …); the step exists so v0 blobs provably ride the chain. | no-op (codec fills defaults) |
| 1 → 2 | `conditions` became per-condition state. v1 stored a bare id *list*; v2 stores `{id: {stage, stage_started_at, event_progress}}`. | reshape: each listed id gets an empty state compound (decodes to stage 0 via field defaults) |

Current `DATA_VERSION`: **2**.

## Removed/renamed content ids — the sanitize fallback

After decode, every stored reference is checked against `ContentIndex`
(`LifepathContent.exists(domain, id)`). Missing content degrades to a
documented fallback — never corruption:

| Stored reference | Domain | Fallback |
|---|---|---|
| `species_id` | `species` | → `null` (no species; effects stop) |
| `specialization_id` | `specialization` | → `null` |
| `skills{}` key | `skill` | entry dropped; **known** skills additionally repaired (level/floor/xp clamped to the current def) |
| `traits[]` | `trait` | **permissive today** — no trait registry exists yet, so `exists` answers true and entries are kept; a future trait domain turns this into drop+warn automatically |
| `conditions{}`, `attunements[]` | `condition`/`attunement` | entry dropped + WARN |
| `unlocks[]` | `unlock_content` | dropped unless the id still names gated content (a species) or a surviving unlock def grants it |
| `resources{}` key | `resource` | dropped; **known** resources get non-finite/out-of-range values repaired to def bounds |
| `cooldowns{}` key | `ability` | dropped; `schedule/*` engine markers are exempt (bookkeeping, not content); cooldowns under `persist_min_seconds` remaining are dropped on load |

Renames are "old id disappears" — the old entry drops, the new content is a
fresh acquisition. There is no alias table by design: keep ids stable instead.

## Adding the next migration

1. Change the model (field add/remove/reshape) in `PlayerCharacterData` +
   bump `LifepathMod.DATA_VERSION` by one.
2. Append `step(new DataMigration() { fromVersion() = N, toVersion() = N+1,
   migrate(blob) { /* reshape the raw NbtCompound */ } })` to the chain in
   `CharacterMigrations`. Migrate the *raw NBT* — codecs aren't involved yet.
3. Write a fixture test in `CharacterPersistenceTest` in the style of
   `v1FixtureMigratesThroughTheRealPath`: hand-build an `NbtCompound` in the
   old shape, run `CharacterPersistence.deserialize`, assert the current
   shape + stamped version.
4. If a field's semantics changed (not just shape), document the mapping
   here in the version table.

Chain gaps are fatal-on-purpose: `IllegalStateException` at migrate → caught
by `loadSafe` → backup + defaults + ERROR. A gap is a programming error, not
a data error.

## Test expectations per version

- `MigrationChainTest`: ordering, version stamping, forward-compat
  untouched, gap-throw, duplicate/skip rejection.
- `CharacterPersistenceTest`: fixture upgrade end-to-end
  (`v1FixtureMigratesThroughTheRealPath`), unknown-id drops
  (`unknownContentIdsAreDroppedOnLoad`), removed-content fallback
  (`removedContentIdDropsWithoutCorrupting`), corrupt-blob backup
  (`corruptBlobYieldsDefaultsPlusBackup`), repair-on-load paths.
