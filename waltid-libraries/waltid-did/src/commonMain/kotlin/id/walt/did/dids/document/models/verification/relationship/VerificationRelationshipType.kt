package id.walt.did.dids.document.models.verification.relationship

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Enumerated type representing verification relationship types according to the respective section [DID Core](https://www.w3.org/TR/did-core/#verification-relationships) specification
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class VerificationRelationshipType {
    @SerialName("assertionMethod")
    AssertionMethod,

    @SerialName("authentication")
    Authentication,

    @SerialName("capabilityDelegation")
    CapabilityDelegation,

    @SerialName("capabilityInvocation")
    CapabilityInvocation,

    @SerialName("keyAgreement")
    KeyAgreement;

    override fun toString(): String {
        return when (this) {
            AssertionMethod -> "assertionMethod"
            Authentication -> "authentication"
            CapabilityDelegation -> "capabilityDelegation"
            CapabilityInvocation -> "capabilityInvocation"
            KeyAgreement -> "keyAgreement"
        }
    }
}
