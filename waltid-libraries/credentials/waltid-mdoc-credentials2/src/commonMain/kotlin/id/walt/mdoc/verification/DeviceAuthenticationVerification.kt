package id.walt.mdoc.verification

import id.walt.cose.Cose
import id.walt.cose.protectedAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject

data class DeviceAuthenticationVerification(
    val deviceKey: Key,
    val coseAlgorithm: Int,
    val deviceAuthenticationBytes: ByteArray,
)

val supportedMdocDeviceSignatureAlgorithms: Set<Int> = setOf(
    Cose.Algorithm.ES256,
    Cose.Algorithm.ES384,
    Cose.Algorithm.ES512,
    Cose.Algorithm.EdDSA,
)

suspend fun verifyDeviceAuthentication(
    document: Document,
    mso: MobileSecurityObject,
    sessionTranscript: SessionTranscript,
    allowedAlgorithms: Set<Int> = supportedMdocDeviceSignatureAlgorithms,
): DeviceAuthenticationVerification {
    val deviceSigned = document.deviceSigned
        ?: throw IllegalArgumentException("DeviceSigned structure is missing")
    val deviceSignature = deviceSigned.deviceAuth.deviceSignature
    if (deviceSignature == null) {
        if (deviceSigned.deviceAuth.deviceMac != null) {
            throw UnsupportedOperationException(
                "Device MAC authentication is not yet supported. Only DeviceSignature is currently validated."
            )
        }
        throw IllegalArgumentException("No device authentication provided")
    }
    val deviceAuthenticationBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
        transcript = sessionTranscript,
        docType = document.docType,
        namespaces = deviceSigned.namespaces,
    )
    val algorithm = deviceSignature.protectedAlgorithm()
    val deviceKey = MdocCrypto.coseKeyToCrypto2Key(mso.deviceKeyInfo.deviceKey, algorithm)
    require(
        MdocCrypto.verifyDeviceSignature(
            payloadToVerify = deviceAuthenticationBytes,
            deviceSignature = deviceSignature,
            devicePublicKey = deviceKey,
            allowedAlgorithms = allowedAlgorithms,
        )
    ) { "Device authentication signature failed to verify" }
    return DeviceAuthenticationVerification(deviceKey, algorithm, deviceAuthenticationBytes)
}
