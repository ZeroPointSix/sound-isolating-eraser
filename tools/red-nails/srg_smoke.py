"""Load the reobfuscated player JAR in an isolated installed Forge server.

This is an installation smoke test, not a replacement for native client gameplay.
Only processes and files created in this invocation's build directory are used.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("red-nails-srg-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
SERVER = RESULTS / "server"
SERVER.mkdir()
PROPS = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
             if "=" in line and not line.lstrip().startswith("#"))
MC = PROPS["minecraft_version"].strip()
FORGE = PROPS["forge_version"].strip()
VERSION = PROPS["mod_version"].strip()
JAR = ROOT / "build/libs" / f"sound_isolating_eraser-{VERSION}.jar"
MODS = SERVER / "mods"
MODS.mkdir()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dependency(group, module, version):
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    matches = list((cache / "caches/modules-2/files-2.1" / group / module / version).glob(f"*/{module}-{version}.jar"))
    if len(matches) != 1:
        raise AssertionError(f"Expected one original runtime dependency: {module}:{version}, got {len(matches)}")
    return matches[0]


def download(url, path):
    with urllib.request.urlopen(url, timeout=120) as response:
        path.write_bytes(response.read())


with zipfile.ZipFile(JAR) as archive:
    names = archive.namelist()
    forbidden = ("com/zeropointsix/eraser/gametest/", "com/zeropointsix/rednailstest/",
                 "com/zeropointsix/suxiatest/", "data/red_nails_tests/", "data/suxia_tests/")
    assert not any(name.startswith(forbidden) for name in names), "Player JAR must not contain QA fixtures"
    for name in ("com/zeropointsix/eraser/rednails/WaterGhostGrab.class", "META-INF/mods.toml",
                 "sound_isolating_eraser.mixins.json", "mixins.sound_isolating_eraser.refmap.json",
                 "assets/sound_isolating_eraser/textures/item/red_nails.png"):
        assert name in names, f"Missing installed resource: {name}"
    assert VERSION in archive.read("META-INF/mods.toml").decode()

dependencies = [dependency("top.theillusivec4.curios", "curios-forge", PROPS["curios_version"].strip()),
                dependency("software.bernie.geckolib", "geckolib-forge-" + MC, PROPS["geckolib_version"].strip())]
for source in [JAR, *dependencies]:
    shutil.copy2(source, MODS / source.name)

installer = RESULTS / f"forge-{MC}-{FORGE}-installer.jar"
url = f"https://maven.minecraftforge.net/net/minecraftforge/forge/{MC}-{FORGE}/{installer.name}"
download(url, installer)
with urllib.request.urlopen(url + ".sha1", timeout=60) as response:
    expected_sha1 = response.read().decode().strip().split()[0]
assert hashlib.sha1(installer.read_bytes()).hexdigest() == expected_sha1, "Official installer checksum"
with (RESULTS / "installer.log").open("w") as log:
    install = subprocess.run(["java", "-jar", str(installer), "--installServer", str(SERVER)],
                             cwd=RESULTS, stdout=log, stderr=subprocess.STDOUT, timeout=600)
assert install.returncode == 0, f"Forge installer exit {install.returncode}"

(SERVER / "eula.txt").write_text("eula=true\n")
(SERVER / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25642\nonline-mode=false\n"
    "level-type=minecraft:flat\ngenerate-structures=false\nspawn-monsters=false\n"
    "view-distance=3\nsimulation-distance=3\nspawn-protection=0\n")
args = SERVER / "libraries/net/minecraftforge/forge" / f"{MC}-{FORGE}" / "unix_args.txt"
assert args.is_file(), "Installed Forge argument file"
log_path = RESULTS / "installed-server.log"
process = None


def await_log(marker, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = log_path.read_text(errors="replace")
        if marker in text:
            return
        if process.poll() is not None:
            raise AssertionError(f"Installed server exited {process.returncode} before {marker}")
        time.sleep(1)
    raise TimeoutError(f"Installed server did not emit {marker}")


def command(text):
    process.stdin.write(text + "\n")
    process.stdin.flush()


try:
    with log_path.open("w") as log:
        process = subprocess.Popen(["java", "-XX:ActiveProcessorCount=2", "-Xmx2g", "@" + str(args), "nogui"],
                                   cwd=SERVER, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        await_log('For help, type "help"')
        command("gamerule doMobSpawning false")
        command("gamerule doDaylightCycle false")
        command("forceload add -16 -16 16 16")
        command("fill -5 -59 -5 5 -52 5 minecraft:water")
        command("summon sound_isolating_eraser:water_ghost 0 -55 0")
        command('summon minecraft:item 8 -59 0 {Item:{id:"sound_isolating_eraser:red_nails",Count:1b}}')
        command("summon sound_isolating_eraser:suxia 10 -59 0")
        # Wait for actual installed production entities to tick before checking them.
        time.sleep(5)
        command("execute if entity @e[type=sound_isolating_eraser:water_ghost] run say SRG_WATER_GHOST_OK")
        command('execute if entity @e[type=minecraft:item,nbt={Item:{id:"sound_isolating_eraser:red_nails"}}] run say SRG_RED_NAILS_ITEM_OK')
        command("execute if entity @e[type=sound_isolating_eraser:suxia] run say SRG_SUXIA_OK")
        for marker in ["SRG_WATER_GHOST_OK", "SRG_RED_NAILS_ITEM_OK", "SRG_SUXIA_OK"]:
            await_log("[Server] " + marker, timeout=30)
        command("execute if entity @e[type=sound_isolating_eraser:water_ghost,x=-1,y=-53,z=-1,dx=2,dy=1,dz=2] run say SRG_NATURAL_LURK_MOVEMENT_OK")
        await_log("[Server] SRG_NATURAL_LURK_MOVEMENT_OK", timeout=30)
        command("data get entity @e[type=sound_isolating_eraser:water_ghost,limit=1]")
        command("stop")
        assert process.wait(timeout=120) == 0, "Installed server saves and shuts down cleanly"
finally:
    if process is not None and process.poll() is None:
        try:
            command("stop")
            process.wait(timeout=60)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            process.terminate()
            process.wait(timeout=30)

text = log_path.read_text(errors="replace")
assert not any(bad in text for bad in ["Mixin apply failed", "InvalidInjectionException", "Crash report saved",
                                      "This crash report has been saved to", "Exception in server tick loop",
                                      "Encountered an unexpected exception", "NoClassDefFoundError",
                                      "NoSuchMethodError", "NoSuchFieldError", "Failed to load registries"])
assert not list((SERVER / "crash-reports").glob("*.txt")), "Installed server must not create a crash report"
report = {"source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
          "version": VERSION, "minecraft": MC, "forge": FORGE, "installer_exit": install.returncode,
          "server_exit": process.returncode, "scope": "installed SRG dedicated-server smoke, not client gameplay",
          "jar": {"name": JAR.name, "sha256": digest(JAR)},
          "dependencies": [{"name": p.name, "sha256": digest(p)} for p in dependencies],
          "installer_sha256": digest(installer), "log_sha256": digest(log_path)}
(RESULTS / "srg-installation.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
print("SRG_INSTALLED_SERVER_SMOKE_PASSED", flush=True)
