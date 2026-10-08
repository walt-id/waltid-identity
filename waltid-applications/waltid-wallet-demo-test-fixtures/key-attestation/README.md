# Shared demo key attestation

[KeyAttestationProfiles.json](../../waltid-wallet-demo-shared-ios/Sources/WalletDemoSharingUI/Resources/KeyAttestationProfiles.json)
is the authoritative ITB/EUDI issuer allowlist, service endpoints, algorithms,
ITB claims and lifetimes. Swift loads it from its demo package resources;
`sources.gradle.kts` embeds the same file into generated Kotlin sources under
each consumer's build directory. Edit the JSON to update profile definitions.

The Kotlin adapter in `kotlin/` is compiled into the Compose demo, the ITB runner,
and Android SDK device tests. Native Swift demo and SDK tests use
`WalletDemoSharingUI.DemoKeyAttestationProviders`. App UI tests get the resolver
from the normal demo factory. Tests that create wallets directly attach the same
resolver explicitly; rejection tests keep their deliberately invalid providers.

These fixtures are confined to demos and integration tests. SDK main sources
have no built-in issuer allowlist or test provider. The profile contains no
signing keys or JWTs: ITB generates a fresh signing key per proof collection,
and EUDI uses its public mock service. Both provide test assurance only.
