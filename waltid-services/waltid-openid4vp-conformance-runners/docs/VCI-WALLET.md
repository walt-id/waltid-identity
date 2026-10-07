# VCI Wallet Conformance Tests

The OpenID Foundation suite acts as issuer; the adapter drives the real Wallet2
REST API. `VciWalletConformanceTests` starts a fresh in-process Wallet2 on
`127.0.0.1:7016` and an adapter on port `7007`. Do not start a separate Wallet2
for these tests. Authorization-code redirects are followed automatically.

## Profiles and batch acceptance

| Test | Format | Grant | Client authentication |
|------|--------|-------|-----------------------|
| `vciWalletSdJwtVcPreAuthorizedCode` | SD-JWT VC | Pre-authorized code | `private_key_jwt` |
| `vciWalletSdJwtVcDpopAuthorizationCode` | SD-JWT VC | Authorization code | `private_key_jwt` |
| `vciWalletIsoMdocDpopAuthorizationCode` | mdoc | Authorization code | `private_key_jwt` |
| `vciWalletSdJwtVcAuthorizationCodeHaipFullTarget` | SD-JWT VC | Authorization code | Client attestation |
| `vciWalletBatchBothGrantsAndFormats` | SD-JWT VC and mdoc | Both grants | `private_key_jwt` |

The batch gate requires suite revision `db1080a`, version `5.2.4`, and the module
`oid4vci-1_0-wallet-test-batch-credential-issuance`. Each of its eight variants
(RAR/simple across both grants and formats except mdoc pre-authorized scope,
plus HAIP SD-JWT authorization code)
uses two newly generated P-256 holder keys, separate from the registered client
key. The suite checks distinct proofs and returns credentials in reverse order.
The gate requires an executed `FINISHED` / `PASSED`, two stored bindings with
distinct key references and thumbprints, and successful separate presentations
of both credentials to in-process Verifier2. Presentation supplies only the
credential selection, without a signing-key override; default verifier policies
remain enabled.

An absent suite/module, skipped batch, failed presentation or warning fails this
gate, except the two named TLS-header warnings allowed by the gate when the suite
cannot observe TLS protocol/cipher headers behind the test proxy. Other profile tests may skip when their suite is unavailable. Full-profile
results can include unsupported modules; inspect the recorded skip reasons.
The targeted gate selects immediate, plain issuance explicitly; other HAIP modules
remain part of the separate full-profile report. This is neither a complete HAIP run
nor certification.

The pinned suite's `VCIInjectRequestScopePreAuthorizedCodeFlow` hard-codes
`eudi.pid.1`, so its mdoc pre-authorized scope variant grants SD-JWT despite
offering mdoc. That variant is excluded until the suite fixes this mismatch;
Wallet2 must reject the unrelated grant. The suite is used without patches.

## Setup and execution

Use a running pinned suite with HTTPS reachable from the test JVM. Its advertised
issuer URL must match the externally visible scheme, host and port. A TLS proxy
must forward the real connection scheme/port and TLS protocol/cipher headers;
otherwise DPoP or TLS checks can fail on fixture configuration.

The suite must also reach the adapter. For a local Docker suite on macOS,
`CONFORMANCE_ADAPTER_HOST=host.docker.internal` normally provides that route.
For a remote suite, provide a reachable HTTPS adapter URL. Keep ports 7016 and
7007 free; batch acceptance refuses to reuse an existing adapter with unknown keys.

Run from the coordinated unified-build root:

```bash
CONFORMANCE_HOST=127.0.0.1 \
CONFORMANCE_PORT=8443 \
CONFORMANCE_ADAPTER_HOST=host.docker.internal \
CONFORMANCE_EXTRA_CA_PEM=/absolute/path/to/local-suite-ca.pem \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests '*VciWalletConformanceTests.vciWalletBatchBothGrantsAndFormats' \
  -PrunIntegrationTests --no-configuration-cache
```

Use `--tests '*VciWalletConformanceTests'` to select every wallet issuance
profile. For offline harness tests, use `-PskipLiveConformance=true`; that
explicitly excludes live conformance tests and is not acceptance evidence.

| Variable | Purpose |
|----------|---------|
| `CONFORMANCE_HOST`, `CONFORMANCE_PORT` | Suite host and HTTPS port |
| `CONFORMANCE_EXTRA_CA_PEM` | Additional local CA/certificate; Gradle derives a test truststore |
| `CONFORMANCE_TRUSTSTORE_PATH`, `CONFORMANCE_TRUSTSTORE_PASSWORD` | Override the base test truststore |
| `CONFORMANCE_ADAPTER_HOST` | Host used by the suite to call the adapter |
| `CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL` | Reachable HTTPS adapter base URL for remote suites |
| `OPENID_CONFORMANCE_ALIAS_SUFFIX` | Namespace independent local runs |

## Evidence

JUnit results include the storage and presentation assertions. Suite module
results are written separately under
`build/reports/openid-conformance/vci-wallet/`, with links to each suite log.
Read both: a passed issuance module alone does not prove usable stored bindings.
Record the wallet revision, suite revision, variant and test ID with any claimed
result. A local run does not establish hosted CI acceptance.

The reusable Gradle workflow runs wallet conformance and publishes its reports;
see [CI summaries](../README.md#ci-summaries). The full-profile adapter defaults
to one holder; the explicit batch gate supplies two. Issuer-role batch results
are independent evidence and do not replace this wallet gate.
