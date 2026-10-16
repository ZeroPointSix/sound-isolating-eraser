#!/usr/bin/env python3
"""Fast bilingual guide contract check; runtime registry parity is covered by GameTests."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"
SECTIONS = ("summary", "usage", "controls", "cooldown", "cost", "lore")
NAMESPACES = ("sound_isolating_eraser", "qihai")


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
        assert entry["text_status"] in {"placeholder", "partial", "complete"}
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
                count = len(entry["controls"]) if section == "controls" else 0
                if entry["id"] == "sound_isolating_eraser:sound_isolating_eraser" and section in {"usage", "cost"}:
                    count = 1
                if entry["id"] == "sound_isolating_eraser:gravity_jade_pendant" and section == "cooldown":
                    count = 2
                assert len(re.findall(r"%s", value)) == count, f"{locale}: format arguments for {key}"
                assert "%" not in value.replace("%s", ""), f"{locale}: unsupported format in {key}"
        hints = {"guide.sound_isolating_eraser." + hint for hint in ("hold_both", "hold_shift", "hold_control")}
        actual = {key for key in translations if key.startswith("guide.")}
        assert actual == expected | hints, f"{locale}: missing or stale guide keys {actual ^ (expected | hints)}"
        assert all(translations.get(key) for key in hints)
    pending = sum(entry["text_status"] != "complete" for entry in entries)
    print(f"ITEM_GUIDE_LANG_COVERAGE items={len(entries)} sections=6 locales=2 keys={len(expected) * 2} PR2_pending={pending}")


if __name__ == "__main__":
    verify()
