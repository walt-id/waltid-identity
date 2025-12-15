# Annex C Support – Specification (Draft)

## Goal
Add ISO/IEC 18013-7 Annex C support to enable verification via DC API (Apple Wallet compatible).

## Non-goals
- OpenID4VP for this flow
- Wallet-side logic

## High-level flow
Backend creates a short-lived Annex C session, then builds `DeviceRequest` + `EncryptionInfo` and returns a DC API protocol object.
Wallet returns an HPKE-wrapped `EncryptedResponse`. Backend stores the response, kicks off validation asynchronously, and exposes results via an info endpoint (mirrors the existing verifier2 create/request/response/info pattern).

## Backend API (Verifier2)
### POST /annex-c/create
Request:
```json
{
  "docType": "org.iso.18013.5.1.mDL",
  "requestedElements": { "org.iso.18013.5.1": ["age_over_18"] },
  "policies": ["mdoc-device-auth","mdoc-issuer-auth"],
  "origin": "https://verifier.multipaz.org"
}
```

Response:
```json
{
  "sessionId": "<uuid>",
  "expiresAt": "<iso8601>"
}
```

### POST /annex-c/request
Request:
```json
{
  "sessionId": "<uuid>"
}
```

Response:
```json
{
  "protocol": "org.iso.mdoc",
  "data": {
    "deviceRequest": "<b64url(cbor(DeviceRequest))>",
    "encryptionInfo": "<b64url(cbor(EncryptionInfo))>"
  },
  "meta": { "sessionId": "<uuid>", "expiresAt": "<iso8601>" }
}
```

Notes:
- `protocol` is `AnnexC.PROTOCOL` from `waltid-18013-7-verifier`.
- `deviceRequest` and `encryptionInfo` are base64url(no-pad) CBOR strings (internally: `deviceRequestB64` / `encryptionInfoB64`).

### POST /annex-c/response
Request:
```json
{
  "sessionId": "<uuid>",
  "response": "<b64url(cbor(EncryptedResponse))>"
}
```

Response: acknowledgement only (results are written to the Annex C session and can be retrieved via `/annex-c/info`).

### GET /annex-c/info
Query:
`?sessionId=<uuid>`

Response: session state plus (once available) the validation result (policy results + extracted credentials), shaped to be easy to consume alongside existing verifier2 `/verification-session/{id}/info` responses.

## SessionTranscript
- dcapiInfo = [ Base64EncryptionInfoString, SerializedOriginString ]
- dcapiInfoHash = SHA-256(CBOR(dcapiInfo))
- SessionTranscript = [ null, null, ["dcapi", dcapiInfoHash] ]
- HPKE info = CBOR(SessionTranscript)
