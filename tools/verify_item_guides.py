#!/usr/bin/env python3
"""Fast bilingual guide contract check; runtime registry parity is covered by GameTests."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"
SECTIONS = ("summary", "usage", "controls", "cooldown", "cost", "lore")
NAMESPACES = ("sound_isolating_eraser", "qihai")
ARGUMENTS = {
    ("enhancement_pill_pack", "usage"): 1,
    ("enhancement_pill_pack", "cooldown"): 1,
    ("enhancement_pill_pack", "cost"): 1,
    ("flare_gun", "cooldown"): 1,
    ("flare_red", "cooldown"): 1,
    ("flare_yellow", "cooldown"): 1,
    ("flare_green", "cooldown"): 1,
    ("flare_red", "usage"): 2,
    ("flare_yellow", "usage"): 2,
    ("flare_green", "usage"): 1,
    ("supply_crate", "cooldown"): 1,
    ("super_fertilizer_bag", "usage"): 2,
    ("super_fertilizer_bag", "cost"): 2,
    ("fertile_fruit", "usage"): 3,
    ("fertile_log", "usage"): 1,
    ("fertile_leaves", "usage"): 1,
    ("fertile_planks", "usage"): 1,
    ("sound_isolating_eraser", "usage"): 1,
    ("sound_isolating_eraser", "cost"): 1,
    ("gravity_jade_pendant", "cooldown"): 2,
}
PENDING = re.compile(r"\b(?:PR2|pending|placeholder)\b|\u5f85\u8865|\u5360\u4f4d", re.IGNORECASE)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def verify():
    manifest = read(ASSETS / NAMESPACES[0] / "item_guides.json")
    assert manifest["schema_version"] == 1
    entries = manifest["items"]
    ids = [entry["id"] for entry in entries]
    assert entries and len(ids) == len(set(ids)), "inventory must be nonempty and unique"
    expected = set()
    for entry in entries:
        assert re.fullmatch(r"(sound_isolating_eraser|qihai):[a-z0-9_/]+", entry["id"])
        assert entry["module"] and entry["category"]
        assert entry["text_status"] == "complete", f"unfinished guide: {entry['id']}"
        assert isinstance(entry["controls"], list) and all(isinstance(key, str) for key in entry["controls"])
        expected.update("guide." + entry["id"].replace(":", ".") + "." + section for section in SECTIONS)
    for locale in ("en_us", "zh_cn"):
        translations = {}
        for namespace in NAMESPACES:
            translations.update(read(ASSETS / namespace / "lang" / f"{locale}.json"))
        for entry in entries:
            for section in SECTIONS:
                key = "guide." + entry["id"].replace(":", ".") + "." + section
                value = translations.get(key)
                assert isinstance(value, str) and value.strip(), f"{locale}: missing {key}"
                assert not PENDING.search(value), f"{locale}: unfinished text in {key}"
                count = len(entry["controls"]) if section == "controls" else ARGUMENTS.get(
                    (entry["id"].split(":", 1)[1], section), 0)
                assert len(re.findall(r"%s", value)) == count, f"{locale}: format arguments for {key}"
                assert "%" not in value.replace("%s", ""), f"{locale}: unsupported format in {key}"
        hints = {"guide.sound_isolating_eraser." + hint for hint in ("hold_both", "hold_shift", "hold_control", "page")}
        actual = {key for key in translations if key.startswith("guide.")}
        assert actual == expected | hints, f"{locale}: missing or stale guide keys {actual ^ (expected | hints)}"
        assert all(translations.get(key) for key in hints)
        assert translations["guide.sound_isolating_eraser.page"].count("%s") == 2
    pending = sum(entry["text_status"] != "complete" for entry in entries)
    print(f"ITEM_GUIDE_LANG_COVERAGE items={len(entries)} sections=6 locales=2 keys={len(expected) * 2} PR2_pending={pending}")


if __name__ == "__main__":
    verify()
