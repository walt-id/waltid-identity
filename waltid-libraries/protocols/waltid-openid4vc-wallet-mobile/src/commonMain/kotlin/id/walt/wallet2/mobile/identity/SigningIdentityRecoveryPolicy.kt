package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.PlatformKeyConfiguration

internal fun SigningIdentityConfiguration.permitsNativeSigning(): Boolean {
    val settings = platform as? PlatformKeyConfiguration.AndroidKeystore ?: return true
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    // Identity signatures cover protocol bytes. Protected Confirmation requires signing its own CBOR structure.
    if (settings.userConfirmationRequired || settings.userPresenceRequired) return false
    // Activation consumes one signature to prove possession of the original public key.
    if (settings.maxUsageCount == 1) return false
    if (settings.validFromEpochMillis?.let { it > now } == true || settings.validUntilEpochMillis?.let { it <= now } == true) return false
    return true
}

internal fun SigningIdentityConfiguration.recoveryConstraints(record: IdentityRecord) = RecoveryConstraints(
    record.storage, record.requirements.authorizationPolicy, requiredConfirmation(record))

internal fun SigningIdentityConfiguration.requiredConfirmation(record: IdentityRecord): RecoveryConfirmation =
    if (recoveryConfirmation == RecoveryConfirmation.ProviderConfirmation ||
        record.recoveryConfirmation == RecoveryConfirmation.ProviderConfirmation) RecoveryConfirmation.ProviderConfirmation
    else RecoveryConfirmation.LocalAcceptance
