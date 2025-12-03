@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject

sealed class MdocVPPolicy(mdocId: String, description: String) : VPPolicy2("mso_mdoc/$mdocId", description) {

    abstract suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: MsoMdocVPVerificationRequest,
    ): Result<Unit>

    suspend fun runPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: MsoMdocVPVerificationRequest,
    ) = runPolicy {
        verifyMdocPolicy(document, mso, verificationContext)
    }
}
