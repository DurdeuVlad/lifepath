# M13-ADR — Multi-loader strategy: NeoForge primary, Fabric secondary

**Status:** Accepted — owner sign-off given 2026-09-27 (issue #133).
**Date:** 2026-09-27. **Milestone:** M13 (plan: `docs/M13-MULTILOADER-PLAN.md`).

## Context

Lifepath 1.0.0-beta.1 ships Fabric-only (fabric-loom + Fabric API, Yarn
mappings). Owner direction: **NeoForge 1.21.1 is the primary loader; every
release dual-ships with Fabric as the secondary.** The Overgeared integration
(#61) can only exist on NeoForge — no Fabric 1.21.1 build of Overgeared
exists (verified at both primary listings 2026-09-27).

## Decisions

### 1. Unified Mojmap across all modules

All source compiles against Mojang official mappings. Fabric module uses
`loom.officialMojangMappings()`; NeoForge uses Mojmap natively.

*Rejected:* keep Yarn in `fabric/` and translate at the seam — impossible;
`common` compiles once and both loaders must see identical `net.minecraft`
symbol names.

*Rejected:* domain core with zero `net.minecraft` references — the domain
*is* Minecraft objects (ability actions/conditions touch entities, world,
registries, text). Wrapping the entire MC surface in domain types is
ceremony without runtime gain. Ports cover only loader APIs, not MC itself.

### 2. Module layout — `common` / `fabric` / `neoforge`

Gradle multi-project, one repo, one version:

- `common/` — Mojmap; all gameplay code, content registries, screens, mixins;
  calls `platform/` interfaces for every loader API. Zero `net.fabricmc` /
  `net.neoforged` imports (CI-enforced).
- `fabric/` — fabric-loom + Fabric API; `fabric.mod.json`; implements the
  ports via Fabric APIs.
- `neoforge/` — ModDevGradle (NeoForge `21.1.x`); `neoforge.mods.toml`;
  `@Mod` entrypoint; implements the ports via NeoForge APIs.

Reference layout: the community MultiLoader-Template pattern, adapted to this
repo's `main`/`client` sourceset split.

### 3. Release policy — dual-ship every release

NeoForge jar is the headline artifact; Fabric jar ships alongside on every
release. CI matrix builds both; `release.yml` attaches both + unified
`checksums.txt`.

## Platform seam inventory (audited 2026-09-27)

28 files import `net.fabricmc.*` / `net.fabricmc.loader.*` (18 main, 10
client). The complete API surface in use:

| Fabric API | Port | NeoForge equivalent |
|---|---|---|
| `PayloadTypeRegistry`, `ServerPlayNetworking`, `ClientPlayNetworking` | `NetPort` — payload register + C2S/S2C send | `RegisterPayloadHandlersEvent`/`PayloadRegistrar`, `PacketDistributor` |
| `AttachmentRegistry`, `AttachmentType` | `AttachPort` — character data persistence | `net.neoforged.neoforge.attachment` (`AttachmentRegistry`, `AttachmentType`) |
| `ResourceManagerHelper`, `SimpleSynchronousResourceReloadListener` | `ReloadPort` — server datapack + client resource reload | `AddReloadListenerEvent`, `RegisterClientReloadListenersEvent` |
| `ServerTickEvents`, `ServerLifecycleEvents` | `EventPort` — tick + lifecycle | `ServerTickEvent.Post`, `ServerStarting/StoppingEvent` |
| `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerPlayerEvents`, `ServerEntityWorldChangeEvents` | `EventPort` — player lifecycle | `PlayerLoggedIn/OutEvent`, `PlayerRespawnEvent`, `PlayerClone`, `PlayerChangedDimensionEvent` |
| `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`, `ServerLivingEntityEvents.AFTER_DAMAGE` | `EventPort` — combat | `LivingDeathEvent`, `LivingDamageEvent.Post`/`LivingIncomingDamageEvent` |
| `PlayerBlockBreakEvents`, `UseBlockCallback`, `UseItemCallback` | `EventPort` — interaction producers | `BlockEvent.BreakEvent`, `PlayerInteractEvent.RightClickBlock/RightClickItem` |
| `CommandRegistrationCallback` | `EventPort` — command registration | `RegisterCommandsEvent` |
| `KeyBindingHelper`, `ClientTickEvents`, `HudRenderCallback`, `ClientPlayConnectionEvents` | `ClientPort` — keys, client tick, HUD, connection | `RegisterKeyMappingsEvent`, `ClientTickEvent.Post`, `RegisterGuiLayersEvent`, `ClientPlayerNetworkEvent` |
| `FabricLoader` (mod list, config dir, version) | `EnvPort` — mod/env introspection | `ModList`, `FMLPaths`, `ModContainer` |
| `ModInitializer`, `ClientModInitializer`, `@Environment(EnvType)` | entrypoint/dist — module-level, not a port | `@Mod`, `Dist.CLIENT` guard/`FMLEnvironment` |
| `EventFactory`/`Event` (`SkillEvents`) | none — own event; replace with plain listener list | n/a |

### File → port map

- **NetPort:** `network/LifepathNetworking`, `client/network/ClientLifepathNetworking`,
  senders in `ability/CooldownService`, `ability/AbilityEngine`,
  `feedback/FeedbackService`, `resource/ResourceService`,
  `client/ability/ClientAbilityState`.
- **AttachPort:** `character/CharacterAttachments`.
- **ReloadPort:** `reload/ReloadManager`, client icon reload in
  `client/LifepathClient`, `client/icon/ClientIcons` (listener registration).
- **EventPort:** `ability/AbilityEngine` (tick), `ability/BuiltinActions`,
  `character/CharacterManager` (join/respawn/world-change),
  `command/LifepathCommands` (+tree), `encumbrance/EncumbranceService`,
  `event/ActivityDispatcher` producers registration,
  `feedback/FeedbackService` (disconnect), `producer/VanillaGameplayProducers`
  (block-break/use), `resource/ResourceService`,
  `skill/SkillDecayService` (tick), `LifepathMod` (lifecycle).
- **EnvPort:** `compat/ExternalAdapterRegistry`, `compat/overgeared/OvergearedCompat`,
  `config/LifepathConfig`, `LifepathMod`.
- **ClientPort:** `client/LifepathClient`, `client/ability/ClientAbilityState`,
  `client/character/ClientCharacterState`, `client/feedback/ClientFeedback`,
  `client/hud/LifepathHud`, `client/network/ClientLifepathNetworking`,
  `client/screen/{CharacterScreen,SkillsScreen,SkillDetailScreen}`
  (`@Environment` dist annotations).
- **Stays in common, no port:** `event/SkillEvents` (swap `EventFactory` for a
  plain listener list).
- **Not ports:** 9 mixins live in `common` and share across loaders under
  Mojmap; mixin config is declared per-module (`fabric.mod.json` /
  `neoforge.mods.toml`).

## Consequences

- One-time repo-wide rename (#134) — mechanical, must land as a clean single
  commit before parallel feature work.
- Test suite unchanged in structure (symbols only); it already runs against
  `main`+`client` output and will run inside `common`.
- `fabric.mod.json` requires a loom-friendly entrypoint shim; `neoforge.mods.toml`
  + `@Mod` constructor wiring in `neoforge/`.
- NF event semantics are not 1:1 — `UseBlockCallback` vs
  `PlayerInteractEvent.RightClickBlock`, damage-event timing differ; adapters
  must re-derive behavior, differences recorded on #136.
- The shipped `v1.0.0-beta.1` Fabric artifact is unaffected; save format is
  loader-neutral NBT — worlds carry across loaders.
