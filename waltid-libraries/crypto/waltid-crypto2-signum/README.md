# waltid-crypto2-signum

Mobile managed-key provider for Android KeyStore and iOS Keychain/Secure Enclave through Signum. Persisted records contain aliases, immutable policy, public keys, and optional attestation evidence, never private key material.

`generate` and `restore` return `ManagedKey`, so inferred kotlinx.serialization uses the provider-neutral managed-key
descriptor. Use `generateSignumKey` or `restoreSignumKey` when Signum-specific protection and attestation properties are
needed directly.
