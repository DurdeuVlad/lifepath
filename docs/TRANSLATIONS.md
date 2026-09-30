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

## Content text (species, abilities, skills, ...)

Every bundled species, specialization, ability, skill, resource band,
condition and attunement carries lang keys of the form:

```
lifepath.<domain>.<id>.<suffix>
```

Domains: `species`, `specialization`, `ability`, `skill`, `resource`,
`condition`, `attunement`. Suffixes: `name`, `description`, `improve_hint`,
`strength.<n>` / `weakness.<n>` (indexed picker pros/cons), `band.<n>`
(resource meter labels).

The wire sends `Component.translatableWithFallback` — the datapack's literal
text rides as the fallback — so each client renders its own language for
bundled content, while a custom datapack with no lang file still shows its
own prose. To translate a piece of content, add its keys to your locale YAML;
you never need the datapack author's permission or files.

The picker chrome ("Active:", "starts at Lv", "Signature:", "+N more") uses
`text.lifepath.detail.*` keys the same way.
