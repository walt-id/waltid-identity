# waltid-trust-registry

Kotlin/JVM library for loading trust lists and resolving certificates, certificate chains, and provider identifiers against a normalized trust model.

Supported inputs:

- ETSI TS 119 612 Trust List and List of Trusted Lists XML
- ETSI TS 119 602 V1.1.1 JSON, validated against the ETSI Annex A.1 schema
- ETSI TS 119 602 V1.1.1 XML, validated against the ETSI Annex A.2.1 XSD
- TS 119 602 JSON in a compact-JWS envelope

Other JSON/XML shapes are rejected.

## Standards status

| Area | Status |
|---|---|
| TS 119 602 Annex A.1 JSON syntax | ETSI JSON Schema validation and normalization implemented |
| TS 119 602 Annex A.2.1 XML syntax | Offline ETSI XSD validation and normalization implemented |
| TS 119 602 Annex A.2.2 / TS 119 612 XML | Ingestion implemented |
| RFC 7515 compact JWS integrity | Implemented for embedded ECDSA payloads |
| TS 119 602 profile rules, annexes D–I | Conformance test expansion required |
| JAdES Baseline B / XAdES-B-B profile validation | Not complete; cryptographic signature validation alone is not a conformance claim |

The library must not be described as fully ETSI-conformant until the remaining profile rules and AdES validation are
implemented and verified against an independent conformance suite.

## Security model

Loading a document and trusting its contents are separate operations. A source is stored only when it satisfies its configured `SourceAcceptancePolicy`; resolution also verifies that the source was admitted.

| Policy | Accepted sources |
|---|---|
| `REQUIRE_AUTHENTICATED` | Valid signature from an independently trusted signer |
| `REQUIRE_VALID_SIGNATURE` | Valid signature; signer authorization may be unevaluated |
| `ALLOW_UNSIGNED` | Signed sources are verified; unsigned sources are explicitly allowed |
| `ALLOW_UNVERIFIED` | Verification is explicitly disabled |

`SourceLoadOptions()` defaults to `REQUIRE_AUTHENTICATED`. Invalid signatures are never activated by the first three policies.

The resulting `SourceAssurance` records:

- signature status: absent, unchecked, valid, invalid, or unsupported;
- signer trust: trusted, untrusted, unevaluated, or not applicable;
- the resulting authenticity state and whether the source was admitted.

`AUTHENTICATED` means both signature integrity and signer authorization were established. `INTEGRITY_VERIFIED` does not claim that the signer is trusted.

## Dependency

```kotlin
repositories {
    mavenLocal()
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.credentials:waltid-trust-registry:<version>")
}
```

To test an unpublished local build:

```bash
./gradlew \
  :waltid-libraries:credentials:waltid-trust-registry:publishToMavenLocal
```

Use the local project version, currently `1.0.0-SNAPSHOT`, in the consuming project.

## Usage

Create an in-memory registry:

```kotlin
val registry = DefaultTrustRegistryService(InMemoryTrustStore())
```

### Authenticated compact-JWS LoTE

The signer certificate is configured independently; the certificate embedded in `x5c` is not trusted by itself.

```kotlin
val result = registry.loadSourceFromContent(
    sourceId = "wallet-providers",
    content = signedLoteJws,
    options = SourceLoadOptions(
        acceptancePolicy = SourceAcceptancePolicy.REQUIRE_AUTHENTICATED,
        trustedSignerCertificates = listOf(trustedSignerCertificatePem)
    )
)

check(result.success) { result.error }
check(result.assurance?.authenticityState == AuthenticityState.AUTHENTICATED)
```

### TSL XML signature integrity

This validates XMLDSig integrity but does not authorize the embedded signer certificate:

```kotlin
val result = registry.loadSourceFromUrl(
    sourceId = "at-tsl",
    url = "https://www.signatur.rtr.at/vertrauensliste.xml",
    options = SourceLoadOptions(
        acceptancePolicy = SourceAcceptancePolicy.REQUIRE_VALID_SIGNATURE
    )
)

check(result.success) { result.error }
check(result.assurance?.authenticityState == AuthenticityState.INTEGRITY_VERIFIED)
```

For `AUTHENTICATED`, use `REQUIRE_AUTHENTICATED` and configure independently trusted TSL signer certificates.

The parser distinguishes a national trust list from the EU List of Trusted Lists (LoTL):

```kotlin
check(result.format == TrustListFormat.ETSI_TS_119_612_TRUST_LIST_XML)
```

For the EU LoTL, `result.format` is `ETSI_TS_119_612_LIST_OF_TRUST_LISTS_XML` and
`result.pointersLoaded` reports the number of `OtherTSLPointer` entries. The library does not automatically fetch the
member-state lists referenced by those pointers; register and load each required national list explicitly.

### Explicitly allow an unsigned list

Unsigned LoTE inputs require an explicit policy:

```kotlin
val result = registry.loadSourceFromContent(
    sourceId = "wallet-providers",
    content = loteJson,
    options = SourceLoadOptions(
        acceptancePolicy = SourceAcceptancePolicy.ALLOW_UNSIGNED
    )
)

check(result.success) { result.error }
check(result.assurance?.signatureStatus == SignatureStatus.NOT_PRESENT)
```

### Resolve trust

```kotlin
val decision = registry.resolveCertificateChain(
    certificateChainPemOrDer = presentedLeafFirstChain,
    instant = Clock.System.now(),
    expectedEntityType = TrustedEntityType.PID_PROVIDER
)

when (decision.decision) {
    TrustDecisionCode.TRUSTED -> println(decision.matchedEntity?.legalName)
    else -> println(decision.evidence)
}
```

Certificate-chain resolution can build a path from a presented leaf to a registry-owned trust anchor; the presented chain does not need to contain that anchor.

## Source lifecycle

```text
fetch → detect format → verify envelope/signature → parse
      → apply acceptance policy → atomically activate → resolve
```

Failed refreshes do not replace the active source snapshot. `RefreshResult` provides an `errorCode`, details, and source assurance where available.

## Compatibility and scope

- Boolean `validateSignature` loading methods remain temporarily available but are deprecated. New integrations should use `SourceLoadOptions`.
- Persisted sources created before the assurance model must be refreshed or migrated; missing assurance is treated as not admitted.
- Compact JWS support validates embedded payloads and pinned X.509 signers; it is not full JAdES support.
- `InMemoryTrustStore` is thread-safe but not persistent. Production deployments should provide a persistent `TrustStore` implementation.

Useful public test sources:

- Austria TSL: `https://www.signatur.rtr.at/vertrauensliste.xml`
- Italy TSL: `https://eidas.agid.gov.it/TL/TSL-IT.xml`
- EU LoTL: `https://ec.europa.eu/tools/lotl/eu-lotl.xml`

## Tests

```bash
./gradlew \
  :waltid-libraries:credentials:waltid-trust-registry:jvmTest
```

Network tests are disabled by default. Enable them explicitly with `RUN_NETWORK_TESTS=true`.

Normative references:

- ETSI TS 119 602 V1.1.1: <https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/01.01.01_60/ts_119602v010101p.pdf>
- ETSI schemas, pinned in the implementation to commit `e84f427f0cde99513b574ef4b5a155ac4a38eab6`:
  <https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities>
- ETSI TS 119 612 V2.4.1: <https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/02.04.01_60/ts_119612v020401p.pdf>
