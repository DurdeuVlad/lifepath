# Translations

All player-facing Lifepath text lives in editable YAML files:

```
common/src/main/lang/<locale>.yaml
```

`en_us.yaml` is the base locale — every other file is validated against it.
At build time `generateLangJson` (see `common/build.gradle`) emits
`assets/lifepath/lang/<locale>.json`, which is what Minecraft's native
localization system loads. Translators never touch Java or JSON.

## Adding a language

1. Copy `en_us.yaml` to your locale file, e.g. `ro_ro.yaml` (Minecraft locale
   code, lowercase).
2. Translate the **values** only. Keys must stay byte-identical.
3. Keep every `%s` placeholder — the build fails if a translation drops or
   reorders one.
4. Build; the generated JSON ships inside the mod jar.

## What the build validates

- Malformed YAML → build error naming the file.
- Duplicate keys → rejected (SnakeYAML `allowDuplicateKeys: false`).
- Keys not present in `en_us.yaml` → build error (catches typos).
- Missing keys → warning only; vanilla falls back to `en_us` at runtime.
- Placeholder set mismatch vs `en_us` → build error.

## Server-authored text

Species and specialization names/descriptions come from datapack content and
are not localized (like vanilla advancements, they follow the server's data).
The picker chrome around them — "Active:", "starts at Lv", "Signature:",
"+N more" — is translated per-client: `SelectionService` sends
`Component.translatable` detail lines (`text.lifepath.detail.*` keys), so the
wire carries keys + resolved names, never formatted English.
