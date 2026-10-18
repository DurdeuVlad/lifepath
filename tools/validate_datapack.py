#!/usr/bin/env python3
"""Datapack integrity validator for LifePath.

Checks (no game needed):
  - every JSON under data/lifepath parses
  - specialization -> skill / ability (signature) refs resolve
  - skill milestone effects -> ability files resolve; description_key present in en_us.yaml
  - xp_source / outcome_rule -> skill, activity, required_tags refs resolve
  - ability files have trigger/target/actions shape
  - item/block/entity_type tag files parse; intra-pack #tag refs resolve
  - dangling: abilities/skills defined but unreferenced is reported as info
"""
import json
import re
import sys
import yaml
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "common/src/main/resources/data/lifepath"
LANG = ROOT / "common/src/main/lang/en_us.yaml"

ACTIVITIES = {
    "lifepath:mining", "lifepath:farming", "lifepath:smithing",
    "lifepath:fishing", "lifepath:crafting", "lifepath:combat",
    "lifepath:archery", "lifepath:defence", "lifepath:brewing",
    "lifepath:husbandry", "lifepath:performance", "lifepath:music",
    "lifepath:diagnosis", "lifepath:treatment",
}

# Event-tag vocabulary beyond datapack tag files: markers producers/mixins/
# KubeJS bridges put on the event's runtime tag set, plus item/entity tags
# carried from the subject stack (workstation ids, mod ids, vanilla tags).
EMITTED_TAGS = {
    "lifepath:fed", "lifepath:cared", "lifepath:bred", "lifepath:born",
    "lifepath:milked", "lifepath:sheared", "lifepath:studied",
    "lifepath:brewed", "lifepath:built", "lifepath:butchered",
    "lifepath:harvested", "lifepath:mature", "lifepath:planted",
    "lifepath:tt_felled", "lifepath:alchimiae_stage",
    "lifepath:fishing_junk", "lifepath:fishing_treasure",
    "lifepath:forged_outputs", "lifepath:smithing_workstations",
    "lifepath:smithing_table", "lifepath:anvil", "lifepath:click_harvest",
    "lifepath:animals", "lifepath:scholarly",
    "lifepath:construction_blocks", "lifepath:engineering_blocks",
    "lifepath:harvestable", "lifepath:vein_mineable",
    "lifepath:masonry", "lifepath:sawmilled", "lifepath:unique_ability",
    "woodworks:sawmill", "sawmill:woodcutting", "riverfishing:fishes",
    "lifepath:assembled", "lifepath:brewable_drinks",
}
DYNAMIC_TAG_PREFIXES = ("lifepath:potion/",)

errors, warnings, infos = [], [], []


def load(dirpath):
    out = {}
    if not dirpath.is_dir():
        return out
    for f in sorted(dirpath.rglob("*.json")):
        rel = f.relative_to(dirpath)
        rid = "lifepath:" + str(rel.with_suffix("")).replace("\\", "/")
        try:
            out[rid] = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            errors.append(f"PARSE {rid}: {e}")
    return out


skills = load(DATA / "skill")
specs = load(DATA / "specialization")
xp_sources = load(DATA / "xp_source")
outcomes = load(DATA / "outcome_rule")
abilities = load(DATA / "ability")
tags_item = load(DATA / "tags/item")
tags_block = load(DATA / "tags/block")
tags_entity = load(DATA / "tags/entity_type")
all_tags = {**tags_item, **tags_block, **tags_entity}
unlock = load(DATA / "unlock")
morph = load(DATA / "morph_form")
attunement = load(DATA / "attunement")
condition = load(DATA / "condition")
diet = load(DATA / "diet")
resource = load(DATA / "resource")
relation = load(DATA / "relation")
item_weight = load(DATA / "item_weight")

lang_keys = set()
if LANG.exists():
    try:
        lang_doc = yaml.safe_load(LANG.read_text(encoding="utf-8")) or {}
        def flatten(d, prefix=""):
            for k, v in d.items():
                key = f"{prefix}{k}"
                lang_keys.add(key)
                if isinstance(v, dict):
                    flatten(v, key + ".")
        flatten(lang_doc)
    except Exception as e:
        warnings.append(f"en_us.yaml parse failed ({e}); lang checks skipped")


def check_ref(rid, pool, kind, where):
    if rid not in pool:
        errors.append(f"{kind} ref {rid} missing ({where})")


def check_tag(rid, where):
    if rid in all_tags or rid in EMITTED_TAGS:
        return
    if any(rid.startswith(p) for p in DYNAMIC_TAG_PREFIXES):
        return
    if rid.startswith("minecraft:") or rid.startswith("c:"):
        warnings.append(f"external tag ref {rid} ({where}) — resolved at runtime")
        return
    errors.append(f"tag ref {rid} missing ({where})")


def check_activity(act, where):
    if act not in ACTIVITIES:
        errors.append(f"unknown activity {act} ({where})")


# ---- specialization ----
for rid, s in specs.items():
    for field in ("starting_skills", "aptitudes", "xp_modifiers",
                  "decay_modifiers", "protected_floors"):
        for sk in (s.get(field) or {}):
            check_ref(sk, skills, "skill", f"{rid}.{field}")
    for ab in s.get("signature") or []:
        check_ref(ab, abilities, "ability", f"{rid}.signature")

# ---- skills ----
for rid, s in skills.items():
    for m in s.get("milestones") or []:
        for eff in m.get("effects") or []:
            check_ref(eff, abilities, "ability", f"{rid}.milestones")
        dk = m.get("description_key")
        if dk and lang_keys and dk not in lang_keys:
            warnings.append(f"missing lang key {dk} ({rid})")

# ---- xp_source / outcome_rule ----
for pool_name, pool in (("xp_source", xp_sources), ("outcome_rule", outcomes)):
    for rid, x in pool.items():
        sk = x.get("skill")
        if sk:
            check_ref(sk, skills, "skill", rid)
        else:
            errors.append(f"{pool_name} {rid} missing 'skill'")
        act = x.get("activity")
        if act:
            check_activity(act, rid)
        else:
            errors.append(f"{pool_name} {rid} missing 'activity'")
        for t in x.get("required_tags") or []:
            check_tag(t, rid)
        for t in (x.get("per_tag") or {}):
            check_tag(t, rid)

# ---- abilities ----
for rid, a in abilities.items():
    for field in ("trigger", "target"):
        if field not in a:
            errors.append(f"ability {rid} missing '{field}'")
    if not a.get("actions") and "multiplier" not in (a.get("trigger") or {}):
        infos.append(f"ability {rid} has no actions (marker/trigger-only)")

# ---- tag internal refs ----
for rid, t in all_tags.items():
    for v in t.get("values") or []:
        if isinstance(v, str) and v.startswith("#lifepath:"):
            ref = "lifepath:" + v[len("#lifepath:"):]
            if ref not in all_tags:
                errors.append(f"tag {rid} references missing tag {ref}")

# ---- unlock / morph / attunement: best-effort ref checks ----
for pool_name, pool in (("unlock", unlock), ("morph_form", morph),
                        ("attunement", attunement)):
    for rid, u in pool.items():
        blob = json.dumps(u)
        for m in re.finditer(r'"lifepath:([a-z0-9_]+)"', blob):
            pass  # ids too polymorphic; skip

# ---- info: unreferenced content ----
spec_sig = {a for s in specs.values() for a in (s.get("signature") or [])}
milestone_effs = {e for s in skills.values() for m in (s.get("milestones") or [])
                  for e in (m.get("effects") or [])}
referenced_abilities = spec_sig | milestone_effs
for rid in abilities:
    if rid not in referenced_abilities:
        infos.append(f"ability {rid} unreferenced by specs/milestones")

xp_skills = {x.get("skill") for x in xp_sources.values()}
for rid in skills:
    if rid not in xp_skills:
        infos.append(f"skill {rid} has no xp_source")

print(f"loaded: {len(specs)} specs, {len(skills)} skills, "
      f"{len(xp_sources)} xp_sources, {len(outcomes)} outcome_rules, "
      f"{len(abilities)} abilities, {len(all_tags)} tags")
print(f"\nERRORS ({len(errors)}):")
for e in errors:
    print(f"  {e}")
print(f"\nWARNINGS ({len(warnings)}):")
for w in warnings:
    print(f"  {w}")
print(f"\nINFO ({len(infos)}):")
for i in infos:
    print(f"  {i}")
sys.exit(1 if errors else 0)
