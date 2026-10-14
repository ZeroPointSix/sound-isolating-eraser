#!/usr/bin/env python3
"""Inspect the remapped production JAR for CityDoorMixin SRG targets.

This is bytecode evidence of the finished jar, not a userdev classpath string search.
Runtime injection of those SRG names still requires a remapped Minecraft process.
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path


CLASS_PATH = "com/zeropointsix/eraser/mixin/CityDoorMixin.class"
MIXIN_JSON = "sound_isolating_eraser.mixins.json"


def strings(data: bytes) -> list[str]:
    return [match.decode("ascii") for match in re.findall(rb"[\x20-\x7e]{4,}", data)]


def main() -> int:
    jar = Path(sys.argv[1] if len(sys.argv) > 1 else "build/libs/sound_isolating_eraser-0.11.0.jar")
    if not jar.is_file():
        libs = sorted(Path("build/libs").glob("sound_isolating_eraser-*.jar"))
        jar = next((path for path in libs if "sources" not in path.name), jar)
    if not jar.is_file():
        print("NIGHT_REMAPPED_DOOR_MIXIN_FAIL missing jar", jar, file=sys.stderr)
        return 1
    with zipfile.ZipFile(jar) as archive:
        data = archive.read(CLASS_PATH)
        mixin_json = archive.read(MIXIN_JSON).decode("utf-8")
    text = strings(data)
    blob = "\n".join(text)
    checks = {
        "class_has_m_153165_": "m_153165_" in blob,
        "class_has_m_6227_": "m_6227_" in blob,
        "class_has_m_6861_": "m_6861_" in blob,
        "class_has_m_7731_": "m_7731_" in blob,
        "class_has_quoted_wrong_srg": '"m_153166_"' in blob or "m_153166_" in blob,
        "json_lists_CityDoorMixin": "CityDoorMixin" in mixin_json,
        "group_min_3": "min" in blob and "3" in blob,
    }
    # The remapped class must carry the three SRG method ids; the off-by-one id must not.
    failed = []
    if not checks["class_has_m_153165_"]:
        failed.append("missing SRG setOpen m_153165_")
    if not checks["class_has_m_6227_"]:
        failed.append("missing SRG use m_6227_")
    if not checks["class_has_m_6861_"]:
        failed.append("missing SRG neighborChanged m_6861_")
    if not checks["class_has_m_7731_"]:
        failed.append("missing SRG Level.setBlock m_7731_ redirect target for setOpen")
    if checks["class_has_quoted_wrong_srg"]:
        failed.append("contains wrong SRG m_153166_")
    if not checks["json_lists_CityDoorMixin"]:
        failed.append("mixin json omits CityDoorMixin")
    print("NIGHT_REMAPPED_DOOR_MIXIN jar=" + str(jar.resolve()))
    print("NIGHT_REMAPPED_DOOR_MIXIN srg=m_6227_,m_6861_,m_153165_ present="
          + str(checks["class_has_m_153165_"] and checks["class_has_m_6227_"] and checks["class_has_m_6861_"]))
    print("NIGHT_REMAPPED_DOOR_MIXIN setOpen_redirect_target_m_7731_=" + str(checks["class_has_m_7731_"]))
    print("NIGHT_REMAPPED_DOOR_MIXIN CityDoorMixin.json=" + str(checks["json_lists_CityDoorMixin"]))
    if failed:
        print("NIGHT_REMAPPED_DOOR_MIXIN_FAIL " + "; ".join(failed), file=sys.stderr)
        return 1
    print("NIGHT_REMAPPED_DOOR_MIXIN_PASS bytecode contains use/neighborChanged/setOpen SRG ids and mixin json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
