from pathlib import Path

folder = Path(__file__).resolve().parents[1] / "screenshots"
for name in ("home", "movies", "search", "detail", "player", "sources", "probe"):
    data = (folder / f"{name}.png").read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Invalid screenshot: {name}")
    print(f"Verified screenshot: {name} ({len(data)} bytes)")
