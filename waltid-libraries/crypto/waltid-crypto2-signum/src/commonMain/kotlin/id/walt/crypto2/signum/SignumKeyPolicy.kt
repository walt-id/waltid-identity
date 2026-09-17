package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeyAttestation
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.keys.KeyUsage
import kotlinx.serialization.Serializable

@Serializable
data class SignumKeyPolicy(
    /** Hardware backing preference; [HardwarePreference.REQUIRED] requires backend-observed hardware. */
    val hardware: HardwarePreference = HardwarePreference.PREFERRED,
    val authentication: SignumAuthenticationPolicy = SignumAuthenticationPolicy.None,
    /** Enables platform ECDH for keys with [KeyUsage.KEY_AGREEMENT] usage. */
    val keyAgreement: Boolean = false,
    /** Requests attestation evidence in addition to any hardware backing requirement. */
    val attestationChallenge: BinaryData? = null,
    @Serializable(with = SignumPlatformConfigurationSerializer::class)
    val platform: PlatformKeyConfiguration = PlatformKeyConfiguration.Default,
) {
    init {
        require(attestationChallenge == null || hardware != HardwarePreference.DISCOURAGED) {
            "Attestation requires preferred or required hardware backing"
        }
    }
}

@Serializable
sealed interface SignumAuthenticationPolicy {
    @Serializable
    data object None : SignumAuthenticationPolicy

    @Serializable
    data class UserPresence(
        val biometric: Boolean = true,
        /** Controls enrollment binding for biometric-only policies. On iOS, combining biometrics with
         * device credentials uses Apple's user-presence policy, which also accepts newly enrolled biometrics. */
        val allowNewBiometrics: Boolean = false,
        val deviceCredential: Boolean = true,
        val timeoutSeconds: Int = 0,
        val prompt: String = "Please authorize cryptographic operation",
        val cancelText: String = "Cancel",
    ) : SignumAuthenticationPolicy {
        init {
            require(biometric || deviceCredential) {
                "At least one authentication factor must be enabled"
            }
            require(biometric || !allowNewBiometrics) {
                "New biometrics cannot be allowed when biometrics are disabled"
            }
            require(timeoutSeconds >= 0) {
                "Authentication timeout cannot be negative"
            }
            require(prompt.isNotBlank()) { "Authentication prompt cannot be blank" }
            require(cancelText.isNotBlank()) { "Authentication cancel text cannot be blank" }
        }
    }
}

internal fun SignumAuthenticationPolicy.isBiometricCurrentSetEveryUse(): Boolean =
    this is SignumAuthenticationPolicy.UserPresence &&
        biometric &&
        !allowNewBiometrics &&
        !deviceCredential &&
        timeoutSeconds == 0

internal fun SignumAuthenticationPolicy.isBiometricTimedReuse(): Boolean =
    this is SignumAuthenticationPolicy.UserPresence &&
        biometric &&
        allowNewBiometrics &&
        !deviceCredential &&
        timeoutSeconds > 0

/** Derives only evidence-backed protection; REQUIRED is verified by each native backend before reporting HARDWARE. */
internal fun SignumKeyPolicy.effectiveProtection(attestation: KeyAttestation?): KeyProtectionLevel = when {
    attestation != null -> KeyProtectionLevel.HARDWARE
    hardware == HardwarePreference.DISCOURAGED -> KeyProtectionLevel.SOFTWARE
    else -> KeyProtectionLevel.UNKNOWN
}
