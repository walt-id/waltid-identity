@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


private const val policyId = "mso_mdoc/mso"

@Serializable
@SerialName(policyId)
class MsoVerificationMdocVpPolicy(
    /**
     * When true, enforce EAA-6.2.7.1-04/05 (ETSI TS 119 472-1 v1.2.1):
     * validFrom and validUntil SHALL have seconds precision and SHALL NOT contain fractions of seconds.
     * Defaults to false so existing test fixtures with sub-second precision still pass.
     * Set to true in ETSI plugtest / strict-conformance contexts.
     */
    @SerialName("strict_etsi_precision")
    val strictEtsiPrecision: Boolean = false
) : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify MSO"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        log.trace { "--- Verifying MSO ---" }
        val timestamps = mso.validityInfo
        addResult("signed", timestamps.signed.toString())
        addResult("valid_from", timestamps.validFrom.toString())
        addResult("valid_until", timestamps.validUntil.toString())
        timestamps.validate()

        // EAA-6.2.7.1-04/05 (ETSI TS 119 472-1 v1.2.1): validFrom and validUntil SHALL
        // have seconds precision and SHALL NOT contain fractions of seconds.
        // Only enforced when strictEtsiPrecision=true (e.g. in ETSI plugtest context).
        if (strictEtsiPrecision) {
            require(timestamps.validFrom.nanosecondsOfSecond == 0) {
                "MSO validityInfo.validFrom SHALL NOT contain fractions of seconds " +
                "(EAA-6.2.7.1-05 ETSI TS 119 472-1); got: ${timestamps.validFrom}"
            }
            require(timestamps.validUntil.nanosecondsOfSecond == 0) {
                "MSO validityInfo.validUntil SHALL NOT contain fractions of seconds " +
                "(EAA-6.2.7.1-05 ETSI TS 119 472-1); got: ${timestamps.validUntil}"
            }
        }

        require(MdocCrypto.isSupportedDigest(mso.digestAlgorithm)) {
            "MSO digest algorithm is not supported: ${mso.digestAlgorithm}"
        }

        return success()
    }
}
