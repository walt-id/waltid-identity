package id.walt.crypto2.keys

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable

/** Origin of private key material; hardware storage does not imply hardware generation. */
@Serializable
public enum class KeyOrigin { GENERATED, IMPORTED, UNKNOWN }

/** Observed native security level, not a certification or issuer assurance rating. */
@Serializable
public enum class KeySecurityLevel { SOFTWARE, TRUSTED_ENVIRONMENT, STRONGBOX, SECURE_ENCLAVE, UNKNOWN }

/** Observed protection of the native key. */
@Serializable
public enum class KeyProtectionLevel { HARDWARE, SOFTWARE, UNKNOWN }

/** Evidence returned by a provider. Applications must verify it before making assurance claims. */
@Serializable
public data class KeyAttestation(
    public val format: String,
    public val statement: BinaryData,
    public val certificateChain: List<BinaryData> = emptyList(),
) {
    init {
        require(format.isNotBlank()) { "Attestation format cannot be blank" }
        require(statement.size > 0) { "Attestation statement cannot be empty" }
    }
}

/** Source used to validate the key's authorization policy; none of these values is attestation. */
@Serializable
public enum class KeyAuthorizationEvidence {
    /** The provider has not established the authorization policy. */
    UNKNOWN,
    /** Authorization settings were read from the native key's attributes. */
    NATIVE_ATTRIBUTES,
    /** Authorization settings come from an SDK creation record bound to the native entry. */
    CREATION_RECORD,
}
