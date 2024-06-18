package id.walt.mdoc.doc

import id.walt.mdoc.docrequest.MDocRequest
import id.walt.mdoc.mdocauth.DeviceAuthentication

/**
 * Parameters, for verification of MDoc documents
 * @param verificationTypes Types of verification to apply
 * @param issuerKeyID ID of the issuer key for verification of MSO signature, if required by crypto provider impl.
 * @param deviceKeyID ID of the device key for verification of device signed data, if using device signature mode and if required by crypto provider impl.
 * @param ephemeralMacKey Ephemeral MAC key for verification of device signed data, as negotiated during session establishment, if using device MAC mode
 * @param deviceAuthentication Device authentication structure, that represents the payload signed by the device
 * @param mDocRequest MDoc request containing requested issuer signed items
 */
data class MDocVerificationParams(
  val verificationTypes: VerificationTypes,
  val issuerKeyID: String? = null,
  val deviceKeyID: String? = null,
  val ephemeralMacKey: ByteArray? = null,
  val deviceAuthentication: DeviceAuthentication? = null,
  val mDocRequest: MDocRequest? = null
)
