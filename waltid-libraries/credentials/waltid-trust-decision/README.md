# walt.id Trust Decision

A small port for one question: is this subject trusted for this purpose? Issuers, wallets and verifiers ask it through `TrustDecisionEvaluator`. The adapter behind it can be static anchors, a remote registry or the Enterprise trust registry. Protocol code depends only on this module, not on a registry client.

The module also holds the trust vocabulary (`id.walt.trust.model`: `TrustDecision`, `TrustedEntityType`, `FreshnessState`, ...). `waltid-trust-registry` re-exports it.

## Asking

```kotlin
val request = TrustDecisionRequest(
    purpose = TrustPurpose.ISSUER_WUA_SIGNER,
    subject = TrustSubject.CertificateChain(x5c), // leaf first, PEM or Base64 DER
    expectedEntityTypes = setOf(TrustedEntityType.WALLET_PROVIDER),
)

when (val check = evaluator.check(request, TrustRequirement.RegistryFresh())) {
    is TrustCheck.Accepted -> proceed()
    TrustCheck.Skipped -> proceed()
    is TrustCheck.Rejected -> reject(check.reason)
}
```

`check` fails closed: a missing evaluator or a decision that misses the requirement is `Rejected`. Exceptions from the evaluator, including `CancellationException`, propagate and abort the operation.

Take the expected entity types and the requirement from your own configuration or profile. Never take them from the counterparty's message.

## Requirements

| Requirement | Accepts | Satisfied by |
|---|---|---|
| `NotRequired` | everything; the evaluator is not called | n/a |
| `Trusted` | `TRUSTED`, source authentication not `FAILED`, freshness not `EXPIRED` | static anchors, registries |
| `RegistryFresh(requireAuthenticated)` | `TRUSTED` with a matched entity of an expected type, from an accepted `FRESH` source (and `AUTHENTICATED` if required) | registries only |

## Adapters

| Adapter | Use |
|---|---|
| `StaticTrustDecisionEvaluator(anchors per purpose)` | PKIX validation against configured anchors. Freshness `UNKNOWN`. A purpose without anchors is `UNSUPPORTED_SOURCE`. |
| `HttpTrustDecisionEvaluator(resolveUrl, headers)` | Remote registry over the `resolve/certificate-chain` contract (OSS route, Enterprise route with a bearer token, or a compatible resolver). Unavailable is `UNKNOWN`. |
| `InsecureAllowAllTrustDecisionEvaluator` | Development only. Trusts everything with `UNKNOWN` freshness, so `RegistryFresh` still rejects. |
| `TrustDecisionEvaluator { ... }` | Lambda for tests and fixtures. |
| `TrustRegistryTrustDecisionEvaluator` (Enterprise) | The tenant's linked trust-registry service. |

When a registry answers per entity type, use `anyOfEntityTypes` to ask once per expected type and keep the best answer.

Never chain adapters so that a denial from one falls through to a more permissive one.

## Remote resolvers and the network guard

`HttpTrustDecisionEvaluator` uses `WebDataFetcher` id `TRUST_DECISION_RESOLVE`, which blocks loopback and private addresses by default. To use a resolver on a private network, allow it through `PrivateNetworkGuardSettings` (in Enterprise, `network-fetch-guard.conf`).
