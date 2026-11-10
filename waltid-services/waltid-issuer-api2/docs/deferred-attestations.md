# Attestation status during deferred issuance

Issuer2 rechecks the WIA accepted during OAuth client authentication and each KA accepted
with the credential proofs. Verified status references, attester public keys, capture time,
version and policy are retained with the original accepted evidence. Refresh, resubmission,
checkpoint recovery and restart preserve that evidence. Original nonce, PoP and proof-age
checks are not replayed. F5 lifetime checks and their original clock tolerance remain in force.

## Configuration

Both `keyAttestationConfig.statusPolicy` and the OAuth client-attestation method's
`config.statusPolicy` accept:

```json
{
  "requireStatusReference": false,
  "maximumListAgeSeconds": 300
}
```

The policy is independent of requiring WIA or KA. Supplied status references are checked
with either policy setting. `requireStatusReference: true` also rejects an omitted
reference. Existing profiles that permit omission retain that behavior. The maximum age
must be positive and is measured from the signed status-list `iat`.

Supported attestations carry a JWT Token Status List reference:

```json
{"status":{"status_list":{"idx":42,"uri":"https://provider.example/statuslists/1"}}}
```

Unsupported or malformed supplied mechanisms are rejected at acceptance. The signed URI
must contain an explicit HTTP(S) scheme and authority; relative references are rejected
before URL normalization. The response
must be a compact signed JWT with media type `application/statuslist+jwt`, JWT type
`statuslist+jwt`, required `sub`, `iat`, and `status_list` claims. Subject must exactly
match the accepted URI. Supported bit widths are 1, 2, 4 and 8; the referenced index must
be in bounds. Signed dates and optional positive numeric TTL are validated.

## Signer trust

Version 1 authorizes the same public key that verified the original attestation. Static
JWK, key-reference and x509 verification capture that key after successful verification.
The wallet confirmation/holder key, an `iss` string, an arbitrary response JWK or a response
certificate does not authorize a status signer. A different delegated signer or signing-key
rotation is not supported by this version; it fails closed. Public signer context stays in
private token/session evidence and is never returned in management responses.

The crypto2 verifier does not require public-key export when an optional status reference
is omitted. If a reference is supplied but the verified attester's public key cannot be
exported, status verification returns a retryable technical failure; it does not report an
invalid attestation signature. A missing required reference remains an authentication error.

Embedding applications supply `AttestationStatusResolver`. Both issuer editions use the
shared HTTP adapter with their existing private-network guard, disabled redirects, a 2 MiB
HTTP response limit and a ten-second lookup timeout. Signed list decompression is bounded
at 16 MiB by the status-list library on every supported platform. Production status transport uses HTTPS;
loopback HTTP supports local development and still requires the deployment network guard's
loopback opt-in. Public status requests carry no administrative bearer token.

## Freshness and delivery checkpoints

Each checkpoint fetches the signed lists afresh. No acceptance-time VALID decision or
previous checkpoint's list authorizes later delivery, even if its signed TTL remains valid.
Within one checkpoint, HTTP requests are deduplicated by URI, while every index and each
accepted attester's signer authorization are checked. The signed `exp` and `nbf`, when
present, must permit current use. `iat` must not be in the future or older than the accepted
`maximumListAgeSeconds`. `exp` and `ttl` may be omitted; the finite local age bound still
applies. TTL is a cache limit rather than an independent token expiry. This adapter keeps
no status-list cache between checkpoints. Within a checkpoint, each deduplicated list's
resolution time is retained. After all lookups and signature checks finish, every list is
checked again against signed expiry, local maximum age, validity start and resolution time
plus TTL. If any bound no longer permits use, the checkpoint returns a retryable technical
failure and the next claim fetches new lists.

Checks run at acceptance, before signing, after signing, before response preparation and
after encryption. Cached responses undergo the same delivery checks after recovery or
restart. Validity is determined at those checkpoints; a remote provider may publish a later
change after a check completes. Network I/O runs outside the storage CAS retry loop, and
the persisted claim fence and lifetimes are checked again after resolution.

## Failure and retry behavior

| Outcome | Deferred behavior |
|---|---|
| Every applicable entry VALID (`0x00`) | Continue remaining lifetime and authorization checks |
| Confirmed INVALID (`0x01`) | Persist whole-transaction denial; HTTP 400 `credential_request_denied` |
| SUSPENDED (`0x02`) | HTTP 503 `temporarily_unavailable`, `Retry-After: 1`; retry the same transaction |
| Lookup failure, bad signature, unauthorized signer, stale/malformed list, unknown status or out-of-range index | HTTP 503 `temporarily_unavailable`, `Retry-After: 1`; no permanent revocation decision |
| Required legacy evidence missing | Persist denial; HTTP 400 `credential_request_denied` |

A confirmed INVALID entry takes precedence over another entry's unavailable lookup.
Suspension and technical failure do not consume or deny the transaction. Processing claims
and uncommitted usage reservations are released; a retry reacquires reservations as needed,
including for a previously signed checkpoint. Coroutine cancellation propagates, with claim
cleanup performed outside cancellation. Cancellation after a durable signing checkpoint
preserves its reservation for F5 recovery. The offer's absolute deadline still limits retries.

An INVALID WIA during OAuth client authentication is a client-authentication failure. Malformed or missing required
KA status evidence is a proof-validation failure. Initial status lookup failures are technical
failures; they create no accepted transaction or successful issuance.

At credential-request acceptance, a confirmed INVALID WIA or KA emits the existing
format-specific credential-request failure notification with `credential_request_denied`,
the request ID and an error description; no transaction is created. After acceptance, the
first permanent status denial emits that failure notification once for the transaction,
including when a signed checkpoint is recovered. Later polls return the persisted denial
without emitting it again. A suspended or unverifiable status emits no permanent-denial or
credential-success notification; a successful retry emits the normal success notification.
These notifications use the offer's existing webhook configuration and event payloads.

One invalid required attestation denies every credential in that transaction. Failed
validation delivers no credentials and commits no new successful issuance result or usage.
Previously durably completed datasets retain their results and accounting, including when a
crash occurred after finalization and before response consumption. A later INVALID status
still prevents delivery of their cached credentials; previously committed accounting is not
rolled back. Signing checkpoints and status-entry allocations are private recovery progress,
not successful issuance or wallet storage.

## Legacy transactions

A captured optional absence is represented by a versioned evidence object with no reference.
A null status-evidence object means the verifier predates status capture. This also applies
to an older access token used to create a new transaction after upgrading.

Legacy evidence fails closed when the current applicable profile requires a status
reference. Legacy optional-status profiles are explicitly grandfathered under F5's existing
lifetime enforcement; their original status cannot be reconstructed. They retain the null
legacy marker, rather than being rewritten as a known optional omission. Newly captured
transactions retain their accepted policy through configuration changes, refresh and
resubmission. Unknown evidence versions fail closed.

Auto-revoke scheduling, Wallet Provider minting, fresh-attestation replacement and
TrustDecision changes remain separate work.

References: [OpenID4VCI KA](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-D.1),
[OpenID4VCI WIA](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-E),
[Token Status List draft 21](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-status-list-21#section-8.3).
