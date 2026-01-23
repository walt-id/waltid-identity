@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import kotlinx.serialization.Serializable

@Serializable
sealed class MdocVPPolicy() : VPPolicy2() {

    abstract suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext // MsoMdocVPVerificationRequest
    ): Result<Unit>

    suspend fun runPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext // MsoMdocVPVerificationRequest
    ) = runPolicy {
        verifyMdocPolicy(document, mso, verificationContext)
    }
}
