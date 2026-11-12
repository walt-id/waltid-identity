# Credential status

Shared, authenticated credential-status evaluation for wallet observations and verifier policies.
`waltid-verification-policies2` re-exports the existing status helper packages from this module;
policy acceptance remains in the policy module. Wallet apps consume the composed status APIs in
`waltid-openid4vc-wallet` or the mobile SDK rather than interpreting status bits themselves.

## Supported profiles

| Reference | Supported list/proof | Meaning |
| --- | --- | --- |
| Token Status List | Signed JWT or CWT, draft-ietf-oauth-status-list-21 | 0 clear, 1 invalid, 2 suspended; other values application-specific |
| ISO MSO `status_list` | Signed CWT, protected certificate chain, one-bit profile | Clear or revoked; authenticated MSO trust point or issuing CA authorizes the signer |
| W3C Bitstring Status List | Signed JWT status-list credential | Purpose-aware revocation, suspension, refresh or application-specific value |
| Legacy StatusList2021 | Signed JWT status-list credential | Purpose-aware value |

The W3C wallet profile requires at least 131,072 entries, so its minimum expanded size scales with
`statusSize`. A supplied list credential ID must match the referenced URL; the ID may be omitted.
Signed one-or-more purposes are matched exactly, and declared list/entry sizes must agree.
Other methods and unsupported proof forms are explicit results. They are never treated as clear.
Standards references: [Token Status List draft 21](https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-21.html)
and [Bitstring Status List v1.0](https://www.w3.org/TR/2025/REC-vc-bitstring-status-list-20250515/).
This is a profile list, not a claim of full ISO, EUDI or W3C proof-suite conformance.

## Observation and acceptance

`CredentialStatusResolver.discover` reads references locally. `resolve` first authenticates the
credential containing the reference, then authenticates and authorizes the list signer, validates
applicability and validity, and reads the applicable value. A byte-cache hit still requires the
current credential's signer authorization. Creating a resolver or reading metadata makes no request.

`CredentialStatusMetadata` retains each reference, including unsupported and failed entries.
`Unavailable` can retain a dated previous authenticated observation. Empty entries mean that no
status was provided. `metadata.presentation(at)` supplies shared kind, issue, freshness, coverage,
action and semantic `advisoryRevision`; user interfaces should use these meanings instead of bits.
A clear observation is not proof that the credential is unexpired, trusted or acceptable to a verifier.

## Network, cache and lifecycle

Use one resolver per wallet/trust configuration. The caller owns its coroutine scope and a dedicated,
unauthenticated HTTP client. The resolver derives a client with redirect and outgoing credential
guards; closing the resolver closes that derived client, not the caller's client. Caller-supplied
clients must still enforce private-network/egress policy and disable external body caches and logging.
The wallet service's default client uses the shared `WebDataFetcher` private-network guard.

Default limits are 10 seconds per list request, 30 seconds per credential resolution, four concurrent
resolutions/requests, 2 MiB per response, 8 MiB expanded data, 16 MiB/64 lists in each bounded cache,
and 64 references per credential. `CredentialStatusLimits` permits bounded host overrides.
HTTPS endpoints cannot contain user information or fragments. Cancellation releases only the
caller's ownership of coalesced work; the last owner cancels it.

Freshness is capped by signed validity, list TTL, HTTP freshness and the local maximum (one hour).
The local fallback is 15 minutes. W3C TTL is milliseconds; Token Status List TTL is seconds.
Force requires network revalidation and supports conditional requests. A 304 cannot extend signed
validity; a 304 without a reusable body causes an unconditional retry. `no-cache` requires
revalidation; `no-store` forbids body reuse after the active batch and persisted observations from
that response. Rate limiting and resource limits return typed failures.

An explicit `CredentialStatusRefreshSession` can share authenticated response bytes within one
bounded refresh batch, including immediate-expiry/no-store responses. Close it in `finally` after
admitting that batch. It rejects later resolutions, expires after the resolution timeout, bounds
total retained bytes and list count, and releases resources after admitted work leaves.
It is not a reusable application cache or a scheduler.

## Wallet integration

The [core wallet](../../protocols/waltid-openid4vc-wallet/README.md) owns snapshots, persistence,
per-record coalescing and explicit IfDue/Force refresh. Its snapshots expose an optional
`nextRefreshAt` under current freshness/backoff, so hosts can schedule due work while active. The [mobile facade](../../protocols/waltid-openid4vc-wallet-mobile/README.md)
adds Kotlin Flow and the [Swift SDK](../../protocols/waltid-wallet-sdk-ios/README.md) adds a bounded
native stream. Hosts own foreground/manual triggers, preferences and any future background scheduling.

Managed stores persist only minimal last-known observations under the credential fingerprint and
trust-policy identity. They do not persist list bodies or history. Custom stores may implement
`CredentialStatusSnapshotStore` for the same conditional-write contract; otherwise snapshots are
instance-local. Missing or changed trust-policy identity restores historical evidence as stale.

Reference and signer authorization hooks are advanced host policy. A stable `trustPolicyId` must
change whenever that policy changes. Attach a configured `WalletCredentialStatusService` to the core
wallet before its first status access. The default mobile policy has no stable persisted identity,
so restored observations are dated historical evidence until explicitly refreshed.
