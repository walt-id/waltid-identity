package id.walt.crypto2.providers.cryptography

internal actual fun CryptographyCapabilityProfile.withPlatformCapabilities(): CryptographyCapabilityProfile =
    this.without448Curves()

// Android JCA does not expose public-point derivation from a private EC scalar.
internal actual val useEcPairwiseValidation: Boolean = false
