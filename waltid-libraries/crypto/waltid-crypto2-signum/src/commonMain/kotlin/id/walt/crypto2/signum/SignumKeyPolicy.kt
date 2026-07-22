package id.walt.crypto2.signum

import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.keys.KeyUsage
import kotlinx.serialization.Serializable

@Serializable
data class SignumKeyPolicy(
    val hardware: SignumHardwarePolicy = SignumHardwarePolicy.PREFERRED,
    val authentication: SignumAuthenticationPolicy = SignumAuthenticationPolicy.None,
    /** Enables platform ECDH for keys with [KeyUsage.KEY_AGREEMENT] usage. */
    val keyAgreement: Boolean = false,
    val attestationChallenge: BinaryData? = null,
) {
    init {
        require(attestationChallenge == null || hardware != SignumHardwarePolicy.DISCOURAGED) {
            "Attestation requires preferred or required hardware backing"
        }
    }
}

@Serializable
enum class SignumHardwarePolicy {
    REQUIRED,
    PREFERRED,
    DISCOURAGED,
}

@Serializable
sealed interface SignumAuthenticationPolicy {
    @Serializable
    data object None : SignumAuthenticationPolicy

    @Serializable
    data class UserPresence(
        val biometric: Boolean = true,
        val allowNewBiometrics: Boolean = false,
        val deviceCredential: Boolean = true,
        val timeoutSeconds: Int = 0,
        val prompt: String = "Please authorize cryptographic operation",
        val cancelText: String = "Cancel",
    ) : SignumAuthenticationPolicy {
        init {
            require(biometric || deviceCredential) { "At least one authentication factor must be enabled" }
            require(biometric || !allowNewBiometrics) { "New biometrics cannot be allowed when biometrics are disabled" }
            require(timeoutSeconds >= 0) { "Authentication timeout cannot be negative" }
            require(prompt.isNotBlank()) { "Authentication prompt cannot be blank" }
            require(cancelText.isNotBlank()) { "Authentication cancel text cannot be blank" }
        }
    }
}

@Serializable
enum class SignumProtectionLevel {
    HARDWARE,
    SOFTWARE,
    UNKNOWN,
}

@Serializable
data class SignumKeyAttestation(
    val format: String,
    val statement: BinaryData,
    val certificateChain: List<BinaryData> = emptyList(),
) {
    init {
        require(format.isNotBlank()) { "Attestation format cannot be blank" }
        require(statement.size > 0) { "Attestation statement cannot be empty" }
    }
}

internal fun SignumKeyPolicy.effectiveProtection(attestation: SignumKeyAttestation?): SignumProtectionLevel = when {
    attestation != null -> SignumProtectionLevel.HARDWARE
    hardware == SignumHardwarePolicy.DISCOURAGED -> SignumProtectionLevel.SOFTWARE
    else -> SignumProtectionLevel.UNKNOWN
}
