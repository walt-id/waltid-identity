package id.walt.policies2.vp.policies.mso_mdoc

import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.policies2.vp.policies.AbstractVPPolicy

abstract class MdocVPPolicy(mdocId: String, description: String) : AbstractVPPolicy("mso_mdoc/$mdocId", description) {

    abstract suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        sessionTranscript: SessionTranscript?,
    ): Result<Unit>

    suspend fun runPolicy(
        document: Document,
        mso: MobileSecurityObject,
        sessionTranscript: SessionTranscript?,
    ) = runPolicy {
        verifyMdocPolicy(document, mso, sessionTranscript)
    }

}
