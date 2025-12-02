package id.walt.policies2.vp.policies.dc_sd_jwt

import id.walt.policies2.vp.policies.AbstractVPPolicy

abstract class DcSdJwtVPPolicy(sdJwtId: String, description: String) : AbstractVPPolicy("dc+sd-jwt/$sdJwtId", description) {

    abstract suspend fun VPPolicyRunContext.verifySdJwtPolicy(

    ): Result<Unit>

    suspend fun runPolicy(

    ) = runPolicy {
        verifySdJwtPolicy()
    }

}
