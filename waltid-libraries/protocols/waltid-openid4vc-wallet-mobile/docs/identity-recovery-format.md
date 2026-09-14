# Identity recovery format 1

This is an internal, versioned portability format for WAL-749. It is not BIP-32, SLIP-0010,
a mnemonic format, an EUDI backup format or a FIPS-approved key-generation scheme. Changing the
following inputs or encodings requires a new version; this implementation accepts version 1 only. Independent review
of the derivation and protection model is required before release.

## Envelope supplied by the provider

The lifecycle submits UTF-8 JSON, 1–4096 bytes, to a trusted `IdentityRecoveryProvider`. The provider
must supply confidentiality, integrity and access control. The bytes are **not independently encrypted
by the core**. Block Store/Keychain rely on their documented OS protection. A custom provider may
wrap these exact bytes in a reviewed authenticated envelope whose key is recoverable independently
of the original device. Neither a wallet database key nor a local non-exportable hardware key is
implicitly an envelope key.

The record is serialized with defaults included and unknown fields rejected on read:

```json
{
  "format": "id.walt.identity-recovery",
  "version": 1,
  "identityId": "<random UUID assigned at creation>",
  "keyId": "<original logical wallet key ID>",
  "did": "<exact original did:jwk or did:key URI>",
  "publicJwk": "<original public JWK JSON string>",
  "secret": {
    "type": "derived",
    "seed": "<32-byte seed, unpadded base64url>",
    "domain": "<identityId>",
    "index": 0
  }
}
```

For an existing exportable software key, `secret` instead contains:

```json
{"type":"exported","jwk":"<validated P-256 private JWK JSON string>"}
```

There are no credential rows, issuer tokens, database-encryption keys, old device attestations or
native aliases in this record. Native aliases are newly allocated at the destination. The logical
key ID and exact DID remain unchanged. The expected public JWK supplies the public-key commitment;
comparison uses the decoded key, not string ordering or a replacement DID serialization.

Only general-purpose identities can submit or restore this format through the service. A record
is not evidence that an issuer permits credential-key migration. Hardware-generated/device-bound
host policies prohibit the operation independently of the record's cryptographic validity.

## Derivation

For `derived` records:

1. Generate a 32-byte seed using the platform cryptographic random source.
2. Compute `PRK = HMAC-SHA-256(key = UTF8("id.walt.wallet.identity/recovery/v1"), message = seed)`.
3. For `attempt` from 0 through 255, compute:
   `candidate = HMAC-SHA-256(PRK, UTF8("P-256/signing/" + domain + "/" + index + "/" + attempt) || 0x01)`.
4. Interpret all 32 candidate bytes as an unsigned big-endian integer `d`. Accept exactly when
   `1 <= d < n`, where P-256's subgroup order is
   `FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551`.
   Otherwise try the next counter. Exhausting the bound is an error, never a modulo reduction.
5. Compute the public point `Q = dG`. Emit a private JWK with `kty=EC`, `crv=P-256`, and exactly
   32-byte unsigned big-endian `x`, `y` and `d`, each encoded with unpadded base64url.

This is HKDF-SHA-256 extract followed by a single 32-byte expand block for each explicitly separated
retry context. Decimal integers use ASCII digits without leading zeroes; zero is `0`. `domain` is
1–128 printable non-space ASCII characters. `index` is an integer from 0 through 2^31−1. Production
creation uses the newly generated identity UUID and index zero. The format retains the domain/index
rather than relying on a wallet database name or device identifier.

## Independent fixture

This seed is public test material and must never protect a real wallet:

| Input/output | Value |
| --- | --- |
| Seed bytes | `00 01 02 ... 1e 1f` |
| Domain | `wal-749-vector-1` |
| Index / accepted attempt | `0` / `0` |
| JWK `d` | `uqDwic1tOTnxFp6amne7WMZU8-6SqRMQbn95fClcK3g` |
| JWK `x` | `Wy87Jza-MAxBSOvUNi73uIaWmnTDrZ5wSTf_PqIaNYc` |
| JWK `y` | `emY2LnzjX8MeSJxPM1mP9c924_V6drBDVB9BCxDYKb8` |

The fixture was computed independently with Python HMAC and OpenSSL P-256 public-point derivation.
The shared Kotlin test checks both coordinates/scalar and domain/index separation. The fixed domain
is a test label, not the production derivation domain.

## Validation and lifecycle

Before offering or executing restoration, the SDK bounds and decodes the record, checks its format
and version, matches `identityId` to the selected provider record ID, validates the P-256 private/public
pair, rejects private JWK members in public metadata and resolves the exact DID to the expected key.
Identity IDs are 1–128 characters, logical key IDs 1–256 and DID strings 1–2048. Provider adapters impose
their own record-ID namespace rules. Unsupported fields/versions and malformed input are rejected.

The executable option retains a SHA-256 fingerprint of the validated bytes. The record is fetched
again before import; a changed fingerprint invalidates the option. The destination is rechecked
against current host policy and device capabilities. Native import must preserve the original public
key and imported origin. A fresh signature is independently verified against that public key before
activation. Native generation attestation cannot be replayed or manufactured from a recovery record.

Backup submission is followed by retrieval and byte comparison. This establishes that the provider
can return the submitted record, not that a remote replica or replacement device has received it.
Retries use the same record ID. Changing providers never automatically deletes the prior copy.

Transient byte arrays are cleared where feasible. Immutable strings, library big integers, native
bridges and managed runtimes prevent a guarantee of complete zeroization. Public identity values and
ordinary app options contain no secret bytes; trusted provider implementations necessarily receive them.
