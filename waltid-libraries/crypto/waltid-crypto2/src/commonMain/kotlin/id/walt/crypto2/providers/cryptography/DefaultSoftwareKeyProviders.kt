package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.providers.SoftwareKeyProvider

/**
 * The software-key providers that are worth registering by default on this platform.
 *
 * Always contains the portable [CryptographySoftwareKeyProvider]. Platforms whose cryptography-kotlin provider
 * cannot do secp256k1 - which the portable profile therefore excludes - additionally contribute a dedicated
 * secp256k1 provider where one exists (Bouncy Castle on the JVM, OpenSSL 3 on Linux and Windows). Registering
 * several providers is what [id.walt.crypto2.CryptoRuntime] expects: it resolves each request to a provider that
 * supports it.
 */
expect fun defaultSoftwareKeyProviders(): List<SoftwareKeyProvider>
