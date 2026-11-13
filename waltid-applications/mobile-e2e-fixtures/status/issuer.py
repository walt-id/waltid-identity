#!/usr/bin/env python3
"""Bounded, ephemeral issuer for real wallet status journeys, never a production issuer.

OID4VCI 1.0 pre-authorized JWT proofs; JWT VC + Bitstring Status List 2025.
The public listener has no mutation controls. Tunnel only that listener over HTTPS.
"""

import argparse
import base64
import gzip
import hashlib
import io
import json
import secrets
import threading
import time
import uuid
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, ed25519
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
from PIL import Image, ImageDraw, ImageFont


GRANT = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
MAX_ACTIVE = 64
MAX_BODY = 65_536


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def unb64(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def encoded(value):
    return json.dumps(value, separators=(",", ":"), sort_keys=True).encode()


def did_for(jwk):
    return "did:jwk:" + b64(encoded(jwk))


@dataclass(frozen=True)
class Profile:
    title: str
    credential_type: str
    purpose: str
    color: str


PROFILES = {
    "staff_access": Profile("Workplace access", "WorkplaceAccessCredential", "revocation", "#17446B"),
    "staff_access_alternative": Profile("Spare workplace access", "WorkplaceAccessCredential", "revocation", "#38586D"),
    "event_pass": Profile("Conference pass", "ConferencePassCredential", "suspension", "#8A4B16"),
}


class FixtureError(Exception):
    def __init__(self, code, status=400):
        self.code, self.status = code, status


class Issuer:
    def __init__(self, issuer_url, ttl):
        self.url, self.ttl = issuer_url, ttl
        self.key = ed25519.Ed25519PrivateKey.generate()
        self.jwk = {"kty": "OKP", "crv": "Ed25519", "x": b64(self.key.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw))}
        self.did = did_for(self.jwk)
        self.lock = threading.RLock()
        self.offers, self.tokens, self.nonces = {}, {}, {}
        self.issued = []
        self.values = {"revocation": {}, "suspension": {}}
        self.outage = False
        self.art = {name: self.card(profile) for name, profile in PROFILES.items()}

    @staticmethod
    def card(profile):
        image = Image.new("RGB", (1280, 807), profile.color)
        draw = ImageDraw.Draw(image)
        for radius in range(150, 1500, 130):
            draw.ellipse((1120-radius, 620-radius, 1120+radius, 620+radius), outline="#8C9DA7", width=2)
        draw.text((64, 64), profile.title, fill="white", font=ImageFont.load_default(size=62))
        draw.text((64, 150), "Controlled issuer journey", fill="white", font=ImageFont.load_default(size=30))
        draw.rounded_rectangle((1000, 46, 1225, 108), radius=30, fill="white")
        draw.text((1030, 59), "JWT VC", fill=profile.color, font=ImageFont.load_default(size=30))
        draw.text((875, 690), "STATUS TEST ISSUER", fill="white", font=ImageFont.load_default(size=25))
        output = io.BytesIO()
        image.save(output, format="PNG")
        return output.getvalue()

    def sign(self, payload):
        header = {"alg": "EdDSA", "kid": self.did + "#0", "typ": "vc+jwt"}
        signing_input = b64(encoded(header)) + "." + b64(encoded(payload))
        return signing_input + "." + b64(self.key.sign(signing_input.encode()))

    def metadata(self):
        return {"credential_issuer": self.url, "display": [{"name": "Wallet status test issuer", "locale": "en"}],
                "credential_endpoint": self.url + "/credential",
                "nonce_endpoint": self.url + "/nonce", "credential_configurations_supported": {
                    name: {"format": "jwt_vc_json", "credential_definition": {
                        "type": ["VerifiableCredential", profile.credential_type]},
                        "cryptographic_binding_methods_supported": ["jwk"],
                        "credential_signing_alg_values_supported": ["EdDSA"],
                        "proof_types_supported": {"jwt": {"proof_signing_alg_values_supported": ["ES256", "EdDSA"]}},
                        "credential_metadata": {"display": [{"name": profile.title, "locale": "en",
                            "description": "Non-personal credential for the controlled status journey",
                            "background_color": profile.color, "text_color": "#FFFFFF",
                            "background_image": {"uri": self.url + "/art/" + name + ".png"}}]}}
                    for name, profile in PROFILES.items()}}

    @staticmethod
    def remember(collection, value):
        now = time.time()
        for key in list(collection):
            if collection[key][0] <= now:
                del collection[key]
        if len(collection) >= MAX_ACTIVE:
            raise FixtureError("fixture_capacity", 429)
        key = secrets.token_urlsafe(32)
        collection[key] = (now + 300, value)
        return key

    @staticmethod
    def lookup(collection, key):
        record = collection.get(key)
        if record is None or record[0] <= time.time():
            raise FixtureError("invalid_grant")
        return record[1]

    def offer(self, configuration):
        if configuration not in PROFILES:
            raise FixtureError("unknown_configuration")
        code = self.remember(self.offers, configuration)
        offer = {"credential_issuer": self.url, "credential_configuration_ids": [configuration],
                 "grants": {GRANT: {"pre-authorized_code": code}}}
        return {"credentialOffer": "openid-credential-offer://?" + urlencode({"credential_offer": encoded(offer).decode()}),
                "configuration": configuration}

    def token(self, parameters):
        if parameters.get("grant_type") != [GRANT]:
            raise FixtureError("unsupported_grant_type")
        code = parameters.get("pre-authorized_code", [None])[0]
        configuration = self.lookup(self.offers, code)
        token = self.remember(self.tokens, (configuration, parameters.get("client_id", [None])[0]))
        del self.offers[code]
        return {"access_token": token, "token_type": "Bearer", "expires_in": 300}

    def holder(self, proof, client_id):
        try:
            head, body, signature = proof.split(".")
            header, claims = json.loads(unb64(head)), json.loads(unb64(body))
            jwk = header["jwk"]
            if "kid" in header or "x5c" in header or any(k in jwk for k in ("d", "p", "q", "k")):
                raise ValueError()
            if header["typ"] != "openid4vci-proof+jwt" or claims["aud"] != self.url:
                raise ValueError()
            if claims.get("iss") != client_id or (client_id is None and "iss" in claims):
                raise ValueError()
            if not isinstance(claims["iat"], (int, float)) or abs(time.time() - claims["iat"]) > 120:
                raise ValueError()
            signing_input = (head + "." + body).encode()
            raw_signature = unb64(signature)
            if header["alg"] == "ES256" and jwk["kty"] == "EC" and jwk["crv"] == "P-256":
                if len(raw_signature) != 64:
                    raise ValueError()
                key = ec.EllipticCurvePublicNumbers(int.from_bytes(unb64(jwk["x"]), "big"),
                    int.from_bytes(unb64(jwk["y"]), "big"), ec.SECP256R1()).public_key()
                key.verify(encode_dss_signature(int.from_bytes(raw_signature[:32], "big"),
                    int.from_bytes(raw_signature[32:], "big")), signing_input, ec.ECDSA(hashes.SHA256()))
            elif header["alg"] == "EdDSA" and jwk["kty"] == "OKP" and jwk["crv"] == "Ed25519":
                ed25519.Ed25519PublicKey.from_public_bytes(unb64(jwk["x"])).verify(raw_signature, signing_input)
            else:
                raise ValueError()
            nonce = claims["nonce"]
            self.lookup(self.nonces, nonce)
            return jwk, nonce
        except FixtureError:
            raise FixtureError("invalid_nonce") from None
        except Exception:
            raise FixtureError("invalid_proof") from None

    def credential(self, token, request):
        try:
            configuration, client_id = self.lookup(self.tokens, token)
        except FixtureError:
            raise FixtureError("invalid_token", 401) from None
        if request.get("credential_configuration_id") != configuration:
            raise FixtureError("unknown_credential_configuration")
        proofs = request.get("proofs", {}).get("jwt", [])
        if not isinstance(proofs, list) or len(proofs) != 1 or not isinstance(proofs[0], str):
            raise FixtureError("invalid_proof")
        holder, nonce = self.holder(proofs[0], client_id)
        if len(self.issued) >= MAX_ACTIVE:
            raise FixtureError("fixture_capacity", 429)
        profile = PROFILES[configuration]
        index = len(self.values[profile.purpose])
        now = int(time.time())
        identifier = "urn:uuid:" + str(uuid.uuid4())
        list_url = self.url + "/status/" + profile.purpose
        subject = {"id": did_for(holder), "given_name": "Alex", "family_name": "Example",
                   "pass_number": f"TEST-{len(self.issued)+1:03d}"}
        if profile.purpose == "suspension":
            subject["event"] = "Wallet developer conference"
        else:
            subject["access_area"] = "Demo workspace"
        vc = {"@context": ["https://www.w3.org/2018/credentials/v1", "https://www.w3.org/ns/credentials/status/v1"],
              "id": identifier, "type": ["VerifiableCredential", profile.credential_type], "issuer": self.did,
              "issuanceDate": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now)), "credentialSubject": subject,
              "credentialStatus": {"id": list_url + "#" + str(index), "type": "BitstringStatusListEntry",
                  "statusPurpose": profile.purpose, "statusSize": 1,
                  "statusListIndex": str(index), "statusListCredential": list_url}}
        credential = self.sign({"iss": self.did, "sub": did_for(holder), "jti": identifier,
            "iat": now, "nbf": now-5, "exp": now+3600, "vc": vc})
        self.values[profile.purpose][index] = 0
        self.issued.append({"id": identifier, "configuration": configuration, "purpose": profile.purpose,
                            "index": index, "credentialDigest": hashlib.sha256(credential.encode()).hexdigest()})
        del self.nonces[nonce]
        del self.tokens[token]
        return {"credentials": [{"credential": credential}]}

    def status_list(self, purpose):
        if purpose not in self.values:
            raise FixtureError("not_found", 404)
        if self.outage:
            raise FixtureError("controlled_outage", 503)
        bits = bytearray(16_384)
        for index, value in self.values[purpose].items():
            bits[index // 8] |= value << (7 - index % 8)
        now, uri = int(time.time()), self.url + "/status/" + purpose
        vc = {"@context": ["https://www.w3.org/2018/credentials/v1", "https://www.w3.org/ns/credentials/status/v1"],
              "id": uri, "type": ["VerifiableCredential", "BitstringStatusListCredential"], "issuer": self.did,
              "credentialSubject": {"id": uri + "#list", "type": "BitstringStatusList", "statusPurpose": purpose,
                  "ttl": self.ttl * 1000, "encodedList": "u" + b64(gzip.compress(bytes(bits), mtime=0))}}
        return self.sign({"iss": self.did, "sub": uri, "iat": now, "exp": now+3600, "vc": vc})


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, *_):
        pass  # Offers, tokens and credential requests must never enter access logs.

    def reply(self, value, status=200, content_type="application/json", cache="no-store"):
        body = value if isinstance(value, bytes) else encoded(value)
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", cache)
        self.send_header("Content-Length", str(len(body)))
        if not self.server.control and content_type == "image/png":
            self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    def dispatch(self, method):
        fixture, path = self.server.fixture, urlsplit(self.path).path
        try:
            with fixture.lock:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 <= length <= MAX_BODY:
                    raise FixtureError("request_too_large", 413)
                raw = self.rfile.read(length)
                body = json.loads(raw) if raw and self.headers.get_content_type() == "application/json" else {}
                if not isinstance(body, dict):
                    raise FixtureError("invalid_request")
                if self.server.control:
                    if method == "POST" and path == "/offers":
                        return self.reply(fixture.offer(body.get("configuration")))
                    if method == "GET" and path == "/state":
                        return self.reply({"issued": fixture.issued, "outage": fixture.outage, "values": fixture.values})
                    if method == "POST" and path == "/outage" and isinstance(body.get("enabled"), bool):
                        fixture.outage = body["enabled"]
                        return self.reply({"outage": fixture.outage})
                    if method == "POST" and path == "/status":
                        purpose, index, value = body.get("purpose"), body.get("index"), body.get("value")
                        if purpose not in fixture.values or type(index) is not int or index not in fixture.values[purpose] or type(value) is not int or value not in (0, 1):
                            raise FixtureError("invalid_status_control")
                        if purpose == "revocation" and fixture.values[purpose][index] == 1 and value == 0:
                            raise FixtureError("revocation_is_irreversible", 409)
                        fixture.values[purpose][index] = value
                        return self.reply({"purpose": purpose, "index": index, "value": value})
                    raise FixtureError("not_found", 404)
                if method == "GET" and path == "/.well-known/openid-credential-issuer":
                    return self.reply(fixture.metadata())
                if method == "GET" and path == "/.well-known/oauth-authorization-server":
                    return self.reply({"issuer": fixture.url, "token_endpoint": fixture.url + "/token",
                        "grant_types_supported": [GRANT], "token_endpoint_auth_methods_supported": ["none"],
                        "pre-authorized_grant_anonymous_access_supported": True})
                if method == "POST" and path == "/token":
                    return self.reply(fixture.token(parse_qs(raw.decode())))
                if method == "POST" and path == "/nonce":
                    return self.reply({"c_nonce": fixture.remember(fixture.nonces, True)})
                if method == "POST" and path == "/credential":
                    authorization = self.headers.get("Authorization", "")
                    if not authorization.startswith("Bearer "):
                        raise FixtureError("invalid_token", 401)
                    return self.reply(fixture.credential(authorization[7:], body))
                if method == "GET" and path.startswith("/status/"):
                    return self.reply(fixture.status_list(path.rsplit("/", 1)[1]).encode(),
                        content_type="application/vc+jwt", cache=f"max-age={fixture.ttl}")
                if method == "GET" and path.startswith("/art/"):
                    art = fixture.art.get(path.rsplit("/", 1)[1].removesuffix(".png"))
                    if art is not None:
                        return self.reply(art, content_type="image/png", cache="max-age=3600")
                raise FixtureError("not_found", 404)
        except FixtureError as error:
            self.reply({"error": error.code}, error.status)
        except (ValueError, KeyError, TypeError):
            self.reply({"error": "invalid_request"}, 400)


class Server(HTTPServer):
    def __init__(self, port, fixture, control=False):
        self.fixture, self.control = fixture, control
        super().__init__(("127.0.0.1", port), Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issuer-url", required=True)
    parser.add_argument("--port", type=int, default=8089)
    parser.add_argument("--control-port", type=int, default=8090)
    parser.add_argument("--ttl", type=int, default=20)
    args = parser.parse_args()
    url = urlsplit(args.issuer_url)
    if url.scheme != "https" or not url.netloc or url.username or url.password or url.path not in ("", "/") or url.query or url.fragment:
        parser.error("issuer-url must be an HTTPS origin")
    if not 1 <= args.ttl <= 300 or args.port == args.control_port:
        parser.error("use distinct listener ports and a TTL from 1 to 300 seconds")
    fixture = Issuer(args.issuer_url.rstrip("/"), args.ttl)
    public, control = Server(args.port, fixture), Server(args.control_port, fixture, control=True)
    threading.Thread(target=control.serve_forever, daemon=True).start()
    print(json.dumps({"issuer": fixture.url, "publicPort": args.port, "controlPort": args.control_port}), flush=True)
    try:
        public.serve_forever()
    finally:
        public.server_close()
        control.shutdown()
        control.server_close()


if __name__ == "__main__":
    main()
