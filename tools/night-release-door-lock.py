"""Run a remapped probe mod against the unmodified release JAR, never a userdev classpath.

Build: ./gradlew -PnightReleaseQA build nightReleaseQAJar
Prepare an isolated Forge 1.20.1-47.2.0 install, then pass its directory and an
existing accepted EULA file. No mock players, production worlds or public ports.
"""
import argparse
import hashlib
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--eula", type=Path, required=True)
    parser.add_argument("--mod-jar", type=Path, required=True,
                        help="Exact release JAR under test, not a userdev or sources JAR")
    parser.add_argument("--timeout-seconds", type=int, default=420,
                        help="Bounded startup budget for the installed server")
    args = parser.parse_args()
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    root = Path(__file__).resolve().parents[1]
    server = args.server_dir.resolve()
    launch = server / "libraries/net/minecraftforge/forge/1.20.1-47.2.0/unix_args.txt"
    if not launch.is_file() or "eula=true" not in args.eula.read_text():
        raise RuntimeError("Requires the specified Forge installation and a previously accepted EULA")
    # Refuse to repurpose an existing game world or overwrite unrelated server settings.
    if (server / "world").exists() or (server / "server.properties").exists():
        raise RuntimeError("Use a fresh isolated server directory; existing world/settings are protected")
    mods = server / "mods"
    mods.mkdir(exist_ok=True)
    if any(mods.iterdir()):
        raise RuntimeError("Probe requires an empty mods directory")
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    jars = [args.mod_jar.resolve()]
    jars += list((root / "build/night-release-qa/probe").glob("*.jar"))
    for group, artifact in (("software.bernie.geckolib", "geckolib-forge-1.20.1"),
                            ("top.theillusivec4.curios", "curios-forge")):
        candidates = [p for p in (cache / group / artifact).glob("*/*/*.jar")
                      if not p.name.endswith(("-api.jar", "-sources.jar", "-javadoc.jar"))]
        if len(candidates) != 1:
            raise RuntimeError(f"Expected one cached release dependency: {artifact}, got {candidates}")
        jars += candidates
    if len(jars) != 4 or not all(jar.is_file() for jar in jars):
        raise RuntimeError(f"Expected release mod, remapped probe, GeckoLib and Curios: {jars}")
    result = root / "build/night-release-qa"
    result.mkdir(exist_ok=True)
    manifest = []
    for jar in jars:
        shutil.copy2(jar, mods / jar.name)
        manifest.append(hashlib.sha256(jar.read_bytes()).hexdigest() + "  " + jar.name)
    (result / "jar-sha256.txt").write_text("\n".join(manifest) + "\n")
    shutil.copy2(args.eula, server / "eula.txt")
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\n"
        "enforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nspawn-monsters=false\n"
        "max-tick-time=0\n")
    logfile = result / "server.log"
    command = ["java", "-Xmx1G", "-Dforge.logging.console.level=info", "@" + str(launch), "--nogui"]
    print("RUN", " ".join(command), flush=True)
    with logfile.open("w") as log:
        completed = subprocess.run(command, cwd=server, stdout=log, stderr=subprocess.STDOUT,
                                   timeout=args.timeout_seconds)
    text = logfile.read_text(errors="replace")
    for line in text.splitlines():
        if "NIGHT_RELEASE_DOOR_" in line or "ModLauncher running" in line:
            print(line)
    if (completed.returncode or "NIGHT_RELEASE_DOOR_LOCK_PASS" not in text
            or "NIGHT_RELEASE_DOOR_ENTRYPOINTS_PASS" not in text or "NIGHT_RELEASE_DOOR_LOCK_FAIL" in text):
        raise RuntimeError(f"Release runtime verification failed (exit {completed.returncode}); see {logfile}")
    print("NIGHT_RELEASE_RUNTIME_VERIFIED", logfile)


if __name__ == "__main__":
    main()
