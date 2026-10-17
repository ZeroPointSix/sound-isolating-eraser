"""Prove the tested named jar maps to the delivered Forge jar, without modifying either."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--runtime-evidence", type=Path)
    options = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    evidence = options.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    named = root / "build/keeper-runtime/keeper-runtime.jar"
    shipped = root / "build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar"
    log = (root / "build/reobfJar/log.txt").read_text()
    arguments = re.search(r"^Arguments: '(.*)'$", log, re.MULTILINE).group(1).split(", ")
    renamer = re.search(r"^ - (.+renamer[^\n]+\.jar)$", log, re.MULTILINE).group(1)
    check = evidence / "remapped-runtime-check.jar"
    arguments[arguments.index("--input") + 1] = str(named)
    arguments[arguments.index("--output") + 1] = str(check)
    command = ["java", "-Xmx384m", "-XX:ActiveProcessorCount=2", "-cp", renamer,
               "net.minecraftforge.renamer.Main", *arguments]
    (evidence / "remap-command.json").write_text(json.dumps(command, indent=2))
    with (evidence / "remap-check.log").open("w") as stream:
        subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, check=True)
    result = {"runtime_jar": str(named), "runtime_sha256": sha(named.read_bytes()),
              "delivery_jar": str(shipped), "delivery_sha256": sha(shipped.read_bytes()), "classes": {}}
    with zipfile.ZipFile(named) as development, zipfile.ZipFile(shipped) as delivery, zipfile.ZipFile(check) as mapped:
        hook = "com/zeropointsix/eraser/mixin/client/KeeperWindowMixin.class"
        names = [n for n in development.namelist() if n.startswith("com/zeropointsix/eraser/keeper/") and n.endswith(".class")]
        assert hook in development.namelist(), "Missing presentation hook"
        names.append(hook)
        assert names, "No keeper classes in packaged runtime"
        for name in names:
            compiled = (root / "build/classes/java/main" / name).read_bytes()
            assert development.read(name) == compiled, "Stale named jar: " + name
            assert mapped.read(name) == delivery.read(name), "Delivery differs from mapped runtime: " + name
            result["classes"][name] = {"runtime_sha256": sha(development.read(name)),
                                       "delivery_sha256": sha(delivery.read(name)), "remap_match": True}
        for name in development.namelist():
            if name.startswith("assets/sound_isolating_eraser/") and ("keeper" in name or "photo" in name):
                assert development.read(name) == delivery.read(name), "Visual resource mismatch: " + name
        assert not any(n.startswith("com/zeropointsix/keepertest/") for n in delivery.namelist()), "QA code shipped"
        config = "sound_isolating_eraser.mixins.json"
        assert development.read(config) == delivery.read(config), "Mixin configuration mismatch"
        assert "client.KeeperWindowMixin" in json.loads(delivery.read(config))["client"]
    if options.runtime_evidence:
        text = (options.runtime_evidence / "runtime-classes.txt").read_text()
        for type_name in ("KeeperVisuals", "KeeperVisuals$Registration"):
            class_name = "com/zeropointsix/eraser/keeper/client/" + type_name + ".class"
            expected = result["classes"][class_name]["runtime_sha256"]
            block = text.split("com.zeropointsix.eraser.keeper.client." + type_name + "\n", 1)[1].split("\nsha256=", 1)
            assert "keeper-runtime.jar" in block[0], "Runtime used loose classes, not packaged jar"
            assert block[1].splitlines()[0] == expected, "Loaded class bytes do not match packaged runtime"
        assert "duration_nanos=200000000" in text
        result["loaded_runtime_verified"] = True
    (evidence / "artifact-provenance.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"keeper_classes_matched": len(result["classes"]),
                      "delivery_sha256": result["delivery_sha256"],
                      "loaded_runtime_verified": result.get("loaded_runtime_verified", False)}))


if __name__ == "__main__":
    main()
