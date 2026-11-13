"""HTTP/proof integrity for the journey fixture, separate from actual wallet qualification."""

import gzip
import json
import threading
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

from issuer import GRANT, Issuer, Server, b64, encoded, unb64


class ControlledIssuerTests(unittest.TestCase):
    def setUp(self):
        self.origin = "https://issuer.example"
        fixture = Issuer(self.origin, 20)
        self.public, self.control = Server(0, fixture), Server(0, fixture, control=True)
        for server in (self.public, self.control):
            threading.Thread(target=server.serve_forever, daemon=True).start()
        self.holder = ed25519.Ed25519PrivateKey.generate()
        self.jwk = {"kty": "OKP", "crv": "Ed25519", "x": b64(self.holder.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw))}

    def tearDown(self):
        for server in (self.public, self.control):
            server.shutdown()
            server.server_close()

    def request(self, path, body=None, control=False, token=None, form=False):
        headers = {}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        if body is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
            data = urllib.parse.urlencode(body).encode() if form else encoded(body)
        else:
            data = None
        port = (self.control if control else self.public).server_port
        request = urllib.request.Request(f"http://127.0.0.1:{port}" + path, data=data, headers=headers)
        try:
            response = urllib.request.urlopen(request, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            value = json.loads(raw) if response.headers.get_content_type() == "application/json" else raw.decode()
            return response.status, value

    def access_token(self, configuration):
        status, value = self.request("/offers", {"configuration": configuration}, control=True)
        self.assertEqual(status, 200)
        offer = json.loads(urllib.parse.parse_qs(urllib.parse.urlsplit(value["credentialOffer"]).query)["credential_offer"][0])
        code = offer["grants"][GRANT]["pre-authorized_code"]
        status, value = self.request("/token", {"grant_type": GRANT, "pre-authorized_code": code}, form=True)
        self.assertEqual(status, 200)
        self.assertEqual(self.request("/token", {"grant_type": GRANT, "pre-authorized_code": code}, form=True)[0], 400)
        return value["access_token"]

    def proof(self, nonce):
        header = {"typ": "openid4vci-proof+jwt", "alg": "EdDSA", "jwk": self.jwk}
        claims = {"aud": self.origin, "iat": int(time.time()), "nonce": nonce}
        message = b64(encoded(header)) + "." + b64(encoded(claims))
        return message + "." + b64(self.holder.sign(message.encode()))

    def issue(self, configuration):
        token = self.access_token(configuration)
        status, challenge = self.request("/nonce", {})
        self.assertEqual(status, 200)
        body = {"credential_configuration_id": configuration, "proofs": {"jwt": [self.proof(challenge["c_nonce"])]}}
        status, response = self.request("/credential", body, token=token)
        self.assertEqual(status, 200)
        self.assertEqual(self.request("/credential", body, token=token)[0], 401)
        return self.verify(response["credentials"][0]["credential"]), challenge["c_nonce"]

    def verify(self, jwt):
        header, payload, signature = jwt.split(".")
        kid = json.loads(unb64(header))["kid"]
        jwk = json.loads(unb64(kid.removeprefix("did:jwk:").split("#")[0]))
        ed25519.Ed25519PublicKey.from_public_bytes(unb64(jwk["x"])).verify(unb64(signature), (header + "." + payload).encode())
        return json.loads(unb64(payload))

    def test_real_signed_issuance_rejects_bad_proofs_and_replays(self):
        metadata = self.request("/.well-known/openid-credential-issuer")[1]
        self.assertEqual(metadata["credential_configurations_supported"]["staff_access"]["format"], "jwt_vc_json")
        authorization = self.request("/.well-known/oauth-authorization-server")[1]
        self.assertEqual(authorization["grant_types_supported"], [GRANT])
        self.assertNotIn("response_types_supported", authorization)
        self.assertNotIn("authorization_endpoint", authorization)
        credential, spent_nonce = self.issue("staff_access")
        self.assertEqual(credential["vc"]["type"], ["VerifiableCredential", "WorkplaceAccessCredential"])
        self.assertEqual(credential["vc"]["credentialStatus"]["statusPurpose"], "revocation")
        self.assertEqual(credential["vc"]["credentialStatus"]["statusSize"], 1)
        token = self.access_token("event_pass")
        body = {"credential_configuration_id": "event_pass", "proofs": {"jwt": [self.proof(spent_nonce)]}}
        self.assertEqual(self.request("/credential", body, token=token), (400, {"error": "invalid_nonce"}))
        nonce = self.request("/nonce", {})[1]["c_nonce"]
        parts = self.proof(nonce).split(".")
        parts[1] = b64(encoded({"aud": self.origin, "iat": int(time.time()), "nonce": nonce, "tampered": True}))
        body["proofs"]["jwt"] = [".".join(parts)]
        self.assertEqual(self.request("/credential", body, token=token), (400, {"error": "invalid_proof"}))
        body["proofs"]["jwt"] = [self.proof(nonce)]
        self.assertEqual(self.request("/credential", body, token=token)[0], 200)

    def test_signed_transitions_are_scoped_and_revocation_cannot_be_undone(self):
        self.issue("staff_access")
        self.issue("staff_access_alternative")
        self.issue("event_pass")
        revoked = {"purpose": "revocation", "index": 0, "value": 1}
        self.assertEqual(self.request("/status", revoked)[0], 404, "Public traffic cannot mutate status")
        self.assertEqual(self.request("/status", revoked, control=True)[0], 200)
        status, signed = self.request("/status/revocation")
        self.assertEqual(status, 200)
        subject = self.verify(signed)["vc"]["credentialSubject"]
        self.assertEqual(subject["ttl"], 20_000)
        self.assertTrue(subject["encodedList"].startswith("u"))
        bits = gzip.decompress(unb64(subject["encodedList"][1:]))
        self.assertEqual(len(bits), 16_384)
        self.assertEqual(bits[:2], bytes([0x80, 0]), "W3C index zero is the first MSB; the alternative remains clear")
        self.assertEqual(self.request("/status", {**revoked, "value": 0}, control=True),
                         (409, {"error": "revocation_is_irreversible"}))
        for value in (1, 0):
            self.assertEqual(self.request("/status", {"purpose": "suspension", "index": 0, "value": value}, control=True)[0], 200)
            signed = self.request("/status/suspension")[1]
            subject = self.verify(signed)["vc"]["credentialSubject"]
            self.assertEqual(gzip.decompress(unb64(subject["encodedList"][1:]))[0], 0x80 if value else 0)
        self.request("/outage", {"enabled": True}, control=True)
        self.assertEqual(self.request("/status/revocation")[0], 503)
        self.assertEqual(self.request("/offers", {"configuration": "staff_access"})[0], 404)


if __name__ == "__main__":
    unittest.main()
