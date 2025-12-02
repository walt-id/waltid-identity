package id.walt.policies2.vp.policies.jwt_vc_json

import id.walt.policies2.vp.policies.AbstractVPPolicy

abstract class JwtVcJsonVPPolicy(jwtVcJsonId: String, description: String) : AbstractVPPolicy("jwt_vc-json/$jwtVcJsonId", description) {
    abstract suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(

    ): Result<Unit>

    suspend fun runPolicy(

    ) = runPolicy {
        verifyJwtVcJsonPolicy()
    }

}
