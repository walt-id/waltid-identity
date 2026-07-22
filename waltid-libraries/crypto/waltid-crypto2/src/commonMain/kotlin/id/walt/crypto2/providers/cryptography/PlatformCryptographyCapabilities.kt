package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec

internal expect fun CryptographyCapabilityProfile.withPlatformCapabilities(): CryptographyCapabilityProfile

internal fun CryptographyCapabilityProfile.withAndroidPrivateImportCapabilities(): CryptographyCapabilityProfile = copy(
    keyGenerationFormats = keyGenerationFormats - KeyEncodingFormat.PKCS8_DER,
    keyImportFormats = keyImportFormats - KeyEncodingFormat.PKCS8_DER,
    privateKeyExportFormats = privateKeyExportFormats - KeyEncodingFormat.PKCS8_DER,
    privateJwkValidationSpecs = privateJwkValidationSpecs.filterIsInstance<KeySpec.Rsa>().toSet(),
)
