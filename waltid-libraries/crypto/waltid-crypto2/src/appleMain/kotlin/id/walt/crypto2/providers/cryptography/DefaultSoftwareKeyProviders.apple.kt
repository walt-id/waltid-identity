package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.providers.SoftwareKeyProvider

// No secp256k1: neither the platform provider nor a maintained add-on offers it here.
actual fun defaultSoftwareKeyProviders(): List<SoftwareKeyProvider> = listOf(CryptographySoftwareKeyProvider())
