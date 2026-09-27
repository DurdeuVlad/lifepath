# Lifepath — Development Timeline & Milestone Plan

> Project: Lifepath  
> Target: Minecraft 1.21.1 / Fabric  
> Document purpose: High-level implementation roadmap for human developers and AI coding agents  
> Status: Living execution plan — M0–M9 and M10-1/M10-2 delivered; M10-3 (this
> document's companion docs) through M11 remain. Exit-gate phrasing below
> describes *targets*; shipped reality is enumerated in `docs/API.md`,
> `docs/DATAPACK_API.md`, `docs/CONFIGURATION.md`, `docs/ADMIN_COMMANDS.md`,
> and `docs/MIGRATIONS.md`.  
> Relationship to design: This document operationalizes `GAMEDESIGN.md`

---

## 1. Execution Rules

This roadmap is intentionally staged.

The project should **not** be developed by implementing every Species, Skill, Specialization, and advanced system in parallel.

Each milestone has:
- a clear objective;
- defined scope;
- explicit dependencies;
- required deliverables;
- required tests;
- an exit gate.

The next milestone should not begin until the current milestone passes its exit gate.

### Non-negotiable project rules

1. **Server authority first.**
   Character state, XP, levels, cooldowns, progression, and unlocks are decided by the server.

2. **Persistence before content scale.**
   No large content expansion before save/load behavior is proven stable.

3. **Networking before UI expansion.**
   UI must consume synchronized state, not invent parallel client-side truth.

4. **Data-driven content before content volume.**
   Species, abilities, skills, and specializations should be defined through registries/data whenever practical.

5. **Generic primitives before bespoke mechanics.**
   A reusable condition/action/resource system is preferred over species-specific Java classes.

6. **Vertical slices before breadth.**
   Four working Species and four working Skills are more valuable than fifteen partially implemented ones.

7. **Every milestone must leave the project in a runnable state.**

---


# 1.1 Architectural Separation Contract

This rule is the most important architectural constraint in Lifepath:

> **Java implements mechanics. Data defines content. Config defines balance.**

This is not a guideline. It is a design contract.

Every implementation decision should be classified into one of these three layers before code is written.

## Java — Mechanics

Java is responsible for reusable system behavior.

Examples of things that belong in Java:

```text
skill XP processing
level calculation engine
decay algorithm
cooldown service
resource system
condition evaluation
action execution
target selection
mob disposition engine
temperature simulation framework
encumbrance framework
network synchronization
persistence
registries
data loading
commands
UI logic
validation
migration
```

Java should answer questions like:

```text
How does a cooldown work?
How is XP awarded?
How is decay calculated?
How do we find entities in a radius?
How do we apply an attribute modifier?
How does a resource increase or decrease?
How are conditions evaluated?
How are character records persisted?
```

Java should NOT answer questions like:

```text
What is a Sylvian?
How much speed does Sylvian receive?
How long is Verdant Bloom's cooldown?
Which Skills does Blacksmith start with?
How much XP does Diamond Ore give?
At which level does Mining unlock Fortune +1?
```

Those belong elsewhere.

---

## Data — Content

Data defines concrete gameplay content using reusable mechanics.

Examples:

```text
Species definitions
Specialization definitions
Skill definitions
Ability definitions
Trait definitions
Condition definitions
Attunement definitions
milestone definitions
XP source definitions
mob relationship definitions
resource behavior definitions
unlock definitions
```

Example:

```text
Java mechanic:
"apply_attribute_modifier"

Data content:
Sylvian Forest Affinity uses
movement_speed +10%
armor +2
while biome has #minecraft:is_forest
```

Another example:

```text
Java mechanic:
"award skill XP for a validated block-break source"

Data content:
diamond_ore -> Mining XP source
ancient_debris -> Mining XP source
iron_ore -> Mining XP source
```

Adding a normal Species, Specialization, Skill milestone, or Ability should usually require **data changes only**, not a new Java class.

A new Java implementation is justified only when the content requires a mechanic that the engine genuinely cannot express yet.

When that occurs, the preferred process is:

```text
1. identify missing generic mechanic
2. implement reusable primitive in Java
3. expose it to data
4. define the actual content through data
```

Not:

```text
1. add SylvianSpecialCase.java
```

---

## Config — Balance

Config controls server/operator tuning.

Examples:

```text
global XP multiplier
decay enabled/disabled
decay grace period
decay rate multiplier
maximum skill level
cooldown multipliers
damage multipliers
resource gain/drain multipliers
anti-exploit thresholds
scan frequency
server performance limits
feature toggles
```

Config should answer:

```text
How strong?
How fast?
How often?
How far?
How expensive?
How punishing?
Is this system enabled?
```

Config should not define the identity of content where datapack/data definitions are more appropriate.

For example:

```text
GOOD:
config: globalSkillXpMultiplier = 0.8

GOOD:
data: diamond_ore grants 2.0 Mining XP

BAD:
Java: if block == DIAMOND_ORE award 2.0 XP
```

---

## Decision Test

Before implementing any feature, ask:

### Is this reusable system behavior?

Put it in **Java**.

### Is this a concrete Species/Skill/Ability/Specialization/etc.?

Put it in **Data**.

### Is this a tunable server balance value?

Put it in **Config**.

If a feature spans all three, split it across all three.

Example: Verdant Bloom

```text
Java:
generic AoE block-targeting
generic grow-block action
generic cooldown system

Data:
Sylvian ability definition
valid targets
radius
conditions
actions

Config:
optional server-wide ability cooldown multiplier
optional growth strength multiplier
```

---

## Forbidden Architectural Patterns

The following should be treated as architecture violations unless explicitly justified in an ADR:

```java
if (species == SYLVIAN) {
    ...
}
```

inside generic engine code.

```java
if (skill == MINING && block == DIAMOND_ORE) {
    xp += 2;
}
```

inside the core XP service.

```java
if (specialization == BLACKSMITH) {
    decay *= 0.7;
}
```

inside the generic decay algorithm.

```java
static final int VERDANT_BLOOM_COOLDOWN = 4800;
```

when the value should be content/config driven.

Likewise, giant switch statements over content IDs are a warning sign:

```java
switch (speciesId) {
    case "sylvian" -> ...
    case "iceborn" -> ...
    case "undead" -> ...
}
```

Generic registries and composable mechanics should replace this pattern.

---

## Milestone Enforcement

Every milestone report must include an architecture audit:

```text
Java mechanics added:
Data content added:
Config balance options added:
Hardcoded content exceptions:
```

The expected value for:

```text
Hardcoded content exceptions
```

is:

```text
None
```

If it is not `None`, the agent must explain why a reusable mechanic could not be used.

---

## Pull Request / Change Review Checklist

Before accepting a feature:

- [ ] No content identity is unnecessarily hardcoded in Java.
- [ ] New reusable behavior is implemented as a generic mechanic.
- [ ] Concrete content uses data definitions.
- [ ] Balance values are not buried in engine code.
- [ ] Server operators can tune appropriate values through config.
- [ ] Adding another similar content entry would not require copying Java code.
- [ ] Registries/data reload correctly validate the new content.
- [ ] Existing saved data remains compatible or migration is documented.

---

## Architecture Success Test

The architecture is healthy if the following task is possible:

> Add a new Species with three passive abilities and one active ability.

The expected implementation should mostly be:

```text
new data files
possibly new assets/localization
possibly config defaults
```

and **not**:

```text
four new Java power classes
changes to CharacterManager
changes to SkillService
changes to networking core
changes to persistence core
```

If adding ordinary content repeatedly requires changes to core Java services, the architecture should be reconsidered before adding more content.

---


# 1.2 Integration-First Compatibility Contract

Lifepath must assume from the beginning that a server may use other mods to implement the actual gameplay loop.

Examples:

```text
Overgeared for smithing/forging
Farmer's Delight-style mods for cooking
modded crops for Farming
modded ores for Mining
custom fishing mods
custom enchanting systems
technology mods
weapon/combat overhauls
```

Lifepath should enhance these systems where possible rather than replacing them.

The intended relationship is:

```text
External mod = gameplay/content provider
Lifepath    = character progression layer
```

Example:

```text
Overgeared provides:
forging stations
hammers
heated metal
forging recipes
equipment quality

Lifepath provides:
Smithing XP
Smithing level
Smithing aptitude
Smithing bonuses
Smithing milestones
Specialization modifiers
```

Lifepath should not require Overgeared, and Overgeared should not require Lifepath.

When both are installed, the experience should become richer automatically or through a small compatibility data pack/module.

---

## Compatibility Priority Order

When integrating with another mod, prefer this order:

```text
1. Standard Minecraft/Fabric events and APIs
2. Minecraft item/block/entity tags
3. Datapack-defined integration
4. Public API exposed by the external mod
5. Optional compatibility module
6. Mixins/hooks only when no stable alternative exists
```

Avoid direct dependency on another mod's internal implementation unless absolutely necessary.

---

## Tag-Driven Recognition

Lifepath should expose and consume semantic tags wherever possible.

Examples:

```text
#lifepath:smithing_tools
#lifepath:smithing_workstations
#lifepath:smithing_materials
#lifepath:mining_ores
#lifepath:farming_crops
#lifepath:fishing_rewards
```

A modpack author should be able to integrate another mod by adding entries to tags or data files.

For example:

```text
Overgeared hammer
        ↓
added to #lifepath:smithing_tools
        ↓
Lifepath recognizes relevant forging activity
```

The exact integration model may use richer event/data definitions, but the core principle remains:

> Compatibility should be declarative whenever possible.

---

## External Event Adapters

Some mods expose actions that cannot be recognized through vanilla events alone.

Lifepath should support optional adapters.

Conceptually:

```text
SmithingActivity
MiningActivity
FarmingActivity
FishingActivity
CraftingActivity
CombatActivity
```

Core progression should consume normalized Lifepath domain events:

```text
External mod event
      ↓
Compatibility adapter
      ↓
Lifepath SmithingActivity
      ↓
Skill XP service
```

This prevents the Skill system from depending directly on Overgeared, Create, Farmer's Delight, or any other mod.

Example:

```text
BAD

SkillXpService:
if (ModList.has("overgeared")) {
    inspect Overgeared internals...
}
```

Preferred:

```text
OvergearedCompat:
translate forging completion
into Lifepath SmithingActivity
```

The generic Skill service remains unaware of Overgeared.

---

## Optional Compatibility Modules

If deeper integration is required, use isolated compatibility code.

Suggested structure:

```text
compat/
├── vanilla/
├── overgeared/
├── farmersdelight/
└── ...
```

Rules:

- compatibility classes must not leak into generic engine services;
- missing external mods must never crash Lifepath;
- optional classes must not load when the target mod is absent;
- compatibility modules translate external mechanics into Lifepath mechanics;
- compatibility behavior should be configurable where useful.

---

## Datapack Integration Contract

Modpack authors should be able to add substantial compatibility without compiling Java.

Where practical, Lifepath data definitions should support:

```text
items
item tags
blocks
block tags
entities
entity tags
recipes
recipe types
loot sources
damage types
biomes
custom identifiers
activity weights
XP values
```

Example:

```text
A mod adds a new Mythril Ore.

Desired integration:
add Mythril Ore to a Lifepath-compatible ore tag/data definition

No Java code required.
```

---

## Graceful Degradation

Compatibility must fail gracefully.

If another mod:

- is not installed;
- changes an ID;
- removes an item;
- removes a recipe;
- changes an API;

then Lifepath should:

```text
skip unavailable integration
log a useful warning when appropriate
continue running
```

It should not corrupt character data or prevent server startup unless a configuration is fundamentally invalid.

---

## Compatibility Acceptance Test

For each core Skill, ask:

> Can a modpack replace the vanilla gameplay implementation while Lifepath still tracks the concept?

Examples:

```text
Smithing
Vanilla crafting replaced by Overgeared forging

Farming
Vanilla crops supplemented by modded crops

Mining
Modded ores and tools

Fishing
Custom fishing loot/mechanics
```

The answer should be "yes, as far as the external mod exposes enough observable behavior."

---

## Compatibility Architecture Audit

Every milestone report involving gameplay hooks must include:

```text
Vanilla event/API used:
Semantic tags used:
External-mod assumptions:
Optional adapters added:
Hardcoded external-mod IDs:
```

Expected value for:

```text
Hardcoded external-mod IDs
```

should normally be:

```text
None
```

unless contained inside a dedicated compatibility module or data file.

---

# 1.3 Zero-Confusion UX Contract

Lifepath must be understandable by a player with no wiki, no Discord explanation, and no previous RPG-system knowledge.

The practical UX target is:

> A five-year-old who can play Minecraft should understand what to press, what happened, and what to do next.

This does not mean childish visuals.

It means **extreme clarity**.

---

## Every Screen Must Answer Four Questions

A player looking at Lifepath should immediately understand:

```text
1. What am I?
2. What am I good at?
3. How do I improve?
4. What should I do next?
```

If a screen does not answer those questions clearly, it is incomplete.

---

## Progressive Disclosure

Do not present the entire simulation at once.

Default view:

```text
Species
Specialization
Top Skills
Current progression
Clear next action
```

Advanced information should appear through:

```text
hover
details button
advanced tab
expandable section
```

Never require the player to understand:

```text
decay multipliers
internal IDs
XP formulas
registry names
NBT
configuration terminology
```

to play normally.

---

## Plain-Language Labels

Prefer:

```text
"Mine ores to improve Mining"
```

over:

```text
"Gain Mining proficiency XP through valid extraction activities"
```

Prefer:

```text
"Protected for 2 days"
```

over:

```text
"Decay grace period: 47h 32m"
```

The precise value may still appear in advanced detail.

---

## Immediate Cause-and-Effect Feedback

When the player performs an important action, feedback should explain the result.

Examples:

```text
Mining +12 XP
Mining reached level 30
New bonus: 5% chance for extra raw ore
```

or:

```text
Verdant Bloom ready
Press [G] to grow nearby plants
```

or:

```text
Too hot!
Find ice or leave the heat.
```

Avoid unexplained icons, silent penalties, and hidden rules.

---

## One Primary Action Per Decision

Character creation should not open with twenty equally important controls.

Example onboarding:

```text
Step 1
Choose your Species

Step 2
Choose your Specialization

Step 3
See your starting strengths

Step 4
Start playing
```

Each step should explain its consequence before confirmation.

---

## Species Selection UX

Each Species card should show:

```text
Name
one-sentence identity
Strengths
Weaknesses
Active ability
Difficulty indicator only if objectively useful
```

Example:

```text
SYLVIAN

Nature-born and strongest around forests.

GOOD AT
✓ Forest movement
✓ Growing plants
✓ Cleansing harmful effects

WATCH OUT
! Deserts weaken you
! Undead hurt more

ABILITY
Verdant Bloom
Grow nearby plants every 4 minutes
```

No player should need to read a paragraph to understand the choice.

---

## Specialization Selection UX

The screen should explicitly say:

```text
This is your starting focus.
It does NOT lock you out of other Skills.
```

Then:

```text
BLACKSMITH

Starts better at:
✓ Smithing
✓ Engineering

Learns Smithing faster.
Forgets high-level Smithing more slowly.

You can still become good at Mining, Farming, Fishing, or anything else.
```

This is especially important because players will assume "class" means restriction unless told otherwise.

---

## Skill UX

Every Skill detail should include a clear action statement:

```text
MINING — Level 32

HOW TO IMPROVE
Mine valuable natural blocks and ores.

CURRENT BONUS
+16% mining speed
5% chance for bonus raw ore

NEXT
Level 40
+20% mining speed
```

The player should never have to guess what awards XP.

---

## Decay UX

Decay must be explained without using threatening language.

Prefer:

```text
Mastery maintenance

Your high Mining skill stays sharp while you keep mining.

Protected for:
2 days

Lowest it can fall:
Level 60
```

Do not show a mysterious red downward arrow with no explanation.

---

## Error Prevention

The UI should prevent mistakes before they happen.

Examples:

```text
confirmation before resetting a character
clear warning before changing irreversible choices
disabled buttons with visible reason
preview before committing a Species/Specialization
```

Never rely on error messages after destructive actions if the mistake can be prevented beforehand.

---

## Visual Hierarchy

Priority order:

```text
1. Current identity
2. Important actionable state
3. Progress
4. Next milestone
5. Advanced numbers
```

The UI should not give every number equal visual weight.

---

## Accessibility

At minimum:

- do not rely on color alone;
- icons must have labels/tooltips;
- text must remain readable at common GUI scales;
- important state must have text equivalents;
- keybindings must be visible and rebindable;
- animations must not be required to understand state.

---

## UX Acceptance Test

For every major screen, test with someone who has not read the design docs.

Give no explanation.

They should be able to answer:

```text
What Species am I?
What Specialization am I?
What should I do to level this Skill?
What happens at the next level?
Why am I currently debuffed?
What button activates my ability?
```

If they cannot answer within a few seconds, simplify the UI.

---

## AI Agent UX Review

Every milestone containing player-facing UI must include:

```text
Primary user goal:
Primary action:
Information visible immediately:
Information hidden behind details:
Possible player confusion:
How confusion was prevented:
External documentation required:
```

Expected value for:

```text
External documentation required
```

for ordinary gameplay should be:

```text
None
```

External docs may explain depth, APIs, modpack integration, or advanced mechanics, but they must not be required to understand normal play.

---

# 2. Timeline Overview

The roadmap is divided into ten major milestones.

```text
M0  Project Foundation
M1  Character Core
M2  Skill Framework
M3  Progression, Aptitudes & Decay
M4  Ability Engine
M5  First Vertical Slice
M6  Character UI & Player Feedback
M7  Hardening, Multiplayer & Balance Infrastructure
M8   Core Content Expansion
M9   Advanced Character Systems
M10  1.0 Stabilization
M10.5 Public Beta / Release Candidate
M11  Distribution, Branding & CurseForge Release
```

Recommended execution dependency:

```text
M0
 ↓
M1
 ↓
M2
 ↓
M3
 ↓
M4
 ↓
M5
 ↓
M6
 ↓
M7
 ↓
M8
 ↓
M9
 ↓
M10
 ↓
M10.5
 ↓
M11
```

Some research and design work may happen ahead of schedule, but production implementation should respect this dependency chain.

---

# 3. M0 — Project Foundation

## Objective

Create a clean, stable Fabric mod project that can support Lifepath's systems without immediately coupling the codebase to any individual Species or Skill.

## Scope

This milestone is infrastructure only.

No meaningful gameplay progression is required yet.

## Deliverables

### Project bootstrap

- Fabric 1.21.1 project initialized.
- Java version and Gradle configuration fixed and documented.
- Mod ID: `lifepath`.
- Stable package namespace selected.
- Basic mod initialization verified in both client and dedicated server environments.

### Suggested top-level package boundaries

```text
lifepath/
├── character/
├── species/
├── specialization/
├── skill/
├── aptitude/
├── ability/
├── trait/
├── condition/
├── attunement/
├── resource/
├── relation/
├── event/
├── network/
├── command/
├── config/
└── client/
```

### Infrastructure

- logging conventions;
- config loading;
- registry bootstrap;
- reload lifecycle;
- basic networking registration;
- command registration;
- test/dev utilities;
- version constants;
- serialization utilities.

### Initial commands

At minimum:

```text
/lifepath version
/lifepath reload
```

## Required Engineering Skills

- Fabric API lifecycle
- Gradle
- Java records/classes/interfaces
- codecs/serialization
- Minecraft registries
- server/client separation
- command registration
- resource reload listeners
- project structure and dependency management

## Tests

- client launches;
- dedicated server launches;
- mod initializes once;
- reload executes without errors;
- no client-only class is loaded on dedicated server;
- configuration can be read and reloaded safely.

## Exit Gate

M0 is complete only when:

- the project starts cleanly;
- dedicated server compatibility is confirmed;
- config, registry, reload, networking, and command foundations exist;
- there is no gameplay-specific architecture blocking later data-driven design.

---

# 4. M1 — Character Core

## Objective

Create the authoritative persistent representation of a Lifepath character.

This milestone establishes **what data a character has**, not yet how all of that data changes.

## Core model

```text
Player
└── CharacterData
    ├── Species
    ├── Specialization
    ├── Skills
    ├── Aptitudes
    ├── Traits
    ├── Conditions
    ├── Attunements
    ├── Resources
    ├── Cooldowns
    └── Unlocks
```

## Deliverables

### Character data model

Implement a versioned persistent structure for:

```text
speciesId
specializationId

skills
aptitudes

traits
conditions
attunements

resources
cooldowns
unlocks
```

### Persistence

Character data must survive:

- logout/login;
- server restart;
- death;
- respawn;
- dimension change.

Explicitly define what should and should not reset on death.

### Data versioning

Persist a schema version.

Example:

```text
dataVersion: 1
```

Provide a migration entrypoint from the beginning, even if no migrations exist yet.

### Character manager/service

Centralized access:

```text
getCharacter(player)
saveCharacter(player)
syncCharacter(player)
initializeCharacter(player)
```

Avoid scattered player-data access throughout feature code.

### Base registries

Introduce registry/data models for:

- Species;
- Specialization;
- Skill.

They do not need full mechanics yet.

### Administration

Add:

```text
/lifepath character inspect <player>
/lifepath character reset <player>
```

Reset must be development/admin-only and protected by permissions.

## Required Engineering Skills

- Minecraft player persistence
- NBT or codec-based serialization
- Fabric networking
- player lifecycle events
- schema migration
- immutable vs mutable state design
- UUID/player identity handling
- defensive data loading

## Tests

- fresh player receives valid default data;
- data survives logout/login;
- data survives server restart;
- invalid/missing IDs fail safely;
- deleted content definitions do not corrupt player saves;
- dimension changes preserve state;
- death/respawn follows documented rules.

## Exit Gate

M1 is complete only when character data can be safely stored, loaded, inspected, migrated, and synchronized.

No Skill XP implementation should begin before this is stable.

---

# 5. M2 — Skill Framework

## Objective

Build the generic Skill system without yet adding decay or complex abilities.

## Initial production Skills

Only four Skills are required in this milestone:

```text
Mining
Farming
Smithing
Fishing
```

These are chosen because together they exercise several different Minecraft event types.

## Skill model

Each player Skill should support:

```text
skillId
xp
level
highestLevel
lastMeaningfulUse
protectedFloor
aptitude
```

## Deliverables

### Skill registry

A Skill definition should describe at least:

- ID;
- display name;
- category;
- maximum level;
- level curve;
- milestone definitions;
- XP rules;
- optional passive scaling.

### XP service

Central API:

```text
awardXp(player, skill, amount, source)
setXp(...)
setLevel(...)
getLevel(...)
```

All XP modifications pass through one authoritative service.

### Level curve

Make the curve configurable/data-driven.

Do not scatter:

```java
level * level * 100
```

throughout the codebase.

### Gameplay hooks

Implement meaningful XP sources using normalized Lifepath activity events so vanilla and modded gameplay can feed the same progression pipeline.

The generic Skill system must not assume vanilla crafting/mining/farming/fishing is the only implementation.

Implement meaningful XP sources for:

#### Mining

Reward based on block/resource significance.

#### Farming

Reward mature crop interaction, not immature harvesting.

#### Smithing

Reward meaningful crafting/smithing activity.

Smithing must be designed so an external forging mod can become the primary source of Smithing activity through tags, datapack definitions, public APIs, or an isolated compatibility adapter.

Overgeared is the reference integration scenario: Lifepath should track Smithing progression around an external forging loop without replacing that loop.

#### Fishing

Reward completed catches, with room for rarity-based XP.

### Anti-exploit metadata

Every XP event should carry enough context to later support:

- repeated-action detection;
- source classification;
- diminishing returns.

## Required Engineering Skills

- Minecraft gameplay events
- loot/block break hooks
- crafting/smithing hooks
- fishing events
- configurable XP curves
- event normalization
- server-side validation
- clean domain/service APIs

## Tests

- XP cannot be awarded client-side;
- level calculation is deterministic;
- level does not exceed maximum;
- invalid skill IDs fail safely;
- XP survives restart;
- each initial Skill has at least one real gameplay XP source;
- ordinary non-qualifying actions grant no XP.

## Exit Gate

M2 is complete when the four Skills can be progressed normally and reliably in survival gameplay.

---

# 6. M3 — Progression, Aptitudes & Decay

## Objective

Turn Skills from simple XP bars into Lifepath's defining progression system.

## Deliverables

### Aptitudes

Supported baseline grades:

```text
D
C
B
A
S
```

Aptitudes modify:

- XP gain;
- decay rate.

Suggested initial defaults:

```text
D   XP x0.70   Decay x1.25
C   XP x0.90   Decay x1.10
B   XP x1.00   Decay x1.00
A   XP x1.25   Decay x0.80
S   XP x1.50   Decay x0.60
```

All numbers are configuration, not engine constants.

### Specialization model

Implement Specializations as presets that can define:

- starting Skills;
- Aptitudes;
- XP modifiers;
- decay modifiers;
- protected floors;
- signature metadata.

Initial Specializations:

```text
Miner
Farmer
Blacksmith
Fisherman
```

### Skill decay

Implement:

- grace period;
- level bands;
- configurable decay rates;
- aptitude modifiers;
- specialization modifiers;
- protected floors.

Suggested initial bands:

```text
0-25     no decay
26-50    extremely low
51-75    low
76-90    moderate
91-100   high maintenance
```

### Lazy decay

Decay is calculated based on elapsed time, not every tick.

Relevant triggers:

- login;
- skill read/update;
- low-frequency maintenance;
- admin inspection.

### Anti-exploit / diminishing returns

Implement a first production version.

Examples:

- repeated identical block mining;
- repeated identical recipe crafting;
- low-value spam actions.

The anti-exploit system should be generic enough to later support other Skills.

## Required Engineering Skills

- time-based state calculation
- progression math
- domain modeling
- anti-abuse mechanics
- cache/window design
- config-driven modifiers
- balancing instrumentation

## Tests

- aptitude changes XP correctly;
- decay never pushes below protected floor;
- no-decay range behaves correctly;
- offline elapsed time is handled deterministically;
- grace period works;
- repeated actions trigger diminishing returns;
- normal varied gameplay is not unfairly penalized.

## Exit Gate

M3 is complete when players can meaningfully specialize through use and high mastery requires maintenance without becoming punitive.

---

# 7. M4 — Ability Engine

## Objective

Create the generic engine that powers Species, Traits, Conditions, and later Attunements.

This milestone is architecture-critical.

## Core model

An ability should conceptually compose:

```text
Trigger
+ Conditions
+ Target
+ Actions
+ Cost
+ Cooldown
+ Resource interaction
```

## Deliverables

### Ability types

Support at minimum:

- passive;
- active;
- event-triggered.

### Generic conditions

Initial primitives:

```text
biome_tag
dimension
block_nearby
entity_nearby
daylight
night
health_threshold
inventory_contains
equipment_contains
submerged
on_fire
weather
skill_level
resource_threshold
```

### Generic actions

Initial primitives:

```text
apply_effect
remove_effect
modify_attribute
damage
heal
grow_blocks
freeze_water
highlight_entities
consume_item
modify_resource
play_sound
spawn_particle
```

### Cooldown service

Centralized cooldown tracking:

```text
abilityId
playerId
remaining/expiry
```

Cooldowns must persist where intended and synchronize correctly.

### Resource framework

Generic player resources should support:

- current value;
- min/max;
- regeneration/drain;
- server authority;
- UI synchronization.

This is required later for Iceborn temperature and other Species-specific meters.

### Data loading

Abilities should be definable through data/JSON wherever practical.

The engine may expose Java extension points for mechanics that genuinely cannot be expressed using standard primitives.

## Required Engineering Skills

- codecs/data loading
- event buses
- composition patterns
- generic action/condition architecture
- cooldown design
- packet synchronization
- server/client responsibility boundaries
- extensibility/API design

## Tests

Create synthetic test abilities that exercise:

- one passive condition;
- one active cooldown;
- one AoE target;
- one resource condition;
- one state-changing action.

## Exit Gate

M4 is complete when new simple abilities can be created primarily through data composition without adding one bespoke Java class per ability.

---

# 8. M5 — First Vertical Slice

## Objective

Prove the entire Lifepath architecture through real playable content.

This is the first milestone where the mod should feel like Lifepath rather than an engine demo.

## Species

Implement exactly:

```text
Human
Sylvian
Iceborn
Undead
```

## Specializations

Implement:

```text
Miner
Farmer
Blacksmith
Fisherman
```

## Skills

Production-ready:

```text
Mining
Farming
Smithing
Fishing
```

---

## 8.1 Human

Purpose:

- control/baseline species;
- prove that a player can participate in all progression without innate supernatural systems.

Human should intentionally remain simple.

---

## 8.2 Sylvian

Required mechanics:

### Forest Affinity

Tests:
- biome conditions;
- passive attributes.

### Grass/Vegetation Movement

Tests:
- environmental context.

### Nature Purification

Tests:
- nearby/interacted block conditions;
- effect removal.

### Verdant Bloom

Tests:
- active ability;
- cooldown;
- AoE;
- world interaction.

### Arid Intolerance

Tests:
- hostile biome;
- periodic effects.

### Necrotic Vulnerability

Tests:
- attacker/entity classification;
- damage modification.

---

## 8.3 Iceborn

Required mechanics:

### Temperature resource

Tests:
- generic resource service;
- environmental accumulation/drain;
- threshold effects.

### Cold affinity

Tests:
- cold biome bonuses.

### Heat hazards

Tests:
- fire/campfire/magma/lava proximity.

### Ice coolant

Tests:
- inventory condition;
- resource mitigation.

### Frost ability

Tests:
- active cooldown;
- area world modification;
- temporary block behavior.

---

## 8.4 Undead

Required mechanics:

### Restricted diet

Tests:
- food validation;
- mod/tag extensibility.

### Mob disposition

Tests:
- entity relationship service.

### Death Sight

Tests:
- active ability;
- night condition;
- area scan;
- player-private rendering/effect.

---

## Vertical Slice Validation

At this stage the team should ask:

- Did Species require bespoke engine hacks?
- Did Specializations remain separate from Species?
- Did Skills remain reusable?
- Did Aptitudes matter?
- Did decay work without annoying normal players?
- Can admins tune values without recompilation?
- Does the system remain understandable in multiplayer?

## Exit Gate

M5 is complete only when the four Species and four Specializations are playable together on a real server and exercise all major core systems.

---

# 9. M6 — Character UI & Player Feedback

## Objective

Make Lifepath understandable without requiring players to inspect config files or read external documentation.

## Deliverables

### Character screen

Display:

- Species;
- Specialization;
- active Conditions;
- Attunements;
- significant Traits.

### Skills screen

Display for each Skill:

- level;
- rank;
- XP progress;
- Aptitude;
- current bonuses;
- next milestone;
- protected floor;
- decay state.

### Skill detail

Example:

```text
SMITHING — 81
Master

Aptitude: A

Current bonuses:
+16% repair efficiency
+12% material recovery

Next milestone:
Level 85 — improved high-quality output chance

Decay:
Grace period remaining: 31h

Protected floor:
30
```

### HUD elements

Support only systems that genuinely need persistent HUD presence:

- active resources;
- important cooldowns;
- major temporary states.

Do not create permanent HUD clutter.

### Feedback

Players should receive concise feedback for:

- level-up;
- milestone unlock;
- specialization assignment;
- condition gained/lost;
- significant decay where appropriate.

## Required Engineering Skills

- Minecraft client screens
- widgets/layout
- packet synchronization
- text localization
- HUD rendering
- accessibility/readability
- state caching
- server-authoritative UI design

## Tests

- UI never mutates authoritative state directly;
- reconnect refreshes correct state;
- dimension change preserves UI state;
- hidden/admin-only Species do not leak unintentionally;
- UI handles missing/deleted definitions gracefully.

## Exit Gate

M6 is complete when players can understand their character state entirely from in-game tools.

A new player who has never read Lifepath documentation must be able to identify:
- what they are;
- what they are good at;
- how to improve;
- what happens next;
- why a major buff/debuff is active;
- how to use an available active ability.

If ordinary gameplay requires a wiki explanation, M6 has not passed.

---

# 10. M7 — Hardening, Multiplayer & Balance Infrastructure

## Objective

Make the engine safe for sustained multiplayer use before expanding content.

## Deliverables

### Compatibility correctness

Use at least one real external gameplay mod as a compatibility validation target.

Recommended reference case:

```text
Overgeared -> Smithing
```

Validate that Lifepath can consume external Smithing activity without embedding Overgeared-specific assumptions into generic Skill services.

Also test modded:
- ores;
- crops;
- tools;
- recipes;

through tags/data where feasible.

### Multiplayer correctness

Test:

- simultaneous XP gains;
- rapid reconnects;
- death/respawn;
- dimension travel;
- server restart;
- multiple players activating AoE abilities;
- cooldown synchronization.

### Performance

Profile:

- world scans;
- nearby-block queries;
- nearby-entity queries;
- resource updates;
- passive abilities;
- decay calculations;
- network packets.

Set explicit budgets where practical.

### Balance configuration

Ensure all high-risk tuning values can be changed without recompiling:

- XP rates;
- level thresholds;
- cooldowns;
- radii;
- decay;
- damage multipliers;
- environmental thresholds;
- resource gain/drain.

### Debug tooling

Add useful development commands:

```text
/lifepath debug character <player>
/lifepath debug ability <player> <ability>
/lifepath debug skill <player> <skill>
/lifepath cooldown clear <player>
```

### Data validation

On reload/startup, detect:

- duplicate IDs;
- missing referenced Skills;
- missing referenced abilities;
- invalid ranges;
- impossible thresholds;
- cycles where relevant.

Fail loudly but safely.

## Required Engineering Skills

- profiling
- concurrency awareness
- event frequency analysis
- server optimization
- diagnostics
- structured logging
- multiplayer testing
- config validation

## Exit Gate

M7 is complete when the vertical slice is stable enough to run continuously on a multiplayer server without requiring developer babysitting.

---

# 11. M8 — Core Content Expansion

## Objective

Expand Lifepath using the proven engine without changing its fundamental architecture.

## Species Expansion Candidates

### Elemental / Affinity

```text
Enderian
Amphibian
Dragonborn
```

### Cursed / Altered

```text
Automaton
Hellborn
Anima
```

### Physical

```text
Dwarf
Goliath
```

Special Species remain optional at this stage.

## Specialization Expansion Candidates

```text
Lumberjack
Hunter
Engineer
Herbalist
Scholar / Chronicler
Alchemist
Cook
Explorer
Laborer
```

## Skill Expansion Candidates

Add only when supported by substantial gameplay loops:

```text
Woodcutting
Hunting
Engineering
Alchemy
Cooking
Enchanting
Melee
Archery
Defence
Athletics
Scholarship
```

Do not add all Skills automatically.

Each candidate must answer:

1. What meaningful actions generate XP?
2. What prevents trivial grinding?
3. What does progression improve?
4. Does this Skill create a meaningful playstyle?
5. Is this distinct enough from an existing Skill?

## Required Engineering Skills

Primarily content implementation using already-proven engine primitives.

New Java engine work should be exceptional and justified.

## Exit Gate

M8 is complete when the majority of intended normal-play Species and Specializations exist without destabilizing the architecture.

---

# 12. M9 — Advanced Character Systems

## Objective

Introduce systems that change the character during play rather than being selected only at character creation.

These systems must be built after the base engine is proven.

## 12.1 Conditions

Initial targets:

```text
Vampirism
Lycanthropy
```

Conditions may define:

- abilities;
- vulnerabilities;
- diet changes;
- transformations;
- resources;
- progression stages;
- cures;
- acquisition rules.

## 12.2 Attunements

Initial targets:

```text
Air
Earth
Lightning
```

Attunements are acquired affinities and should remain distinct from Species.

## 12.3 Encumbrance

Implement shared carrying/load mechanics.

Baseline concept:

```text
0-40%    normal
40-70%   light pressure
70-90%   movement penalty
90-100%  heavy penalty
```

Species and Specializations can modify capacity and penalties.

### Expected users

```text
Goliath
Laborer
possibly Dwarf
```

## 12.4 Narrative unlocks

Support:

- quest-based unlock;
- item-based unlock;
- admin command;
- world event;
- advancement/event hook.

## 12.5 Special Species

Candidates:

```text
Phoenix
Phantom
Celestial
```

They should use the same engine and not require player-name hardcoding.

## Required Engineering Skills

- compositional state systems
- transformations
- progression trees/stages
- acquisition/removal flows
- interaction between multiple character layers
- migration compatibility

## Exit Gate

M9 is complete when advanced character changes coexist safely with Species, Skills, Specializations, and persistence.

---

# 13. M10 — 1.0 Stabilization

## Objective

Freeze the public behavior of the first stable Lifepath release.

## Deliverables

### API freeze

Stabilize:

- IDs;
- data paths;
- config keys;
- command names;
- public extension points;
- saved data schema expectations.

### Migration strategy

Document:

- save compatibility;
- data version upgrades;
- removed IDs;
- renamed IDs;
- fallback behavior.

### Documentation

Required:

```text
README.md
GAMEDESIGN.md
TIMELINE.md
CONFIGURATION.md
DATAPACK_API.md
ADMIN_COMMANDS.md
MIGRATIONS.md
```

### Testing

Perform:

- fresh-world test;
- existing-world upgrade test;
- dedicated server test;
- multiplayer soak test;
- death/respawn test;
- dimension travel test;
- reload test;
- datapack override test;
- corrupted/missing data recovery test.

### Release criteria

1. No known progression duplication exploits.
2. No known save corruption.
3. No client/server desynchronization in core systems.
4. No mandatory Origins/Apoli/KubeJS dependency.
5. Core configuration documented.
6. Data-driven extension path documented.
7. Server can operate continuously without manual state repair.


## Exit Gate

M10 is complete when the mod is internally stable, documented, migration-aware, and feature-complete enough to enter external testing.

---

# 14. M10.5 — Public Beta / Release Candidate

## Objective

Validate Lifepath with real players who did not help design the system.

This milestone exists primarily to validate:
- onboarding clarity;
- zero-confusion UX;
- progression clarity;
- multiplayer stability;
- compatibility expectations;
- real-world exploit and balance issues.

## Deliverables

### Public beta build

Prepare an RC/Beta build that is stable enough for invited or public testing.

### Real player validation

Test with players who have **not** read the design docs.

Without verbal coaching, they should be able to:

- create a character;
- understand Species vs Specialization;
- identify what their Skills are;
- understand how to level at least one Skill;
- identify what happens at the next milestone;
- use an active ability;
- understand a visible buff/debuff;
- understand, at a basic level, what decay means.

### Feedback collection

Collect structured feedback in categories:

```text
confusion
bugs
balance
performance
compatibility
missing information
UI/UX friction
```

### Beta triage

Categorize all findings into:

```text
must-fix before 1.0
should-fix soon
post-1.0 backlog
not a bug / intended
```

### Release candidate criteria

An RC is acceptable only if:

- no known save corruption exists;
- no core progression desync exists;
- no critical onboarding confusion remains unresolved;
- no major multiplayer exploit remains unresolved;
- the release build is representative of what will ship.

## Required Engineering Skills

- QA triage
- bug reproduction
- player observation
- UX testing
- release candidate management
- multiplayer validation
- issue prioritization

## Tests

- fresh player onboarding test;
- veteran player migration/upgrade test;
- no-explanation UX test;
- server operator install test;
- optional integration test;
- multiplayer soak test;
- reconnect/restart test.

## Exit Gate

M10.5 is complete only when real users can reach the core gameplay loop without external explanation and no release-blocking issues remain open.

---

# 15. M11 — Distribution, Branding & CurseForge Release

## Objective

Turn Lifepath from a technically complete mod into a publishable, moderated, player-facing public release.

This milestone covers packaging, release process, documentation, public-facing assets, and platform readiness.

## Deliverables

### Release engineering

- reproducible release JAR build;
- release build from clean checkout;
- versioning strategy;
- changelog generation;
- CI pipeline for compile/test/build;
- release workflow for tagged versions;
- dependency metadata verified.

### Mod metadata

Verify and finalize:

- `fabric.mod.json`
- mod icon
- version string
- loader/game dependencies
- authors
- contact/support links
- license
- issue tracker link
- source link

### Player-facing documentation

Provide:

- player-facing `README.md`
- install instructions
- features overview
- quick-start / getting started
- compatibility notes
- FAQ
- support / bug-report link

### CurseForge readiness

Prepare:

- project summary;
- full project description;
- release changelog;
- supported versions/loader metadata;
- categories/tags;
- initial beta/release upload package.

### Visual branding assets

Prepare:

- project icon / profile image;
- CurseForge banner;
- gallery screenshots;
- optional key feature card image.

These assets must be **Minecraft-native, clean, readable, and anti-slop by design**.

See the issue backlog for asset quality rules.

### Compatibility validation

Before release, validate at least:

- vanilla-only install;
- dedicated server install;
- existing-world upgrade path;
- missing optional-mod graceful degradation;
- one real optional integration scenario, with **Overgeared as the reference Smithing case**.

## Required Engineering Skills

- release engineering
- CI/CD
- packaging
- metadata management
- public documentation
- platform compliance
- QA verification
- product presentation

## Tests

- release JAR built from clean environment;
- release JAR works outside dev environment;
- fresh client install test;
- dedicated server install test;
- migration test;
- missing dependency graceful degradation test;
- CurseForge metadata completeness review;
- screenshot and asset quality review;
- README "new player" readability review.

## Exit Gate

M11 is complete only when all of the following are true:

```text
[ ] release JAR builds from clean checkout
[ ] release JAR works on fresh Fabric client
[ ] release JAR works on dedicated server
[ ] no required Origins/Apoli/KubeJS dependency
[ ] existing world upgrade tested
[ ] character data migration tested
[ ] optional integrations fail gracefully
[ ] Overgeared compatibility scenario tested
[ ] no hardcoded content exceptions
[ ] Java = mechanics
[ ] Data = content
[ ] Config = balance
[ ] normal gameplay requires no wiki
[ ] project icon finished
[ ] screenshots finished
[ ] CurseForge banner finished
[ ] player-facing README finished
[ ] English summary finished
[ ] English description finished
[ ] license selected
[ ] changelog finished
[ ] support/issues URL available
[ ] CurseForge metadata correct
[ ] Beta/Release JAR uploaded
[ ] moderation-ready review passed
```

M11 complete = Lifepath public release candidate is ready for CurseForge submission.

---

---

# 16. Skill Rollout Matrix

This matrix defines when game Skills should become production-ready.

| Skill | First Required Milestone | Purpose |
|---|---:|---|
| Mining | M2 | Proves block/resource progression |
| Farming | M2 | Proves state-aware block progression |
| Smithing | M2 | Proves crafting/value progression |
| Fishing | M2 | Proves event/loot progression |
| Woodcutting | M8 | Gathering expansion |
| Hunting | M8 | Entity/combat gathering |
| Engineering | M8 | Advanced crafting |
| Alchemy | M8 | Potion/process progression |
| Cooking | M8 | Food/value progression |
| Enchanting | M8 | Knowledge/value progression |
| Melee | M8 | Combat progression |
| Archery | M8 | Ranged combat progression |
| Defence | M8 | Damage-response progression |
| Athletics | M8 | Movement/physical progression |
| Scholarship | M8 | Knowledge/research progression |

**M8-3 delivery record** (accuracy pass): Woodcutting, Hunting, Engineering,
Cooking, Archery, Defence, Athletics, Scholarship shipped; **Melee rejected**
(subsumed by Athletics' all-combat feed), **Enchanting rejected** (activity
too rare — fails the frequency gate), **Alchemy deferred** (no brewing-station
attribution primitive). Foraging shipped in M8 alongside as an existing skill.
Shipped skill inventory: `docs/API.md` §6.

---

# 17. Engineering Capability Timeline

The coding agent should acquire/prove capabilities in roughly this order.

## Phase A — Foundation

```text
Fabric lifecycle
Gradle
registries
serialization
resource reload
server/client separation
commands
basic networking
```

## Phase B — Persistent Domain Model

```text
player persistence
schema versioning
safe defaults
migration hooks
sync snapshots
```

## Phase C — Progression

```text
gameplay event hooks
XP services
level curves
anti-exploit rules
time-based decay
aptitudes
specialization modifiers
```

## Phase D — Mechanics Engine

```text
generic conditions
generic actions
targets
cooldowns
resources
data-driven ability loading
```

## Phase E — Client

```text
screens
HUD
localization
networked state display
```

## Phase F — Production

```text
profiling
multiplayer testing
validation
debugging
migration safety
documentation
extension API
```

---

# 18. AI Agent Execution Contract

The AI coding agent should follow these rules:

### Before starting a milestone

- read `GAMEDESIGN.md`;
- read this timeline;
- inspect current repository state;
- identify dependencies from earlier milestones;
- avoid implementing later milestone systems prematurely.

### During a milestone

- keep changes scoped;
- enforce **Java = mechanics, Data = content, Config = balance**;
- classify every new feature across those three layers before implementation;
- prefer reusable primitives;
- add tests alongside infrastructure;
- document durable architecture decisions;
- never hardcode ordinary Species/Skill/Specialization/Ability identity into generic services;
- never bury tunable balance values inside engine code;
- avoid content-specific shortcuts in the engine.

### At milestone completion

Produce a short report containing:

```text
Implemented
Not implemented
Known limitations
Tests performed

Java mechanics added
Data content added
Config balance options added
Hardcoded content exceptions

Migration impact
Exit gate status
```

`Hardcoded content exceptions` should normally be `None`.

The milestone is not complete simply because code compiles.

---

# 19. Recommended First Execution Order

For the first development cycle:

```text
1. Finish M0 completely.
2. Finish M1 completely.
3. Implement M2 with exactly four Skills.
4. Implement M3 before adding more Skills.
5. Build M4 as a reusable ability framework.
6. Use M5 to prove the architecture.
7. Build UI only after server state is stable.
8. Harden before expanding content.
```

The most important architectural checkpoint is the end of **M5**.

If M5 works without substantial species-specific hacks, the project is ready to scale.

If M5 requires repeated exceptions and hardcoded mechanics, stop content expansion and repair the engine before continuing.

---

# 20. Definition of Success

The roadmap succeeds when a player can eventually say:

> I am an Iceborn who started as a Fisherman, became an expert Smith through actual play, lost some edge after months away from the forge, retained the fundamentals because of my background, acquired a Lightning attunement later, and now plays differently from another Iceborn Fisherman.

Every part of that sentence should correspond to independent, composable Lifepath systems.

That is the standard the architecture should be built to support.
