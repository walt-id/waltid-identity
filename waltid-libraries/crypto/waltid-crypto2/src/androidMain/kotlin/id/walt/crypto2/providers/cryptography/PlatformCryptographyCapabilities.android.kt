package id.walt.crypto2.providers.cryptography

internal actual fun CryptographyCapabilityProfile.withPlatformCapabilities(): CryptographyCapabilityProfile =
    withAndroidPrivateImportCapabilities()
