#!/usr/bin/env python3
"""Fail the build when a mixin injector would not resolve in production (SRG) Forge.

Our refmaps are handwritten. A remapped (default) injector target therefore needs an entry in
its config's refmap "mappings"; otherwise the target must use remap = false and list the SRG
name (m_NNN_) next to the MCP name. CityArrivalSleepMixin broke this rule and crashed 0.12.0
at boot ("could not find any targets matching 'stopSleeping'").
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
SRC = ROOT / "src/main/java"
ANN = re.compile(r"@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|ModifyExpressionValue|WrapOperation|WrapWithCondition)\s*\(")

def annotation_body(text, start):
    depth, i = 0, start
    while i < len(text):
        if text[i] == "(": depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0: return text[start + 1:i]
        i += 1
    return text[start + 1:]

def main():
    failures, checked = [], 0
    for cfg_path in sorted(RES.glob("*.mixins.json")):
        cfg = json.loads(cfg_path.read_text())
        refmap = json.loads((RES / cfg["refmap"]).read_text()) if cfg.get("refmap") else {}
        mappings = refmap.get("mappings", {})
        pkg = cfg["package"]
        for name in cfg.get("mixins", []) + cfg.get("client", []) + cfg.get("server", []):
            cls = pkg + "." + name
            src = SRC / (cls.replace(".", "/") + ".java")
            text = src.read_text()
            owner = cls.replace(".", "/")
            for m in ANN.finditer(text):
                body = annotation_body(text, m.end() - 1)
                outer = re.sub(r"@At\s*\((?:[^()]|\([^()]*\))*\)", "", body)
                mm = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", outer)
                if not mm: continue
                names = re.findall(r"\"([^\"]+)\"", mm.group(1))
                checked += 1
                if re.search(r"remap\s*=\s*false", outer):
                    if not any(re.match(r"m_\d+_", n) for n in names) and not re.search(r"require\s*=\s*0", outer):
                        failures.append(f"{name}: remap=false target {names} has no SRG m_NNN_ name")
                    continue
                missing = [n for n in names if n not in mappings.get(owner, {})]
                if missing:
                    failures.append(f"{name}: remapped target(s) {missing} absent from {cfg.get('refmap')} mappings")
    for f in failures: print("MIXIN_TARGET_FAIL " + f, file=sys.stderr)
    print(f"MIXIN_TARGETS checked={checked} failures={len(failures)}")
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
