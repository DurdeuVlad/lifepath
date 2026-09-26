# Lifepath — Release & Branding Issue Backlog

> Purpose: Concrete issue list for public-release readiness, especially branding, CurseForge presence, and player-facing materials.  
> Scope: These issues are intended to be created in the project tracker once the repository is ready.

---

## Issue LP-REL-001 — Create player-facing README

### Goal
Write a clean, player-facing `README.md` for the repository and release pages.

### Must include

- what Lifepath is;
- key features in plain language;
- Species / Specialization / Skills explained simply;
- how progression works;
- how decay works in simple terms;
- installation instructions;
- Fabric / Minecraft version;
- compatibility philosophy;
- optional integrations note;
- support / bug-report links;
- quick-start for first-time players.

### UX requirement

The README must be understandable by someone who has **never** read internal design documents.

### Anti-confusion requirement

Use plain language first, technical depth second.

Good:
- "Mine ores to improve Mining."
- "Blacksmith is a starting focus, not a lock."

Bad:
- unexplained internal system jargon;
- design-doc language pasted directly into player docs.

### Acceptance criteria

- readable by a first-time player;
- explains the mod in under one minute of reading;
- no critical concept requires Discord context;
- reviewed for clarity, not just completeness.

---

## Issue LP-REL-002 — Create CurseForge project summary and full description

### Goal
Prepare the final CurseForge copy.

### Must include

- short summary;
- full feature description;
- compatibility statement;
- current supported loader/game versions;
- onboarding explanation;
- optional integration philosophy;
- warning if the project is beta/RC;
- links to docs/source/issues if desired.

### Content rules

- English first;
- short paragraphs;
- clear headers;
- no wall-of-text dumping;
- no misleading marketing language;
- no placeholder sections.

### Acceptance criteria

- can be pasted into CurseForge with minimal edits;
- readable by players who do not know the project;
- accurate to the shipped feature set.

---

## Issue LP-REL-003 — Create Minecraft-style project icon / profile image

### Goal
Create the main public-facing icon/profile image for Lifepath.

### Output

- square icon suitable for repo/profile/project use;
- readable at small sizes;
- recognizable silhouette;
- consistent with the mod identity.

### Style requirements

- Minecraft-native visual language;
- blocky, clean, readable forms;
- simple composition;
- strong silhouette;
- restrained detail;
- no clutter.

### Content suggestions

Possible motifs:
- an open book + skill star;
- a path/road + character marker;
- a crest combining Species + progression themes;
- a simple emblem evoking growth and identity.

### Strict anti-AI-slop rules

The image must NOT have:

- random floating details;
- smeared textures;
- extra fingers/hands if a character appears;
- inconsistent perspective;
- noisy decorative junk;
- unreadable micro-details;
- generic fantasy sludge;
- too many unrelated objects;
- malformed Minecraft items/blocks;
- fake text;
- overrendered plastic look.

### Quality bar

If the icon is reduced to a small square and the idea disappears, it fails.

### Acceptance criteria

- legible at small size;
- unmistakably Minecraft-inspired;
- not visually noisy;
- no obvious AI artifacts;
- approved against the anti-slop checklist.

---

## Issue LP-REL-004 — Create CurseForge banner

### Goal
Create a clean banner/header image for CurseForge.

### Output

- wide banner suitable for CurseForge/project presentation;
- clearly branded as Lifepath;
- immediately communicates the mod fantasy.

### Required content

The banner should communicate at a glance:

- Lifepath name;
- character progression identity;
- Minecraft style;
- one or two core concepts:
  - Species,
  - Skills,
  - Progression,
  - Choice,
  - Growth.

### Style requirements

- Minecraft-native art direction;
- clean composition;
- readable title;
- controlled color palette;
- no visual chaos.

### Strict anti-AI-slop rules

The banner must avoid:

- crowded collage layouts with twenty unrelated elements;
- unreadable title treatment;
- fake UI overlays;
- visual soup backgrounds;
- broken item/block geometry;
- malformed mobs/characters;
- meaningless glow spam;
- noisy “epic fantasy poster” clutter;
- six focal points competing at once.

### Composition guidance

Prefer:
- one clear focal subject;
- one supporting environment;
- title area with strong contrast;
- 2–4 supporting motifs max.

### Acceptance criteria

- title is readable at common banner sizes;
- composition reads in under 2 seconds;
- instantly feels like Minecraft, not generic fantasy AI art;
- no obvious anatomy/perspective/item errors.

---

## Issue LP-REL-005 — Create release gallery screenshots

### Goal
Create a first screenshot set for CurseForge/gallery/repo presentation.

### Required screenshots

At minimum:

1. Species selection screen
2. Specialization selection screen
3. Skill screen
4. Skill detail / progression screen
5. In-world ability example
6. Resource/cooldown example
7. Optional integration example (if available, e.g. Overgeared + Smithing)

### Screenshot rules

- use real in-game UI where possible;
- avoid debug overlays unless specifically showcasing them;
- avoid placeholder assets;
- use clean worlds / scenes;
- use readable GUI scale;
- caption each screenshot clearly.

### Acceptance criteria

- screenshots explain the mod without a long text explanation;
- UI is readable;
- examples are representative of the shipped build.

---

## Issue LP-REL-006 — Create Minecraft-style visual identity guide

### Goal
Define a lightweight art direction guide so future AI-generated assets remain consistent.

### Must define

- color palette direction;
- logo/title treatment direction;
- iconography style;
- UI screenshot framing rules;
- environment style rules;
- acceptable character style;
- unacceptable visual tropes.

### Anti-slop checklist

Every generated image should be reviewed against:

- Is the subject immediately clear?
- Is the composition simple enough?
- Does it look like Minecraft or compatible Minecraft promo art?
- Are all visible items/blocks plausible?
- Is any text readable and intentional?
- Are there malformed hands/faces/limbs?
- Is the image free of meaningless detail clutter?
- Can the image survive cropping/scaling?

### Acceptance criteria

- usable as review criteria for all future project art;
- short enough to actually follow;
- strict enough to block low-quality AI output.

---

## Issue LP-REL-007 — Review all public-facing assets for anti-AI-slop compliance

### Goal
Perform a final quality review of all generated visual assets.

### Assets in scope

- project icon/profile image;
- CurseForge banner;
- gallery screenshots with overlays/captions;
- optional feature cards;
- README visual sections.

### Review checklist

Reject any asset with:

- malformed anatomy;
- fake unreadable text;
- muddy focal point;
- visual clutter;
- inconsistent Minecraft scale/perspective;
- overprocessed lighting;
- “AI soup” textures;
- low readability at intended display size.

### Acceptance criteria

- every public-facing image passes the checklist;
- every image has a clear purpose;
- every image feels deliberate, not auto-generated filler.

---

## Issue LP-REL-008 — Validate Overgeared compatibility scenario for public messaging

### Goal
Prepare a demonstrable Smithing compatibility example using Overgeared as the reference case.

### Must validate

- Lifepath does not replace Overgeared;
- Lifepath can observe/translate Overgeared smithing into Smithing progression;
- failure is graceful if Overgeared is missing;
- public messaging about compatibility is accurate.

### Public-facing deliverables

- one screenshot or demo scene;
- one concise compatibility note for README/CurseForge.

### Acceptance criteria

- no misleading compatibility claim;
- clear explanation for players/server owners;
- validated on a real test instance.

---

## Issue LP-REL-009 — Final release metadata and packaging review

### Goal
Make sure the public package and metadata are clean and release-safe.

### Review items

- version number;
- mod icon;
- dependency metadata;
- supported versions;
- license;
- changelog;
- source/issues links;
- package cleanliness;
- no debug/test garbage in release jar.

### Acceptance criteria

- release artifact is clean;
- metadata is complete;
- ready for CurseForge upload.

---

## Issue LP-REL-010 — Public release readiness review

### Goal
Perform the final go/no-go review before uploading to CurseForge.

### Must confirm

- code readiness;
- UI/UX readiness;
- compatibility readiness;
- documentation readiness;
- visual asset readiness;
- moderation readiness.

### Final gate checklist

```text
[ ] README complete
[ ] CurseForge description complete
[ ] project icon complete
[ ] CurseForge banner complete
[ ] gallery screenshots complete
[ ] anti-AI-slop review complete
[ ] Overgeared example validated
[ ] release jar verified
[ ] metadata verified
[ ] changelog ready
[ ] support/issues link ready
[ ] public-facing copy proofread
```

### Acceptance criteria

- no blocker remains open;
- project is ready to upload and present publicly.
