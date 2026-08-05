@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.MdocVerificationContext
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.verifyDeviceAuthentication
import io.github.oshai.kotlinlogging.KotlinLogging
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
    ): Result<Unit> {
        requireNotNull(verificationContext) { "Verification context needs to be provided for DeviceAuth Mdoc VP Policy" }
        require(!verificationContext.isEncrypted || !verificationContext.jwkThumbprint.isNullOrBlank()) {
            "Encrypted mdoc presentation requires the response-encryption JWK thumbprint"
        }

        log.trace { "--- Verifying device authentication ---" }
        log.trace { "Using verification context: $verificationContext" }

        val devicePublicKeyJwk = mso.deviceKeyInfo.deviceKey.toJWK()
        addResult("device_public_jwk", devicePublicKeyJwk)

        val sessionTranscript = MdocVerifier.buildSessionTranscriptForContext(verificationContext.toMdocVerificationContext())
        val verification = verifyDeviceAuthentication(document, mso, sessionTranscript)
        log.trace { "Device public key: ${verification.deviceKey}" }
        log.trace { "Device auth bytes (hex): ${verification.deviceAuthenticationBytes.toHexString()}" }
        addResult("device_auth_bytes_hex", verification.deviceAuthenticationBytes.toHexString())
        return success()
    }

    private fun VerificationSessionContext.toMdocVerificationContext() = MdocVerificationContext(
        expectedNonce = expectedNonce,
        expectedAudience = if (isDcApi) (expectedOrigins?.firstOrNull()
            ?: throw IllegalArgumentException("Missing expected origin for DC API")) else expectedAudience,
        responseUri = responseUri,
        isEncrypted = isEncrypted,
        isDcApi = isDcApi,
        isAnnexC = isAnnexC,
        data = customData,

        jwkThumbprint = jwkThumbprint
    )
}
