package id.walt.mdoc.objects.document

import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the device authentication data within a `DeviceSigned` structure.
 *
 * This structure provides proof of possession of the mdoc private key, which prevents cloning
 * and mitigates Man-in-the-Middle (MITM) attacks. It contains either a digital signature
 * (`deviceSignature`) or a message authentication code (`deviceMac`), but never both.
 *
 * The choice between a signature and a MAC has privacy implications; a MAC is not
 * non-repudiable, which can be preferable for the holder's privacy.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (DeviceResponse CDDL structure)
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.3 (mdoc authentication mechanism)
 *
 * @property deviceSignature The COSE_Sign1 structure if authentication is performed via digital signature. Null otherwise.
 * @property deviceMac The COSE_Mac0 structure if authentication is performed via a Message Authentication Code. Null otherwise.
 */
@Serializable
data class DeviceAuth(
    @SerialName("deviceSignature")
    val deviceSignature: CoseSign1? = null, // ByteArray
    @SerialName("deviceMac")
    val deviceMac: CoseMac0? = null, // ByteArray
) {
    init {
        // Enforce the ISO/IEC 18013-5 rule that exactly one of the two fields must be present.
        require((deviceSignature == null) xor (deviceMac == null)) {
            "DeviceAuth must contain either a 'deviceSignature' or a 'deviceMac', but not both or neither."
        }
    }
}
