# waltid-crypto2-kms

Multiplatform REST key providers for `waltid-crypto2`.

Provider configuration stores only credential references. Applications resolve those references to credentials at operation time and remain responsible for the injected Ktor `HttpClient` lifecycle.

Implemented providers:

- HashiCorp Vault Transit with token, AppRole, and userpass authentication
- Azure Key Vault with OAuth client credentials
- AWS KMS with SigV4 access-key and temporary-session credentials
- OCI KMS with API-key HTTP Signature authentication
- CSC API v2.2 remote signing services (for example a QTSP's qualified seal), JVM and Android only, see below

Optional JVM extensions:

- `waltid-crypto2-kms-aws-sdk` for AWS default credentials, multi-region replicas, aliases, tags, and regional failover
- `waltid-crypto2-kms-azure-identity` for Azure managed, workload, and default credentials

OCI SDK principal authentication remains in the legacy adapter until there is a concrete crypto2 consumer; the portable REST provider should be preferred otherwise.

## CSC remote signing

`CscKeyProvider` signs through a remote signing service (RSSP) that implements the
[CSC API v2.2](https://cloudsignatureconsortium.org/wp-content/uploads/2025/11/csc-api.pdf). The RSSP provisions the
credential, so `generate` is rejected. Attach an existing credential instead:

```kotlin
val provider = CscKeyProvider(httpClient, CscCredentialResolver { reference -> loadCscCredentials(reference) })
val stored = provider.storedKeyForExisting(
    id = KeyId("qeaa-seal"),
    options = CscOptions("https://rssp.example/csc/v2", CredentialReference("qtsp-seal")),
    credentialID = "credential-id-from-the-rssp",
)
val key = CryptoRuntime(emptyList(), listOf(provider)).restore(stored)
```

`CscKeyProvider` is available on JVM and Android only. It reads certificates with `waltid-x509`, which has no Linux,
macOS or Windows targets and uses Node's `crypto` module on JS. `waltid-x509` builds for iOS, but adding CSC there needs
an iOS ABI dump generated on macOS.

Attaching calls `info` and `credentials/info` and keeps the leaf certificate's public key and the certificate chain in the
descriptor (`CscKeyProvider.certificateChain`). Restoring needs no network. Each signature costs one
`credentials/authorize` and one synchronous `signatures/signHash` call, and is verified locally before it is returned.

The token endpoint defaults to `oauth2/token` below the `oauth2` URI from `info`, but only when that URI is HTTPS on the
service URL's origin, so the client secret never follows an unauthenticated answer to another host. Set
`CscOptions.tokenEndpoint` for an authorization server elsewhere. A credential the provider cannot use fails with
`KmsProviderException`; an HTTP 401 from the service drops the cached access token.

Supported: `specs` 2.2.0.0, OAuth 2.0 client credentials (`oauth2client`) with `Bearer` tokens, `explicit` credential
authorization with static `Password` objects (`CscCredentials.authData`) that `auth/expression` combines only with
`AND`, ECDSA P-256 with SHA-256. Not supported: other CSC versions, `oauth2code`, `OR` and `XOR` authorization
expressions, out-of-band or one-time-password authorization, `signDoc`, asynchronous signing, and digest-only signing.
