package id.walt.openid4vp.conformance.testplans.httpdata

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
        class Variant
    }
}
