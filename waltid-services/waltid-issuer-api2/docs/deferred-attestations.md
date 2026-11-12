# Attestation status during deferred issuance

Issuer2 rechecks the WIA accepted during OAuth client authentication and each KA accepted
with the credential proofs. Verified status references, attester public keys, capture time
and policy are retained with the original accepted evidence. Refresh, resubmission,
checkpoint recovery and restart preserve that evidence. Original nonce, PoP and proof-age
checks are not replayed. F5 lifetime checks and their original clock tolerance remain in force.

## Configuration

Both `keyAttestationConfig.statusPolicy` and the OAuth client-attestation method's
`config.statusPolicy` accept:

```json
{
  "requireStatusReference": false,
  "maximumListAgeSeconds": null
}
```

The policy is independent of requiring WIA or KA. Supplied status references are checked
with either policy setting. `requireStatusReference: true` also rejects an omitted
reference. Existing profiles that permit omission retain that behavior.

`maximumListAgeSeconds` is optional and defaults to `null`. Omitting it or supplying
`null` adds no age limit measured from the signed status-list `iat`. An issuer operator
can configure a positive number, such as `300`, when its attestation profile requires a
stricter freshness bound. Choose that value to match the status provider's republication
guarantees; fetching an unchanged signed list does not renew its `iat`. The wallet cannot
choose or weaken this policy in a Credential Request or deferred poll.

Signature, signer authorization, signed validity, TTL and status checks always run for
supplied references, including when no additional age limit is configured. Attestation
expiry checks also remain automatic and independent of this policy.

Supported attestations carry a JWT Token Status List reference:

```json
{"status":{"status_list":{"idx":42,"uri":"https://provider.example/statuslists/1"}}}
```

Unsupported or malformed supplied mechanisms are rejected at acceptance. The signed URI
must contain an explicit HTTP(S) scheme and authority; relative references are rejected
before URL normalization. The response
must be a compact signed JWT with media type `application/statuslist+jwt`, JWT type
`statuslist+jwt` (also `application/statuslist+jwt`, case-insensitively), and required
`sub`, `iat`, and `status_list` claims. Subject must exactly
match the accepted URI. Supported bit widths are 1, 2, 4 and 8; the referenced index must
be in bounds. Signed dates and optional positive numeric TTL are validated.

## Signer trust

The supported trust profile authorizes the same public key that verified the original
attestation. Static JWK, key-reference and x509 verification capture that key after
successful verification.
KA verification retains the public material used for the successful signature check; it
does not retrieve the key again from a provider for status capture.
The wallet confirmation/holder key, an `iss` string, an arbitrary response JWK or a response
certificate does not authorize a status signer. A different delegated signer or signing-key
rotation is not supported by this version; it fails closed. Public signer context stays in
private token/session evidence and is never returned in management responses.

The WIA verifiers do not require public-key export when an optional status reference
is omitted. If a reference is supplied but the verified attester's public key cannot be
exported, status verification returns a retryable technical failure; it does not report an
invalid attestation signature. A missing required reference remains an authentication error.

Embedding applications supply `AttestationStatusResolver`. Both issuer editions provide
`HttpAttestationStatusResolver` with a service-owned `WebDataFetcher` using the dedicated
`OPENID4VCI_ATTESTATION_STATUS` identifier. This reuses the HTTP engine, URL restrictions,
private-network guard and response limits. The adapter uses uncached raw fetching,
`Accept: application/statuslist+jwt` and `Cache-Control: no-cache`; it clears configured
request headers, cookies and authentication and always uses GET. URL credentials are
rejected. A fetcher's result cache never authorizes a later checkpoint.

The default HTTP response cap is 2 MiB and the total lookup timeout is ten seconds,
including any configured retries. `WebDataFetcherManager` configuration for this ID can
apply a stricter response cap, timeouts and URL allowlists. Configurations that disable or
increase the cap above 2 MiB, or enable redirects, are rejected when the adapter is created.
In the shared `httpclient` configuration, place per-ID settings under `service`:

```json
{
  "service": {
    "OPENID4VCI_ATTESTATION_STATUS": {
      "maxResponseBytes": 1048576,
      "engine": { "followRedirects": false },
      "timeouts": { "requestTimeout": "PT5S" },
      "url": { "hosts": { "whitelist": ["provider.example"] } }
    }
  }
}
```

Production status transport requires HTTPS. Loopback HTTP supports local development and
still requires the deployment network guard's loopback opt-in. An alternative implementation
of `AttestationStatusResolver` can provide another transport while retaining shared protocol
enforcement. It must return freshly resolved signed JWTs, preserve cancellation and never
substitute a cached VALID decision.

Generic token claims, validity and entry decoding live in `waltid-status-list` common code;
bounded decompression is limited to 16 MiB on every supported platform. `crypto2` verifies
the original compact JWT against the accepted attester key in `waltid-openid4vci` before
those claims are used. OpenID4VCI retains signer authorization, original accepted evidence,
policy, delivery checkpoints and outcome mapping. The generic status-list API provides no
signature verification or trust discovery. Attestation profile and OpenAPI request shapes
are unchanged by this library refactor.

## Freshness and delivery checkpoints

Each checkpoint fetches the signed lists afresh. No acceptance-time VALID decision or
previous checkpoint's list authorizes later delivery, even if its signed TTL remains valid.
Within one checkpoint, HTTP requests are deduplicated by URI, while every index and each
accepted attester's signer authorization are checked. The status-list profile permits
`iat` and `nbf` up to 60 seconds ahead of the issuer clock, matching the existing
attestation verifiers' tolerance. This allowance does not extend signed `exp`, retrieval
TTL or a configured maximum list age. When the accepted
`maximumListAgeSeconds` is non-null, the list must also be no older than that bound;
omission or `null` imposes no additional age cap. `exp` and `ttl` may be omitted under
the supported profile. TTL is a cache limit rather than an independent token expiry.
This adapter keeps no status-list cache between checkpoints. Within a checkpoint, each deduplicated list's
resolution time is retained. After all lookups and signature checks finish, every list is
checked again against signed expiry, any configured maximum age, validity start and
resolution time plus TTL. If any bound no longer permits use, the checkpoint returns a
retryable technical failure and the next claim fetches new lists.

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
| SUSPENDED (`0x02`) | HTTP 202 with the same `transaction_id` and a positive `interval`; no credentials |
| Lookup failure, bad signature, unauthorized signer, stale/malformed list, unknown status or out-of-range index | HTTP 202 with the same `transaction_id` and `interval`; no credentials or permanent revocation decision |
| Required status reference missing | Persist denial; HTTP 400 `credential_request_denied` |

A confirmed INVALID entry takes precedence over another entry's unavailable lookup.
For a retryable status outcome on an accepted transaction, the wallet waits at least the
returned `interval` before polling again. The pending response uses the requested response
encryption when configured. The provider exposes retryability to the embedding service
separately from the HTTP status; it is not an extra field in the wire response.
Suspension and technical failure do not consume or deny the transaction. Processing claims
and uncommitted usage reservations are released; a retry reacquires reservations as needed,
including for a previously signed checkpoint. Coroutine cancellation propagates, with claim
cleanup performed outside cancellation. Cancellation after a durable signing checkpoint
preserves its reservation for F5 recovery. The offer's absolute deadline still limits retries.

An INVALID WIA during OAuth client authentication is a client-authentication failure. Malformed
or missing required KA status evidence is a proof-validation failure. For a deferred flow,
the initial Credential Request must pass every proof signature, nonce, lifetime and key-binding
check before its evidence is captured. The provider then checks status once for the complete
batch, deduplicating shared list URIs. A confirmed INVALID attestation prevents acceptance;
suspended or unverifiable status may remain pending. If delivery is still blocked, the service
persists the transaction and returns HTTP 202 with its new `transaction_id` and a positive
`interval`, even when the offer was already marked ready. Every later delivery rechecks the
original status references and lifetimes. Direct proof-verifier callers retain status checking.

Without a deferred flow, suspended or unverifiable status encountered during credential-proof
verification returns HTTP 400 `credential_request_denied`; no transaction is created. Attestation-status credential errors do not use
`temporarily_unavailable`, HTTP 503 or a `Retry-After` header. At the token endpoint, suspended
or unverifiable WIA status returns HTTP 400 `temporarily_unavailable` without issuing tokens.
The client may retry the same unexpired grant with a fresh client-attestation proof of possession
after status becomes usable. This token-specific mapping does not change PAR error handling.

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

## Captured evidence

Every verified attestation records its status policy, including when the profile permits
omitting the status reference. A captured evidence object with no reference represents that
known optional omission. A missing evidence object is an internal construction error and
blocks delivery; it is never treated as optional omission or recorded as confirmed revocation.

Deferred transactions use their original captured policy through configuration changes,
refresh, resubmission and restart. Status checks do not reconstruct that policy from current
issuer configuration. An omitted `maximumListAgeSeconds` adds no age cap; an explicit cap
remains attached to the transaction. Signed validity and freshness checks still apply.

This unreleased feature provides no migration for snapshots or tokens from earlier
development versions. Restart affected in-flight flows when changing development versions.

Auto-revoke scheduling, Wallet Provider minting, fresh-attestation replacement and
TrustDecision changes remain separate work.

References: [OpenID4VCI KA](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-D.1),
[OpenID4VCI WIA](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-E),
[Token Status List draft 21](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-status-list-21#section-8.3).
