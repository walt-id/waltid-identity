@file:OptIn(ExperimentalTime::class)
@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

private const val policyId = "mso_mdoc/mso"

@Serializable
@SerialName(policyId)
class MsoVerificationMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify MSO"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        log.trace { "--- Verifying MSO ---" }
        val timestamps = mso.validityInfo
        addResult("signed", timestamps.signed.toString())
        addResult("valid_from", timestamps.validFrom.toString())
        addResult("valid_from", timestamps.validUntil.toString())
        timestamps.validate()

        require(MdocCrypto.isSupportedDigest(mso.digestAlgorithm)) {
            "MSO digest algorithm is not supported: ${mso.digestAlgorithm}"
        }

        return success()
    }
}
