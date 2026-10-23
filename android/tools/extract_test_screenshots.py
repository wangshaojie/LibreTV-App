"""Reassemble test-only screenshots captured in each test's continuously collected logcat.

This works on API 21 without external storage permissions and survives AGP uninstalling
the instrumentation APK. Only synthetic UI test content is emitted, never user data.
"""
import base64
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
captures = {}
pattern = re.compile(r"PNG\|([a-z-]+)\|(\d+)\|(\d+)\|([A-Za-z0-9+/=]+)")
for log in (root / "app/build/outputs/androidTest-results").rglob("logcat-*.txt"):
    for match in pattern.finditer(log.read_text(encoding="utf-8", errors="replace")):
        name, index, count, chunk = match.groups()
        capture = captures.setdefault(name, {"count": int(count), "parts": {}})
        capture["parts"][int(index)] = chunk
output = root / "screenshots"
output.mkdir(exist_ok=True)
for name, capture in captures.items():
    if len(capture["parts"]) != capture["count"]:
        raise RuntimeError(f"Incomplete screenshot {name}")
    data = base64.b64decode("".join(capture["parts"][n] for n in range(capture["count"])), validate=True)
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Invalid screenshot {name}")
    (output / f"{name}.png").write_bytes(data)
    print(f"Screenshot: {name} ({len(data)} bytes)")
expected = {"home", "movies", "search", "detail", "player", "sources", "probe"}
missing = expected - captures.keys()
if missing:
    raise RuntimeError(f"Missing screenshots: {sorted(missing)}")
