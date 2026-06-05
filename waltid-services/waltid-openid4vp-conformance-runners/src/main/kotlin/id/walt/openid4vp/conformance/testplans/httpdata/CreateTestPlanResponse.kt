package id.walt.openid4vp.conformance.testplans.httpdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateTestPlanResponse(
    val id: String, // mJxiQLcJwoZVX
    val name: String, // oid4vp-1final-verifier-test-plan
    val modules: List<Module> = listOf()
) {
    @Serializable
    data class Module(
        val testModule: String, // oid4vp-1final-verifier-happy-flow
        val instances: List<String?> = listOf(),
        val variant: Variant? = null
    ) {
        @Serializable
        data class Variant(
            @SerialName("client_id_prefix") val clientIdPrefix: String? = null,
            @SerialName("request_method") val requestMethod: String? = null,
            @SerialName("vp_profile") val vpProfile: String? = null
        )
    }
}
