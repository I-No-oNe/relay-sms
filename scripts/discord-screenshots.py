"""Send CI screenshots to Discord."""
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlsplit, urlunsplit
from urllib.request import Request, urlopen
import uuid


def main():
    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not webhook:
        print("DISCORD_WEBHOOK_URL secret is missing.")
        return 1
    parsed = urlsplit(webhook)
    if parsed.scheme != "https" or parsed.hostname != "discord.com" or not parsed.path.startswith("/api/webhooks/"):
        print("Invalid Discord webhook configuration.")
        return 1
    paths = [Path("test-results") / name for name in ["home-phone.png", "import-phone.png", "review-phone.png", "wrong-script-phone.png", "no-name-phone.png", "messages-phone.png", "ready-phone.png", "bulk-700-phone.png"]]
    paths = [path if path.is_file() else Path("android") / path for path in paths]
    paths = [path for path in paths if path.is_file()]
    payload = {"content": "Relay SMS · native Android emulator screenshots · " + os.environ.get("CHECK_STATUS", "unknown") + "\n" + os.environ.get("RUN_URL", ""), "allowed_mentions": {"parse": []}}
    if not paths:
        payload["content"] += "\nNo screenshots were produced; inspect the workflow logs."
    boundary = uuid.uuid4().hex
    body = bytearray()
    def field(name, value, filename=None):
        body.extend(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"" + ("; filename=\"" + filename + "\"" if filename else "") + "\r\n" + ("Content-Type: image/png\r\n" if filename else "") + "\r\n").encode())
        body.extend(value)
        body.extend(b"\r\n")
    field("payload_json", json.dumps(payload).encode())
    for index, path in enumerate(paths):
        if path.stat().st_size > 8_000_000:
            print("Screenshot exceeds upload limit.")
            return 1
        field("files[" + str(index) + "]", path.read_bytes(), path.name)
    body.extend(("--" + boundary + "--\r\n").encode())
    request = Request(urlunsplit(parsed._replace(query="wait=true")), data=bytes(body), headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "User-Agent": "RelaySMS-CI"}, method="POST")
    try:
        with urlopen(request, timeout=40) as response:
            result = json.load(response)
        if not result.get("id") or len(result.get("attachments", [])) != len(paths):
            print("Discord attachment acknowledgment incomplete.")
            return 1
    except Exception:
        # the error can include the webhook URL, so don't print it
        print("Discord upload failed; delivery unconfirmed.")
        return 1
    print("Discord confirmed receipt of " + str(len(paths)) + " screenshots.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
