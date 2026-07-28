package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.providers.SoftwareKeyProvider

// Built once: the providers are stateless adapters, and constructing the secp256k1 backend registers a full JCA
// provider, which is not worth repeating per call site.
private val providers: List<SoftwareKeyProvider> by lazy {
    listOf(CryptographySoftwareKeyProvider(), BouncyCastleSecp256k1SoftwareKeyProvider())
}

internal actual fun platformSoftwareKeyProviders(): List<SoftwareKeyProvider> = providers
