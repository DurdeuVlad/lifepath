# Lifepath — Game Design

> Status: Foundational design document  
> Target: Minecraft 1.21.1 / Fabric  
> Project type: Independent character progression mod  
> Design direction: RimWorld-inspired progression, data-driven species, specialization presets, skill use and decay

---

## 1. Project Direction

**Lifepath** is not an Origins addon and is not intended to be an Origins-compatible content pack.

It is an independent Minecraft character progression framework built from the ground up.

The old `origins-rustic` project remains useful as:

- a gameplay idea bank;
- legacy specification;
- source material for species concepts;
- reference for professions, balance experiments, economy design, and server gameplay goals.

However, Lifepath should not depend on Origins, Origins Classes, Apoli, OriginJS, or KubeJS for its core character mechanics.

The new framework should be implemented as a standalone Fabric mod.

The fundamental player model is:

```text
CHARACTER
│
├── Species
│   └── what the character biologically / metaphysically IS
│
├── Specialization
│   └── what the character has CHOSEN to focus on
│
├── Skills
│   └── how GOOD the character actually is at activities
│
├── Aptitudes
│   └── how easily the character learns and maintains skills
│
├── Traits
│   └── innate or acquired modifiers
│
├── Conditions
│   └── vampirism, lycanthropy, curses, transformations, etc.
│
├── Attunements
│   └── air, earth, lightning, and similar acquired affinities
│
└── Abilities
    └── active/passive mechanics granted by the systems above
```

This separation is the core of the entire design.

---

## 2. Core Design Principles

These principles should remain stable even if individual mechanics change.

1. **Species defines biology, not profession.**
2. **Specialization defines aptitude, not permission.**
3. **Doing an activity is how the player becomes good at it.**
4. **High mastery requires maintenance.**
5. **Java implements mechanics; data defines content; configuration defines balance.**
6. **The server is authoritative over progression and character state.**
7. **Systems should compose instead of being hardcoded around individual species or classes.**
8. **Character identity should emerge from combinations instead of one rigid class choice.**
9. **Gameplay bonuses must reward meaningful play, not trivial automation or repetitive exploits.**
10. **Every important system must be visible and understandable in-game.**

---

## 3. Character Composition

Character identity is produced by combining independent systems:

```text
Character =
    Species
  + Specialization
  + Skills
  + Aptitudes
  + Equipment
  + Traits
  + Conditions
  + Attunements
```

Example:

```text
Character A

Species: Dwarf
Specialization: Blacksmith

Mining:      43
Smithing:    91
Engineering: 62

Condition: Vampirism
Attunement: None
```

versus:

```text
Character B

Species: Dwarf
Specialization: Explorer

Mining:    72
Smithing:  13
Athletics: 84

Condition: None
Attunement: Lightning
```

Both are Dwarves, but they play completely differently.

This is preferable to defining every meaningful character archetype as a separate Origin.

---

## 4. Species

Species represents relatively permanent biological, supernatural, or metaphysical properties.

Species may influence:

- environmental affinity;
- diet;
- temperature;
- healing;
- innate attributes;
- movement;
- body characteristics;
- damage vulnerabilities;
- mob relations;
- resource bars;
- passive abilities;
- active abilities.

Species should generally **not** directly grant vocational mastery such as:

- Fortune because the player mines;
- improved crop yield because the player farms;
- superior enchanting;
- superior crafting quality.

Those effects belong primarily to Skills and Specializations.

### 4.1 Initial Species Catalogue

#### Elemental / Affinity Species

- **Sylvian** — nature energy affinity.
- **Iceborn** — ice and cold affinity.
- **Enderian** — void affinity.
- **Amphibian** — aquatic affinity.
- **Dragonborn** — fire affinity.

#### Cursed / Altered Beings

- **Automaton** — living construct / life inside a nonliving body.
- **Undead** — dead being cursed or forced to remain alive.
- **Hellborn** — demonic or infernal affinity.
- **Anima** — animal affinity.

#### Physically Exceptional Species

- **Dwarf** — subterranean affinity and compact physiology.
- **Goliath** — large body and increased physical strength.

#### Special / Narrative Species

- **Phoenix** — descendants of the first Phoenix.
- **Phantom** — void-affiliated race able to alternate between material and immaterial states.
- **Celestial** — beings with divine affinity.

Special species should use the same engine primitives as normal species.

Their availability may differ:

```text
visibility: hidden
selection: admin_only
```

or through unlock conditions such as:

```text
quest
item
command
narrative_event
```

The engine should never contain player-specific hardcoded checks.

---

## 5. Specializations

A Specialization is a player-selected vocational preset.

It is not a hard class restriction.

A specialization primarily defines:

- starting skill levels;
- skill aptitudes;
- XP gain modifiers;
- decay resistance;
- skill floors;
- a limited number of signature bonuses;
- thematic identity.

A player who chooses Farmer must still be able to become an excellent Blacksmith through sustained gameplay.

The Blacksmith simply reaches and maintains Smithing mastery more efficiently.

### 5.1 Example: Blacksmith

```text
BLACKSMITH

Primary Skills
- Smithing
- Engineering

Secondary Skill
- Mining

Starting Levels
Smithing       20
Engineering    15
Mining         10

Aptitude
Smithing XP       x1.35
Engineering XP    x1.25

Decay Resistance
Smithing          +30%
Engineering       +20%

Protected Floor
Smithing          30

Signature
Forge Mastery I
```

### 5.2 Initial Specialization Candidates

#### Gathering

- Miner
- Lumberjack
- Farmer
- Fisherman
- Hunter

#### Crafting / Knowledge

- Blacksmith
- Engineer
- Herbalist
- Scholar / Chronicler
- Alchemist
- Cook

#### Exploration / Physical

- Explorer
- Laborer

Additional specializations should only be added when they correspond to substantial gameplay loops.

---

## 6. Skills

Skills are the primary long-term progression system.

The central philosophy is:

> Perform an activity to become better at that activity.

Skills should generally range from **0 to 100**.

### 6.1 Proposed Initial Skill Set

#### Gathering

- Mining
- Woodcutting
- Farming
- Fishing
- Hunting

#### Crafting

- Smithing
- Engineering
- Alchemy
- Cooking
- Enchanting

#### Physical

- Melee
- Archery
- Defence
- Athletics

#### Knowledge

- Scholarship

Do not immediately create dozens of narrow skills.

A new skill should only exist if:

1. players perform the associated activity often enough;
2. the activity supports meaningful progression;
3. the skill creates useful gameplay decisions.

---

## 7. Skill Levels

Suggested skill bands:

```text
0       Untrained
1-19    Novice
20-39   Apprentice
40-59   Skilled
60-79   Expert
80-94   Master
95-100  Legendary
```

Progression should combine:

- continuous scaling;
- milestone unlocks.

Example: Mining

```text
Mining 0
Vanilla baseline

Mining 10
+5% mining speed

Mining 20
+10% mining speed

Mining 30
+15% mining speed
5% chance for bonus raw ore

Mining 40
+20% mining speed

Mining 50
+25% mining speed
10% bonus raw ore chance

Mining 60
+30% mining speed

Mining 70
+35% mining speed
effective Fortune +1

Mining 80
+40% mining speed
15% bonus raw ore chance

Mining 90
+45% mining speed
effective Fortune +2

Mining 100
+50% mining speed
Legendary perk
```

Exact values are balance configuration, not engine constants.

---

## 8. Aptitudes

Aptitudes represent how naturally a character learns and retains a skill.

Suggested ranks:

```text
D
C
B
A
S
```

Example multipliers:

```text
D   XP x0.70   decay x1.25
C   XP x0.90   decay x1.10
B   XP x1.00   decay x1.00
A   XP x1.25   decay x0.80
S   XP x1.50   decay x0.60
```

Specializations are the primary source of Aptitude changes.

Species may impose occasional minimum aptitudes where thematically justified.

Examples:

```text
Dwarf
Mining aptitude cannot be below B

Sylvian
Farming aptitude cannot be below B

Goliath
Athletics aptitude cannot be below B
```

Species should influence potential without automatically dictating career.

---

## 9. Skill Decay

Decay exists to create specialization through maintenance.

It should **not** punish casual players or make characters feel as if they have forgotten basic knowledge.

Suggested model:

```text
Skill 0-25
No decay

Skill 26-50
Extremely low decay

Skill 51-75
Low decay

Skill 76-90
Moderate decay

Skill 91-100
High maintenance
```

Decay should only begin after an inactivity grace period.

Example:

```text
Mining: 84
Last meaningful use: 3 real days ago
Grace period: 48 hours
Decay starts after the grace period
```

Example configurable rates:

```text
26-50   -0.05 level/day
51-75   -0.10 level/day
76-90   -0.20 level/day
91-100  -0.35 level/day
```

### 9.1 Lazy Decay Calculation

Decay should not be processed every tick.

Instead, it should be calculated when needed:

- player login;
- skill queried;
- relevant skill activity;
- scheduled low-frequency maintenance;
- administrative inspection.

Conceptually:

```text
elapsed = now - lastActivity
decay = decayFunction(skillLevel, elapsed, aptitude, specializationModifiers)
```

This keeps the system cheap and scalable.

---

## 10. Skill Floors

Decay represents loss of sharpness, not complete loss of knowledge.

Each skill can track:

```text
currentLevel
highestLevelEver
protectedFloor
lastMeaningfulUse
```

Example:

```text
Mining peak: 82
Current: 73
Protected floor: 60
```

The skill cannot decay below 60.

Specializations may grant floors.

Example:

```text
Blacksmith
Smithing floor: 30
```

This means a Blacksmith can become rusty but will not forget the fundamentals of smithing.

---

## 11. XP and Anti-Exploit Rules

Minecraft is highly automatable.

XP must therefore be awarded for **meaningful activity**, not raw repetition.

Bad design:

```text
break any block = +1 Mining XP
```

This allows trivial cobblestone-generator grinding.

Better example:

```text
Stone           0.05 XP
Deepslate       0.07 XP
Coal Ore        0.30 XP
Iron Ore        0.60 XP
Gold Ore        0.80 XP
Diamond Ore     2.00 XP
Ancient Debris  4.00 XP
```

### 11.1 Diminishing Returns

Repeated identical actions should gradually give less XP.

Example:

```text
First 64 stone:
100% XP

64-256:
50% XP

256+:
10% XP
```

The penalty can recover through:

- time;
- changing activity;
- changing resource type;
- meaningful variation in gameplay.

### 11.2 Farming Rules

Example:

```text
plant seed          small XP
harvest immature    0 XP
harvest mature      normal XP
rare crop           increased XP
```

### 11.3 Crafting Rules

Crafting 100 identical cheap items should not be the optimal method of reaching Smithing 100.

Repeated recipes should suffer diminishing returns.

XP should consider:

- material tier;
- recipe difficulty;
- recipe novelty;
- item value;
- repetition;
- output quality.

---

## 12. Ability Engine

Lifepath should not create one Java class for every racial mechanic.

Avoid architecture like:

```text
SylvianForestSpeedPower
SylvianFlowerPower
SylvianPotPower
IcebornIcePower
IcebornHeatPower
...
```

Instead, build a reusable effect/ability engine.

Species, traits, conditions, and specializations should compose generic primitives.

### 12.1 Core Ability Components

```text
conditions
actions
targets
cooldowns
costs
effects
resources
```

### 12.2 Example Conditions

```text
biome_tag
dimension
block_nearby
entity_nearby
daylight
night
health_threshold
temperature
inventory_contains
equipment_contains
submerged
on_fire
weather
skill_level
```

### 12.3 Example Actions

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
teleport
spawn_particle
play_sound
change_mob_disposition
```

The test for the engine is simple:

> If a new species idea can be represented by composing existing primitives, the engine is healthy.

If many new species require custom core code, the primitive set is incomplete.

---

## 13. Species Prototype: Sylvian

### Identity

Nature-aligned species that thrives in vegetation-rich environments and struggles with corrupted or arid environments.

### Passive — Forest Affinity

While inside forest biomes:

```text
+10% movement speed
+2 armor
```

### Passive — Grass Walker

While moving through tall grass / dense vegetation:

```text
additional movement bonus
```

### Passive — Nature Purification

Interaction with or proximity to valid overworld flowers can remove selected negative effects.

Exact trigger and flower requirements are configurable.

### Active — Verdant Bloom

```text
Cooldown: 240 seconds
Radius: 7 blocks
```

Accelerates valid nearby:

- crops;
- saplings;
- grass;
- flowers;
- compatible modded vegetation.

### Debuff — Arid Intolerance

In desert/arid biomes:

```text
movement speed penalty
periodic nausea
```

Suggested baseline:

```text
-15% movement speed
Nausea for 15 seconds every 60 seconds
```

### Debuff — Necrotic Vulnerability

Damage from undead sources is increased.

Suggested initial multiplier:

```text
x1.5
```

This should remain configurable.

### Debuff — Nether Corruption

Selected Nether blocks and flora may cause:

- Weakness;
- Nausea;
- damage;
- Blindness.

---

## 14. Species Prototype: Iceborn

Iceborn should use a thermal resource rather than a large collection of unrelated environmental checks.

### Internal Temperature

```text
0 ------------------------------ 100

0      dangerously cold
50     stable
100    overheating
```

Potential environmental modifiers:

```text
cold biome       decreases temperature
ice nearby       decreases temperature

desert           increases temperature
fire nearby      strongly increases temperature
campfire         strongly increases temperature
magma            strongly increases temperature
lava             massively increases temperature
```

Ice items or an ice sack can provide a cooling buffer.

### Temperature Bands

```text
0-25
cold-adapted bonuses
possible Speed / light regeneration

26-70
normal

71-85
Slowness

86-99
Weakness + Slowness

100
periodic heat damage
```

### Active — Frost Walk / Frozen Ground

```text
Radius: 7 blocks
Cooldown: 120 seconds
```

Nearby valid water becomes temporary frozen terrain.

Prefer temporary/frosted ice behavior over permanent world modification unless intentionally configured otherwise.

---

## 15. Species Prototype: Undead

Undead should have a distinctly different survival loop rather than simply being "human with buffs."

### Diet

Valid baseline foods:

- rotten flesh;
- spider eye;
- suspicious stew where appropriate;
- configurable custom rotten/necrotic foods.

Normal food can provide zero nutrition by default.

Custom datapacks/mod integrations may extend the allowed food tag.

### Mob Disposition

Create a reusable relationship system.

Example:

```text
Zombie      Neutral
Skeleton    Neutral
Drowned     Neutral
Husk        Neutral
```

This system should later support other species:

```text
Hellborn -> Piglin Neutral
Anima -> selected animals Friendly
```

### Active — Death Sight

Conditions:

```text
Night only
Radius: 15 blocks
```

Effect:

Nearby living entities receive an outline visible only to the casting Undead player.

Suggested baseline:

```text
Duration: 8 seconds
Cooldown: 60 seconds
```

Prefer client-specific rendering/networking instead of globally applying Minecraft's Glowing effect if private visibility is required.

---

## 16. Acquired Conditions and Attunements

Some concepts should not be Species.

Examples:

### Conditions / Transformations

- Vampirism
- Lycanthropy
- major curses
- mutations
- narrative transformations

### Attunements

- Air
- Earth
- Lightning / Electricity
- other elemental affinities acquired in play

Example character:

```text
Species:
Human

Specialization:
Scholar

Condition:
Vampirism

Attunement:
Lightning
```

This creates much more character variety than defining every combination as a separate species.

---

## 17. Laborer and Encumbrance

The Laborer specialization should not simply gain Strength while carrying a full inventory.

A reusable encumbrance system creates deeper gameplay.

Example load bands:

```text
0-40%
Normal

40-70%
Minor stamina / mobility pressure

70-90%
Movement penalty

90-100%
Heavy movement / stamina penalty
```

Laborer and Goliath may modify:

```text
carry capacity
encumbrance thresholds
stamina cost
movement penalty
```

This turns carrying capacity into a shared mechanic rather than a one-off class gimmick.

---

## 18. Player Data

Conceptual persistent structure:

```text
PlayerCharacterData
{
    speciesId

    specializationId

    skills {
        mining {
            xp
            level
            highestLevel
            protectedFloor
            aptitude
            lastMeaningfulUse
        }

        smithing {
            ...
        }
    }

    traits []

    conditions []

    attunements []

    cooldowns {}

    resources {}

    unlocks []
}
```

All progression must be **server authoritative**.

The client receives only the state required for:

- UI;
- HUD;
- rendering;
- local prediction where safe.

The server remains the source of truth.

---

## 19. Data-Driven Content

The design target is:

```text
Java = engine
data = content
config = balance
```

Not:

```text
Java = Sylvian
Java = Iceborn
Java = Farmer
Java = Miner
```

Suggested datapack/data directories:

```text
data/lifepath/species/
data/lifepath/specialization/
data/lifepath/skill/
data/lifepath/ability/
data/lifepath/trait/
data/lifepath/condition/
data/lifepath/attunement/
data/lifepath/item_weight/
data/lifepath/unlock/
```

Unlock definitions (`data/lifepath/unlock/`): `{display_name, description?, unlocks:[content-id...], sources:[{type,...}]}`. When a source fires, every `unlocks[]` id lands in the character's `unlocks[]` — the list `selection:"unlocked"` species check. Source types: `item` (`item`, `consume?`, fires on use), `advancement` (`advancement` id), `event` (`event` activity type, `subject`/`tag` filters, `chance`), `admin` (`/lifepath unlock add`). Shipped: `phoenix_contract` (item: totem), `phantom_touch` (End advancement or phantom kills), `celestial_blessing` (admin only).

The exact format can be finalized during architecture design.

---

## 20. Configuration

Configuration must exist from the beginning.

Do not hardcode balance values and promise to make them configurable later.

Suggested configuration:

```text
/config/lifepath/

general.toml
skills.toml
decay.toml
species.toml
specializations.toml
```

Balance values such as:

- XP multipliers;
- cooldowns;
- damage multipliers;
- decay rates;
- grace periods;
- radius values;
- environmental modifiers;
- skill thresholds;

must be configurable where practical.

---

## 21. UI / UX

Lifepath requires a proper Character interface.

Players should not need an external wiki to understand why something is happening.

### Main Character Screen

Example:

```text
VLAD

Species
Dwarf

Specialization
Blacksmith

Condition
None

Attunement
None
```

### Skills Screen

Example:

```text
Mining        67 █████████░
Smithing      81 ██████████
Farming       12 ██
Fishing        4 █
```

### Skill Detail

Example:

```text
SMITHING — 81

Rank:
Master

Current bonuses:
+16% repair efficiency
+12% material recovery
Masterwork quality available

Next milestone — 85:
+5% high-quality chance

Decay:
Protected for 31h

Aptitude:
A
```

Important information to expose:

- level;
- current XP;
- next milestone;
- current bonuses;
- XP sources;
- aptitude;
- decay status;
- protected floor;
- recent gains where useful.

---

## 22. Commands and Administration

Admin tools are mandatory for testing and balancing.

Suggested command tree:

```text
/lifepath character inspect <player>

/lifepath species get <player>
/lifepath species set <player> <species>

/lifepath specialization get <player>
/lifepath specialization set <player> <specialization>

/lifepath skill get <player> <skill>
/lifepath skill set <player> <skill> <level>
/lifepath skill addxp <player> <skill> <amount>

/lifepath condition add <player> <condition>
/lifepath condition remove <player> <condition>

/lifepath attunement add <player> <attunement>
/lifepath attunement remove <player> <attunement>

/lifepath cooldown clear <player>

/lifepath reload
```

These tools will drastically reduce iteration time during balancing.

---

## 23. Suggested Code Architecture

```text
lifepath/
│
├── character/
│   ├── CharacterData
│   ├── CharacterManager
│   └── CharacterPersistence
│
├── species/
│   ├── Species
│   ├── SpeciesRegistry
│   └── SpeciesLoader
│
├── skill/
│   ├── Skill
│   ├── SkillRegistry
│   ├── SkillProgress
│   ├── SkillXpService
│   └── SkillDecayService
│
├── specialization/
│   ├── Specialization
│   ├── SpecializationRegistry
│   └── SpecializationLoader
│
├── aptitude/
│
├── ability/
│   ├── Ability
│   ├── ActiveAbility
│   ├── PassiveAbility
│   ├── AbilityCondition
│   ├── AbilityAction
│   └── AbilityRegistry
│
├── trait/
│
├── condition/
│
├── attunement/
│
├── resource/
│
├── relation/
│
├── event/
│
├── network/
│
├── client/
│   ├── hud/
│   └── screen/
│
├── command/
│
└── config/
```

The exact package layout may evolve, but system boundaries should remain explicit.

---

## 24. Gameplay Mechanic Matrix

Every mechanic should be expressible in a common format.

| System | Source | Trigger | Condition | Effect | Scaling | Cooldown |
|---|---|---|---|---|---|---|
| Forest Affinity | Sylvian Species | Passive | Forest biome | Armor + speed | Fixed/config | — |
| Verdant Bloom | Sylvian Species | Active key | Ability available | Grow vegetation | Radius/config | 240s |
| Mining Speed | Mining Skill | Passive | Mining valid block | Break speed | Skill level | — |
| Prospector | Miner Specialization | Block break | Ore block | Bonus yield chance | Skill/config | — |
| Death Sight | Undead Species | Active key | Night | Private entity outline | Fixed/config | 60s |

If most future ideas fit this matrix using existing primitives, the engine is sufficiently generic.

If many ideas require changes to the core, the primitive vocabulary needs expansion.

---

## 25. MVP Scope

Do **not** build everything at once.

The first playable vertical slice should prove the framework.

### Species

- Human
- Sylvian
- Iceborn
- Undead

### Specializations

- Miner
- Farmer
- Blacksmith
- Fisherman

### Skills

- Mining
- Farming
- Smithing
- Fishing

### Core Systems

- persistent character data;
- XP;
- levels;
- aptitudes;
- decay;
- protected floors;
- cooldowns;
- passive effects;
- active abilities;
- configuration;
- networking;
- basic UI;
- admin commands.

Why these four species:

```text
Sylvian
tests biome conditions, vegetation interactions and AoE abilities

Iceborn
tests resources, environmental simulation and temperature

Undead
tests diet, mob relations and conditional active abilities

Human
provides baseline/control behavior
```

This MVP should prove the architecture before content expansion begins.

---

## 26. Roadmap

Suggested order:

```text
0.1
Character Core

0.2
Skill Engine + XP + Decay

0.3
Ability Engine

0.4
Four-Species Vertical Slice

0.5
Four Specializations

0.6
Character UI

0.7
Configuration + Balance Pass

0.8
Remaining Core Species

0.9
Remaining Specializations

1.0
Production Release
```

Post-1.0 systems:

```text
Conditions:
- Vampirism
- Lycanthropy

Attunements:
- Air
- Earth
- Lightning

Narrative / Special Species:
- Phoenix
- Phantom
- Celestial
```

---

## 27. What Lifepath Is Not

Lifepath is not:

- an Origins addon;
- a collection of hardcoded races;
- a rigid class system;
- an MMO skill tree where clicking a menu replaces gameplay;
- a permission system where the wrong class simply cannot interact with content;
- a framework where every content addition requires Java code;
- a progression system that rewards AFK farms more than actual play.

---

## 28. Target Player Experience

A player should be able to describe their character naturally:

> I am an Iceborn who trained as a Fisherman, but over time I became a skilled Smith. I am excellent at Fishing, competent at Mining, poor at Farming, and I recently became attuned to Lightning.

That sentence should map directly to actual gameplay systems.

The player should not merely be:

> Iceborn + Fisherman preset.

Their history of play should matter.

That is the core fantasy of **Lifepath**.

---

## 29. Final Design Thesis

Lifepath is a character simulation layer for Minecraft.

Species defines what the character is.

Specialization defines what the character initially intends to become.

Aptitude defines how easily they learn.

Skills record what they actually practice.

Decay makes exceptional mastery something that must be maintained.

Traits, conditions and attunements allow the character to change through play.

Abilities turn those identities into concrete Minecraft mechanics.

The result should be a system where characters are not selected from a list of complete builds.

They are **developed over time through their lifepath**.
