package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.providers.SoftwareKeyProvider

/**
 * The software-key providers worth registering by default on this platform.
 *
 * Always contains the portable [CryptographySoftwareKeyProvider]. Platforms whose cryptography-kotlin provider
 * cannot do secp256k1 - which the portable profile therefore excludes - additionally contribute a dedicated
 * secp256k1 provider where one exists (Bouncy Castle on the JVM, OpenSSL 3 on Linux and Windows).
 *
 * Deliberately a common function delegating to an internal expect: a top-level `expect fun` compiles to a
 * per-target file class (`DefaultSoftwareKeyProviders_jvmKt`, `..._androidKt`, ...), so a caller compiled against
 * the JVM variant but running on Android dies with NoClassDefFoundError - which is exactly what broke the Android
 * device tests, because modules without an Android target (waltid-mdoc-credentials2) are consumed there as their
 * JVM artifact. Keeping the public symbol here gives every target the same `DefaultSoftwareKeyProvidersKt`.
 */
public fun defaultSoftwareKeyProviders(): List<SoftwareKeyProvider> = platformSoftwareKeyProviders()

internal expect fun platformSoftwareKeyProviders(): List<SoftwareKeyProvider>
