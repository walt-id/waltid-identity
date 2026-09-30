#!/usr/bin/env python3
"""Local issuer for the optional iOS rejected-credential cache acceptance check.

Start with --state-file PATH. Pass each generated offer URL to the corresponding
UI test as CREDENTIAL_CACHE_TEST_OFFER_URL. After it passes, use --inspect APP
--container DATA_CONTAINER to scan Library/Caches and tmp for the generated marker.
The fixture never sets Cache-Control.
All tokens and credential bodies are synthetic. No external service is used.
"""

import argparse
import hashlib
import json
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlencode, urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-file", type=Path, required=True)
    parser.add_argument("--inspect", choices=("native", "compose"))
    parser.add_argument("--container", type=Path)
    args = parser.parse_args()
    if args.inspect:
        if args.container is None:
            parser.error("--container is required for inspection")
        inspect_cache(args.state_file, args.inspect, args.container)
        return
    lock = threading.Lock()
    state = {app: {"marker": f"WAL1498-REJECTED-{app}-{uuid.uuid4()}", "requests": []}
             for app in ("native", "compose")}

    def save():
        temporary = args.state_file.with_suffix(args.state_file.suffix + ".tmp")
        temporary.write_text(json.dumps(state, indent=2) + "\n")
        temporary.replace(args.state_file)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            self.respond()

        def do_POST(self):
            self.respond()

        def respond(self):
            path = urlsplit(self.path).path
            app = next((name for name in state if name in path.split("/")), None)
            if app is None:
                self.send_error(404)
                return
            issuer = f"http://127.0.0.1:{self.server.server_port}/{app}"
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            with lock:
                state[app]["requests"].append({"method": self.command, "path": path,
                    "authorized": self.headers.get("Authorization") == "Bearer SYNTHETIC-CACHE-TOKEN"})
                save()
            if path.endswith("/offer"):
                payload = {"credential_issuer": issuer, "credential_configuration_ids": ["cache-test"],
                    "grants": {"urn:ietf:params:oauth:grant-type:pre-authorized_code":
                        {"pre-authorized_code": "SYNTHETIC-CACHE-CODE"}}}
            elif "openid-credential-issuer" in path:
                payload = {"credential_issuer": issuer, "credential_endpoint": issuer + "/credential",
                    "credential_configurations_supported": {"cache-test": {"format": "jwt_vc_json",
                        "credential_definition": {"type": ["VerifiableCredential", "CacheTest"]}}}}
            elif "oauth-authorization-server" in path or "openid-configuration" in path:
                payload = {"issuer": issuer, "token_endpoint": issuer + "/token",
                    "response_types_supported": ["code"],
                    "grant_types_supported": ["urn:ietf:params:oauth:grant-type:pre-authorized_code"],
                    "pre-authorized_grant_anonymous_access_supported": True}
            elif path.endswith("/token") and self.command == "POST":
                payload = {"access_token": "SYNTHETIC-CACHE-TOKEN", "token_type": "Bearer", "expires_in": 300}
            elif path.endswith("/credential") and self.command == "POST":
                # A received HTTP success body that CredentialParser must reject before persistence.
                payload = {"credentials": [{"credential": state[app]["marker"] + "-" + "x" * 262144}]}
            else:
                self.send_error(404)
                return
            response = json.dumps(payload, separators=(",", ":")).encode()
            if path.endswith("/credential"):
                with lock:
                    state[app]["responseBytes"] = len(response)
                    state[app]["responseSha256"] = hashlib.sha256(response).hexdigest()
                    save()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(response)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    for app in state:
        offer_uri = f"http://127.0.0.1:{server.server_port}/{app}/offer"
        state[app]["offerUrl"] = "openid-credential-offer://?" + urlencode({"credential_offer_uri": offer_uri})
    save()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def inspect_cache(state_file, app, container):
    state = json.loads(state_file.read_text())[app]
    requests = [r for r in state["requests"] if r["path"].endswith("/credential")]
    assert len(requests) == 1 and requests[0]["authorized"], "Expected one authenticated credential request"
    assert state["responseBytes"] > 262144, "Fixture did not return the large rejected body"
    assert (container / "Library").is_dir(), "Not an installed app data container"
    needle = state["marker"].encode()
    files = []
    matches = []
    for root in (container / "Library/Caches", container / "tmp"):
        for path in root.rglob("*"):
            if path.is_file() and not path.is_symlink():
                files.append(str(path.relative_to(container)))
                with path.open("rb") as stream:
                    previous = b""
                    while chunk := stream.read(1024 * 1024):
                        window = previous + chunk
                        if needle in window:
                            matches.append(str(path.relative_to(container)))
                            break
                        previous = window[-len(needle) + 1:]
    result = {"app": app, "container": str(container), "marker": state["marker"],
        "credentialRequests": len(requests), "responseBytes": state["responseBytes"],
        "cacheFilesScanned": files, "matches": matches}
    print(json.dumps(result, indent=2))
    assert not matches, "Rejected credential body found in app cache files"


if __name__ == "__main__":
    main()
