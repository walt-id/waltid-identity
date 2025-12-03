@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.ShaUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/sd_hash-check"

@Serializable
@SerialName(policyId)
class SdHashCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Verify SD-JWT Key Binding by recalculating SD Hashes"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        presentationRequireNotNull(
            presentation.sdHash,
            DcSdJwtPresentationValidationError.MISSING_SD_HASH
        )
        addResult("presentation_sdhash", presentation.sdHash!!)

        log.trace { "Verifier received presentation: Recalculating hash for SD-JWT kb from: ${presentation.presentationStringHashable}" }
        val recalculatedSdHash = ShaUtils.calculateSha256Base64Url(presentation.presentationStringHashable)
        addResult("recalculated_sdhash", recalculatedSdHash)
        presentationRequire(presentation.sdHash == recalculatedSdHash, DcSdJwtPresentationValidationError.SD_HASH_MISMATCH)

        log.trace { "KB-JWT validated successfully. sd_hash matches." }

        return success()
    }
}
