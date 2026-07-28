package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.MontgomeryCurve

internal expect fun CryptographyCapabilityProfile.withPlatformCapabilities(): CryptographyCapabilityProfile

internal fun CryptographyCapabilityProfile.withAndroidPrivateImportCapabilities(): CryptographyCapabilityProfile = copy(
    keyGenerationFormats = keyGenerationFormats - KeyEncodingFormat.PKCS8_DER,
    keyImportFormats = keyImportFormats - KeyEncodingFormat.PKCS8_DER,
    privateKeyExportFormats = privateKeyExportFormats - KeyEncodingFormat.PKCS8_DER,
    privateJwkValidationSpecs = privateJwkValidationSpecs.filterIsInstance<KeySpec.Rsa>().toSet(),
)

/**
 * Drops Ed448 and X448. Web Crypto in Chrome, Apple CryptoKit, and the Android providers implement only the
 * Curve25519 pair, while the JDK, OpenSSL 3, and Node do offer the 448-bit curves. A curve cannot be probed from
 * the synchronous capability API - registering EdDSA or XDH says nothing about which curves it accepts - so the
 * platforms that cannot do them have to say so here instead of failing at key generation.
 */
internal fun CryptographyCapabilityProfile.without448Curves(): CryptographyCapabilityProfile = copy(
    keySpecs = keySpecs.filterNotTo(linkedSetOf(), ::is448Curve),
    // Kept consistent with keySpecs: the profile requires every validation specification to be a supported one.
    privateJwkValidationSpecs = privateJwkValidationSpecs.filterNotTo(linkedSetOf(), ::is448Curve),
)

private fun is448Curve(spec: KeySpec): Boolean =
    spec == KeySpec.Edwards(EdwardsCurve.ED448) || spec == KeySpec.Montgomery(MontgomeryCurve.X448)
