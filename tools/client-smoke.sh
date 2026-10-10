#!/usr/bin/env bash
set -euo pipefail

mkdir -p build run/config
# The early Forge splash window can time out under Xvfb. The real Minecraft
# window, OpenGL resource reload, mixins, and screenshot checks remain enabled.
python3 - <<'PY'
from pathlib import Path
import re
path = Path("run/config/fml.toml")
text = path.read_text() if path.exists() else ""
setting = "earlyWindowControl = false"
if re.search(r"(?m)^earlyWindowControl\s*=", text):
    text = re.sub(r"(?m)^earlyWindowControl\s*=.*$", setting, text)
else:
    text = setting + "\n" + text
path.write_text(text)
PY
bash ./gradlew --no-daemon runClient > build/client-smoke.log 2>&1 &
client_pid=$!
cleanup() {
    kill "$client_pid" 2>/dev/null || true
    wait "$client_pid" 2>/dev/null || true
}
trap cleanup EXIT

for attempt in $(seq 1 300); do
    if ! kill -0 "$client_pid" 2>/dev/null; then
        tail -n 120 build/client-smoke.log
        exit 1
    fi
    if grep -q 'Created:.*minecraft:textures/atlas/particles.png-atlas' build/client-smoke.log; then
        sleep 10
        kill -0 "$client_pid"
        if grep -Eq 'Mixin apply failed|InvalidInjectionException|ReportedException|Crash report saved' build/client-smoke.log; then
            tail -n 120 build/client-smoke.log
            exit 1
        fi
        java tools/CaptureClient.java build/client-smoke.png
        printf '%s\n' 'CLIENT_RESOURCE_RELOAD_AND_OUTLINE_MIXINS_PASSED'
        exit 0
    fi
    sleep 1
done

tail -n 120 build/client-smoke.log
printf '%s\n' 'Client did not finish resource reload within 300 seconds.' >&2
exit 1
