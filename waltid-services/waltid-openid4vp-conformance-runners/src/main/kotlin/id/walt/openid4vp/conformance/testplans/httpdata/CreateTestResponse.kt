package id.walt.openid4vp.conformance.testplans.httpdata


import kotlinx.serialization.Serializable

@Serializable
data class CreateTestResponse(
    val id: String, // vqxeOg4tC1wQiSo
    val name: String, // oid4vp-1final-verifier-happy-flow
    val url: String // https://localhost.emobix.co.uk:8443/test/vqxeOg4tC1wQiSo
)
