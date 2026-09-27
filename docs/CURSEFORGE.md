# CurseForge Copy — LP-REL-002

Paste-ready text for the CurseForge project page. Two blocks: the short
summary (the line under the title) and the full description body.

---

## Summary (project tagline)

```
Character progression for people who play, not plan: pick a species, choose
a starting focus, and level thirteen skills by actually doing the work —
mastery is maintained, not bought.
```

## Full description

````markdown
<h2>Build a character by playing, not by picking from a list</h2>

<p>Lifepath is a character-progression mod where three things shape who you
become: <strong>what you are</strong>, <strong>what you focus on</strong>,
and <strong>what you actually do</strong>.</p>

<h3>Species — what you are</h3>
<p>Fifteen playable species, from amphibian to undead. Each brings innate
traits and signature abilities — your species defines your starting canvas,
not a rigid class.</p>

<h3>Specialization — a head start, not a lock</h3>
<p>Thirteen trades — Blacksmith, Hunter, Scholar, Farmer and more — give you
skill aptitude and a <em>protected floor</em>: your trade skills can't decay
below a guaranteed level. But nothing is locked: anyone can learn anything;
your specialization just makes some paths faster.</p>

<h3>Skills — what you actually practice</h3>
<p>Thirteen skills level by doing. Mine ores to improve Mining. Cook meals
to improve Cooking. Skill milestones unlock real abilities — an Iceborn's
frost, a Phoenix's rebirth, and everything in between (79 abilities ship
with the mod).</p>

<h3>Decay — mastery needs maintenance, not punishment</h3>
<p>Skills fade only if you ignore them: nothing below level 25 ever decays,
decay only starts after an inactivity grace period, and your specialization
protects a floor you'll never fall under. Casual play is safe; peak mastery
is earned and kept.</p>

<h3>What it looks like in game</h3>
<ul>
<li>A character screen for your species, specialization, traits and
conditions — every entry has its own icon.</li>
<li>A skills screen with level bands and milestone progress; click a skill
for the full picture.</li>
<li>A compact HUD with resource bars and cooldown/state icons that stays
out of the way.</li>
<li>Server-authoritative progression — safe on dedicated servers, persists
across deaths, works in singleplayer.</li>
</ul>

<h3>Install</h3>
<ul>
<li>Minecraft <strong>1.21.1</strong>, Fabric Loader <strong>0.16.10+</strong></li>
<li>Fabric API <strong>0.116.0+</strong> in <code>mods</code></li>
<li>Lifepath in <code>mods</code> on <strong>client and server</strong> —
the server owns character state</li>
<li>Java 21</li>
</ul>

<h3>Compatibility</h3>
<p>Lifepath is standalone — it does not require Origins or any other
progression mod, and it doesn't touch vanilla progression systems. It's
built to sit <em>under</em> content mods: an integration adapter for
Overgeared-style smithing ships in the jar and turns forged-output crafting
into Smithing XP when a compatible partner mod is present (currently
dormant — see the project repo for the exact compat state).</p>

<h3>First minutes</h3>
<ol>
<li>Open the character screen (default <code>C</code>; the Controls menu
lists both Lifepath keys).</li>
<li>Pick a species with <code>/lifepath species choose</code> —
tab-completion lists the species, and its description prints
when you pick.</li>
<li>Specializations are assigned by a server admin
(<code>/lifepath specialization set</code>) — solo with cheats, set your
own. It's a head start, not a contract.</li>
<li>Go play. Watch the skills screen fill in; press the ability key
(default <code>G</code>) to fire an active ability — click an ability row
on the character screen to choose which.</li>
</ol>

<h3>Status</h3>
<p><em>Approaching 1.0 — feature-complete and in release-candidate
stabilization. The content set and UI are final for 1.0; balance may still
shift. Report issues on the GitHub issue tracker linked from this page.</em></p>
````

---

## Notes for the uploader (do not paste)

- Version/loader claims verified against `fabric.mod.json`
  (`fabricloader >=0.16.10`, `minecraft ~1.21.1`, `fabric-api >=0.116.0`)
  and `gradle.properties`.
- Content counts verified against `data/lifepath/`: 15 species,
  13 specializations, 13 skills, 79 abilities, 3 attunements,
  2 conditions, 4 resources.
- Overgeared framing verified against `docs/COMPAT.md`: adapter ships
  dormant; Overgeared has no Fabric 1.21.1 build today — the copy says
  "Overgeared-style … dormant" rather than claiming live compatibility.
- Status line is honest pre-release framing; flip it at 1.0.
