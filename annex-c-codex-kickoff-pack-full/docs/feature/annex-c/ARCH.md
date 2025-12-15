# Annex C Support – Architecture (Draft)

## Components

- `waltid-18013-7-verifier` (new library)
  - Provides Annex C protocol constants (`AnnexC.PROTOCOL = "org.iso.mdoc"`)
  - Builds Annex C request structures:
    - `DeviceRequest` CBOR -> `deviceRequestB64` (base64url, no pad)
    - `EncryptionInfo` CBOR -> `encryptionInfoB64` (base64url, no pad)
  - Verifies Annex C responses by decrypting to a 18013-5 `DeviceResponse`:
    - `EncryptedResponse` CBOR (base64url) + `encryptionInfoB64` + `origin` + `recipientPrivateKey`
    - -> `deviceResponseCborBytes`
    - implemented via `AnnexCResponseVerifier.decryptToDeviceResponse(...)`

- `waltid-verifier-api2` (existing service)
  - Hosts endpoints `/annex-c/create`, `/annex-c/request`, `/annex-c/response`, `/annex-c/info`
  - Manages transaction sessions (TTL, nonce, recipient keypair, policies)
  - Calls existing policy engine / mdoc validator with decoded `DeviceResponse`

## Data flow

Frontend (RP) → POST /annex-c/create → sessionId → POST /annex-c/request → protocol object → DC API call → Wallet →
returns encrypted response → POST /annex-c/response → async decrypt/validate → GET /annex-c/info → result.

## Trust boundaries

- Origin binding is critical to prevent relay:
  - Backend must use `origin` (serialized origin string) in SessionTranscript
  - Backend should compare received origin to stored origin for the transaction
- Transaction session must be short-lived (e.g., 5 minutes)
- Recipient private key must never leave backend

## Storage

- In-memory store acceptable for PoC / portal test
- Optionally plug into existing session store used by verifier2
