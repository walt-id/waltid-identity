@file:OptIn(ExperimentalTime::class)
@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.ExperimentalTime

class MsoVerificationMdocVpPolicy : MdocVPPolicy("mso", "Verify MSO") {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: MsoMdocVPVerificationRequest
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
