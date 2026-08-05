package id.walt.crypto2.providers.cryptography

/**
 * Whether the platform's cryptography provider accepts an explicit RSASSA-PSS salt length.
 *
 * Apple's Security framework only exposes PSS with the fixed, digest-sized salt and rejects every
 * explicit salt size, so those platforms report custom salt lengths as unsupported instead of failing
 * during signing.
 */
internal expect val platformSupportsCustomPssSaltLengths: Boolean
