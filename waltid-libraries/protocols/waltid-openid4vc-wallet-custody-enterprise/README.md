# Enterprise identity key custody

Optional Kotlin Multiplatform adapter for an existing Enterprise KMS. The base mobile SDK does not depend on it.

```kotlin
val custodian = EnterpriseIdentityKeyCustodian(
    httpClient = authenticatedClient,
    kmsResourceUrl = Url("https://enterprise.example/v1/org.kms"),
)
val identity = IdentityConfiguration(keyCustodians = listOf(custodian))
```

After creating the wallet with this identity configuration, select an option from
`wallet.identities.custodyOptions(identityId)` and pass it to `transferToCustody(option)`.
The destination receives an additional private-key copy. The SDK checks the returned public key,
retains local signing, and records the destination reference separately from recovery status.

The host supplies authentication, TLS, timeouts and permissions (`ES_KMS_IMPORT_KEY_JWK`). Do not log
request or response bodies. Redirects are refused. Imports use the stable logical key ID and reject
a different existing key. Close the adapter when finished; the original HTTP client remains host-owned.

The API does not store a portable recovery record or configure remote signing. See the
[identity lifecycle guide](../waltid-openid4vc-wallet-mobile/docs/identity-recovery.md) for policy and assurance limits.
