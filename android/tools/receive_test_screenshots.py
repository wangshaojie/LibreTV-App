"""Local CI-only PNG receiver for the emulator's 10.0.2.2 host alias.

It listens only on the runner's loopback interface. Test captures contain synthetic
catalogue data; no screenshot capture or upload code is included in release APKs.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re

parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=18765)
parser.add_argument("--output", default="android/screenshots")
args = parser.parse_args()
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        name = self.path.removeprefix("/")
        size = int(self.headers.get("Content-Length", "0"))
        if not re.fullmatch(r"[a-z-]+\.png", name) or not 0 < size <= 10 * 1024 * 1024:
            self.send_error(400)
            return
        data = self.rfile.read(size)
        if len(data) != size or not data.startswith(b"\x89PNG\r\n\x1a\n"):
            self.send_error(400)
            return
        (output / name).write_bytes(data)
        self.send_response(204)
        self.end_headers()

    def log_message(self, *_args):
        pass


ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
