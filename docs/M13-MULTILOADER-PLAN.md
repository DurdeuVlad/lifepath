# M13 — Multi-loader rearchitecture (NeoForge primary, Fabric secondary)

**Status: ready-for-handoff — decisions approved by owner 2026-09-27;
filed as issues #133–#138.**

## Contract

- **Goal:** Lifepath builds for NeoForge 1.21.1 as the primary loader and
  Fabric 1.21.1 as the secondary, from one codebase with a loader-agnostic
  domain core and platform ports/adapters (hexagonal). CI ships both jars.
- **System:** Gradle multi-project (`common/`, `fabric/`, `neoforge/`); all
  shared code compiled against Mojang official mappings; each loader module
  implements a small `platform` port set.
- **Constraints:** the shipped Fabric beta (`v1.0.0-beta.1`) must keep working
  and keep its current save format; the 346-test suite stays green at every
  step; no Forge/legacy loaders; datapack content ships unchanged.
- **Evaluation:** both jars build and boot; character flow, abilities, HUD,
  and persistence behave identically; the Overgeared adapter is exercisable
  on NeoForge 1.21.1 (the blocker behind #61 disappears).

## Repository facts (inspected this commit)

- 196 Java files; **28 reference `net.fabricmc`** (18 common, 10 client).
- Fabric API surface actually used, i.e. the port inventory:
  - **Networking:** `PayloadTypeRegistry`, `ServerPlayNetworking`,
    `ClientPlayNetworking` (`network/LifepathNetworking`,
    `client/network/ClientLifepathNetworking`, senders in `CooldownService`,
    `FeedbackService`, `ResourceService`, `AbilityEngine`).
  - **Persistence:** `AttachmentRegistry`/`AttachmentType`
    (`character/CharacterAttachments`) — NeoForge has a native
    data-attachment equivalent.
  - **Datapack reload:** `ResourceManagerHelper` +
    `SimpleSynchronousResourceReloadListener` (`reload/ReloadManager`,
    client `ClientIcons` reload listener in `LifepathClient`) — NeoForge:
    `AddReloadListenerEvent`.
  - **Events:** `ServerTickEvents`, `ServerPlayConnectionEvents`,
    `PlayerBlockBreakEvents`, `UseBlockCallback`, join/respawn/copy hooks
    (`AbilityEngine`, `FeedbackService`, `VanillaGameplayProducers`,
    `CharacterManager`, `EncumbranceService`, `SkillDecayService`,
    `ResourceService`) — NeoForge: `NeoForge.EVENT_BUS` equivalents.
  - **Own event:** `SkillEvents` uses Fabric `EventFactory` — replace with a
    plain internal dispatcher (it is our own event; no loader feature needed).
  - **Mod/env introspection:** `FabricLoader` (`OvergearedCompat`,
    `LifepathConfig`, `LifepathMod`) — NeoForge: `ModList`/`FMLPaths`.
  - **Client:** `KeyBindingHelper`, `ClientTickEvents`, `HudRenderCallback`,
    `ClientPlayConnectionEvents` — NeoForge: `RegisterKeyMappingsEvent`,
    `ClientTickEvent`, `RegisterGuiLayersEvent`, `ClientPlayerNetworkEvent`.
- **Metadata:** `fabric.mod.json` → `neoforge.mods.toml`; entrypoints
  `ModInitializer`/`ClientModInitializer` → `@Mod` class.
- **Mixins:** 9 (anvil/smithing/crafting/fishing/entity/advancement hooks).
  NeoForge runs SpongePowered mixins; target names differ only by mappings —
  unified Mojmap makes them portable as-is.
- **Datapack content:** zero `fabric:`-namespaced keys in `data/` — all 129
  defs ship unchanged to both loaders.
- **Mappings:** current code is **Yarn** (`ServerPlayerEntity`, `Text`,
  `DrawContext`); NeoForge is **Mojmap** (`ServerPlayer`, `Component`,
  `GuiGraphics`). This is the pivotal decision — see Decisions.
- **Tests:** JUnit over MC classes; unaffected in structure, renamed symbols
  only. `test` source set already spans `main`+`client` output.
- **Toolchain:** NeoForge 1.21.1 = `21.1.x`; ModDevGradle is the current
  recommended plugin (reference: MultiLoader-Template layout). Fabric module
  keeps fabric-loom + Fabric API.
- **Releases:** `release.yml` is tag-driven (`v*` → clean build →
  `checksums.txt` → draft release). Matrix extension ships both jars.

## Decisions (approved by owner 2026-09-27; recorded in #133)

1. **Unified Mojmap everywhere** (recommended) — one symbol set for
   `common+fabric+neoforge`; loom supports official mappings on Fabric.
   *Alternative rejected:* domain core with zero `net.minecraft` references —
   the domain *is* Minecraft objects (every ability action/condition touches
   entities/registries); wrapping all of it in domain types is
   disproportionate ceremony for no runtime gain.
2. **Module layout** — `common` (Mojmap, today's `main`+`client` minus the 28
   coupled files), `fabric` (loom), `neoforge` (ModDevGradle). Single repo,
   one version.
3. **Primary/secondary split** — NeoForge is the release-facing target;
   Fabric remains fully supported and shipped on every release (not
   "best-effort"), per the user's stated intent. Resolved: every M13-onward
   release dual-ships — NeoForge primary jar, Fabric secondary jar.

## Issues (execution order)

### M13-1 (#133) — ADR: loader strategy + platform seam inventory
- **Intent:** record the multi-loader decision and the exact port surface so
  no implementer re-derives it.
- **Expectation:** `docs/adr/` (or `docs/M13-ADR.md`) contains the mapping
  decision, module layout, port list, and rejected alternatives; the port
  list is verified against a fresh `net.fabricmc` import audit.
- **Acceptance:** every coupled file maps to a named port or an explicit
  "stays in common" note; decision record approved.
- **Non-goals:** no code changes.
- **Depends on:** owner decisions above. **Labels:** `infrastructure`,
  `engine`, `documentation`. **Verify:** doc review vs audit output.
  **Filed: #133.**

### M13-2 (#134) — Unify mappings to Mojmap (Yarn → Mojmap rename)
- **Intent:** one codebase, one symbol set — precondition for any shared code.
- **Expectation:** `loom.officialMojangMappings()`; every Yarn identifier
  remapped; `fabric.mod.json`/mixins updated; runtime behavior identical.
- **Acceptance:** `clean build` green; full test suite passes; Fabric dev
  client boots and character flow works live (regression proof, not just
  compile).
- **Non-goals:** no module split yet; no NeoForge code.
- **Depends on:** #133. **Labels:** `engine`, `infrastructure`.
- **Verify:** suite + live boot evidence.

### M13-3 (#135) — Extract `platform` ports; move Fabric behind them
- **Intent:** hexagonal seam — common code calls interfaces, never
  `net.fabricmc`.
- **Expectation:** `platform/` interfaces (networking, attachments,
  event-registration, reload-listener hook, mod-list/config-dir, client
  keybind/tick/hud/connection); `fabric/` implements them; `common` has zero
  `net.fabricmc` imports (enforced, e.g. checkstyle/forbidden-imports or a
  test scanning sources).
- **Acceptance:** zero `net.fabricmc` in `common` (checked in CI); suite
  green; live regression pass on Fabric client+server.
- **Non-goals:** no NeoForge implementation; no behavior changes.
- **Depends on:** #134. **Labels:** `engine`, `infrastructure`.
- **Verify:** CI forbidden-imports check + suite + live boot.

### M13-4 (#136) — NeoForge module reaches feature parity
- **Intent:** Lifepath runs on NeoForge 1.21.1 with identical gameplay.
- **Expectation:** `neoforge/` module (ModDevGradle, `neoforge.mods.toml`,
  `@Mod` entrypoint, mixin config); every port implemented via NeoForge APIs;
  shared `data/` assets on both jars.
- **Acceptance:** NF client boots, joins world, `/lifepath species choose`
  + ability key + screens + HUD work; save/load round-trips the same
  character NBT; dedicated NF server boots and runs `/reload` clean.
- **Non-goals:** Overgeared integration test (#138); publishing.
- **Depends on:** #135. **Labels:** `engine`, `infrastructure`.
- **Verify:** live NF client+server evidence.

### M13-5 (#137) — CI matrix ships both loaders
- **Intent:** "secondary support in CI/CD" made literal — every build/release
  produces both jars.
- **Expectation:** `build.yml` builds both modules; `release.yml` attaches
  `lifepath-<ver>-neoforge.jar`, `-fabric.jar`, sources jars, unified
  `checksums.txt`; README/COMPAT.md loader table updated.
- **Acceptance:** a pushed tag produces a draft release containing both
  loaders with correct metadata; artifact names match docs.
- **Non-goals:** CurseForge listing changes (separate, gated on release
  readiness); Forge/1.20.1 ports.
- **Depends on:** #136. **Labels:** `infrastructure`, `release`.
- **Verify:** CI run receipts + draft release contents.

### M13-6 (#138) — Overgeared adapter live validation on NeoForge
- **Intent:** close the #61 blocker by actually running the integration.
- **Expectation:** NeoForge dev instance with `overgeared-neoforge-1.21.1-*`
  alongside Lifepath; adapter registers, activity events flow, denial/
  fallback paths behave; evidence recorded on #61.
- **Acceptance:** both mods loaded in one instance; adapter-active path
  exercised end-to-end; #61 updated with real evidence and closable.
- **Depends on:** #136. **Labels:** `compatibility`, `verification`.
- **Verify:** live co-load proof; #61 evidence comment.

## Risks

- **Mapping rename is repo-wide** — mechanical but touches ~all files; do it
  as one clean commit before any feature work resumes on it.
- **Mixin injection points** under Mojmap need per-mixin verification
  (targets like `FishingBobberEntity` → `FishingHook`).
- **Event parity gaps:** not every Fabric callback has a 1:1 NeoForge event —
  expect a few adapters to re-derive behavior (e.g. `UseBlockCallback` vs
  `PlayerInteractEvent.RightClickBlock` semantics differ subtly).
- **Beta continuity:** players on beta.1 (Fabric) — if the next release is
  NeoForge-primary, document the Fabric jar's continued availability
  (open question in Decisions §3).

## Next action

Decisions approved; issues filed (#133–#138). Execution begins at #133 —
the ADR sign-off gate — then strictly in order.
