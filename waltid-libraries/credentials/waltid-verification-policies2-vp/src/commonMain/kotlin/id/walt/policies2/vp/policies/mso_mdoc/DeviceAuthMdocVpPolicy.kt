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
        verificationContext: VerificationSessionContext?
    ): Result<Unit> = coroutineScope {
        requireNotNull(verificationContext) { "Verification context needs to be provided for DeviceAuth Mdoc VP Policy" }

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

        // For DC API flows, log the origin used in SessionTranscript reconstruction to help
        // diagnose mismatches (the wallet uses the browser-reported origin; this must match exactly)
        if (verificationContext.isDcApi) {
            val originUsed = verificationContext.expectedOrigins?.firstOrNull()
            addResult("dc_api_origin_used_for_transcript", originUsed ?: "null")
            log.debug { "DC API device auth: reconstructing SessionTranscript with origin='$originUsed'" }
        }

        when {
            deviceAuth.deviceSignature != null -> {
                log.trace { "Device auth contains device signature: ${deviceAuth.deviceSignature}" }
                require(
                    MdocCrypto.verifyDeviceSignature(
                        payloadToVerify = deviceAuthBytes,
                        deviceSignature = deviceAuth.deviceSignature!!,
                        sDevicePublicKey = devicePublicKey
                    )
                ) {
                    when {
                        verificationContext.isDcApi -> {
                            val originUsed = verificationContext.expectedOrigins?.firstOrNull()
                            "Device authentication signature failed to verify (Annex D - OID4VP over DC API). " +
                            "The verifier reconstructed the OpenID4VPDCAPIHandover SessionTranscript using " +
                            "origin='$originUsed' (from expectedOrigins[0]). " +
                            "The wallet signs using the exact browser page origin reported by the platform - " +
                            "these must match. Verify that expectedOrigins[0] in DcApiAnnexDFlowSetup equals " +
                            "the origin of the page that called navigator.credentials.get()."
                        }
                        verificationContext.isAnnexC ->
                            "Device authentication signature failed to verify (Annex C - ISO 18013-7 DC API). " +
                            "The verifier reconstructed the DCAPIHandover SessionTranscript using the origin " +
                            "from DcApiAnnexCFlowSetup. Verify that this origin matches the origin the wallet " +
                            "received from the platform (i.e. the origin of the page that triggered the request)."
                        else ->
                            "Device authentication signature failed to verify."
                    }
                }

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
            ?: throw IllegalArgumentException(
                "Missing expectedOrigins for Annex D (OID4VP over DC API) device authentication. " +
                "DcApiAnnexDFlowSetup must include expectedOrigins with the exact origin of the " +
                "page that called navigator.credentials.get() " +
                "(e.g. 'https://verifier.example.com'). This value is used to reconstruct the " +
                "OpenID4VPDCAPIHandover SessionTranscript for signature verification."
            )) else expectedAudience,
        responseUri = responseUri,
        isEncrypted = isEncrypted,
        isDcApi = isDcApi,
        isAnnexC = isAnnexC,
        data = customData,

        jwkThumbprint = jwkThumbprint
    )
}
