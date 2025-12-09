@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.MdocVerificationContext
import id.walt.mdoc.verification.MdocVerifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "mso_mdoc/device-auth"

@Serializable
@SerialName(policyId)
class DeviceAuthMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify device authentication"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext
    ): Result<Unit> = coroutineScope {
        log.trace { "--- Verifying device authentication ---" }
        log.trace { "Using verification context: $verificationContext" }

        val deviceSigned = document.deviceSigned ?: throw IllegalArgumentException("DeviceSigned structure is missing.")
        log.trace { "Device signed: $deviceSigned" }


        val devicePublicKeyJwk = mso.deviceKeyInfo.deviceKey.toJWK()
        addResult("device_public_jwk", devicePublicKeyJwk)
        val devicePublicKey = JWKKey.importJWK(devicePublicKeyJwk.toString()).getOrThrow()
        log.trace { "Device public key: $devicePublicKey" }

        val deviceAuth = deviceSigned.deviceAuth
        log.trace { "Device auth (of device signed): $deviceAuth" }

        val sessionTranscript = MdocVerifier.buildSessionTranscriptForContext(verificationContext.toMdocVerificationContext())

        requireNotNull(sessionTranscript) { "Requiring session transcript for this verification policy" }

        val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript = sessionTranscript,
            docType = document.docType,
            namespaces = deviceSigned.namespaces
        )
        log.trace { "Device auth bytes (hex): ${deviceAuthBytes.toHexString()}" }
        addResult("device_auth_bytes_hex", deviceAuthBytes.toHexString())

        when {
            deviceAuth.deviceSignature != null -> {
                log.trace { "Device auth contains device signature: ${deviceAuth.deviceSignature}" }
                require(
                    MdocCrypto.verifyDeviceSignature(
                        payloadToVerify = deviceAuthBytes,
                        deviceSignature = deviceAuth.deviceSignature!!,
                        sDevicePublicKey = devicePublicKey
                    )
                ) { "Device authentication signature failed to verify." }

                success()
            }

            deviceAuth.deviceMac != null -> {
                TODO("Device MAC is not yet validated")
                /*val eReaderKeyBytes = MdocCrypto.parseSessionTranscript(sessionTranscript)?.eReaderKeyBytes
                    ?: return false
                val eReaderPublicKey = MdocCrypto.decodeCoseKey(eReaderKeyBytes)

                MdocCrypto.verifyDeviceMac(
                    deviceAuthBytes = deviceAuthBytes,
                    deviceMac = deviceAuth.deviceMac,
                    sessionTranscript = sessionTranscript,
                    eReaderPrivateKey,
                    sDevicePublicKey = devicePublicKey
                )*/
            }

            else -> { // No device authentication provided
                throw IllegalArgumentException("No device authentication provided ")
            }
        }
    }

    private fun VerificationSessionContext.toMdocVerificationContext() = MdocVerificationContext(
        expectedNonce = expectedNonce,
        expectedAudience = if (isDcApi) (expectedOrigins?.firstOrNull()
            ?: throw IllegalArgumentException("Missing expected origin for DC API")) else expectedAudience,
        responseUri = responseUri,
        isEncrypted = isEncrypted,
        isDcApi = isDcApi,

        jwkThumbprint = jwkThumbprint
    )
}
