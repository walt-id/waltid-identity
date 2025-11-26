package id.walt.mdoc.verification

import id.walt.crypto.keys.DirectSerializedKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A result class providing a detailed breakdown of the mdoc verification process.
 *
 * @property valid - Overall: True if all mandatory verification checks passed, false otherwise.
 * @property issuerSignatureValid: True if the issuer signature on the Mobile Security Object (MSO) is valid.
 * @property dataIntegrityValid: True if the digests of all returned issuer-signed data elements match the digests in the MSO.
 * @property msoValidityValid: True if the current time is within the MSO's validity period.
 * @property deviceSignatureValid: True if the device authentication (signature or MAC) is valid.
 * @property deviceKeyAuthorized: True if the device key was authorized by the issuer to sign the returned data elements.
 *
 * @property errors: Any errors that caused one of the checks to be false are listed out here
 */
@Serializable
data class VerificationResult(
    val valid: Boolean,
    val issuerSignatureValid: Boolean? = null,
    val dataIntegrityValid: Boolean? = null,
    val msoValidityValid: Boolean? = null,
    val deviceSignatureValid: Boolean? = null,
    val deviceKeyAuthorized: Boolean? = null,

    val docType: String,
    val x5c: List<String>?,
    val signerKey: DirectSerializedKey?,
    val credentialData: JsonObject,

    val errors: List<String> = emptyList()
)
