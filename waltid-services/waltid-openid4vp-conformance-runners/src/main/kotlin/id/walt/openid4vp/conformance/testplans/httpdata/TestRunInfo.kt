package id.walt.openid4vp.conformance.testplans.httpdata


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
data class TestRunInfo(
    @SerialName("_id")
    val id: String, // hHufFDmFHEuLYrb
    val testId: String, // hHufFDmFHEuLYrb
    val testName: String, // oid4vp-1final-verifier-happy-flow
    val variant: JsonObject,
    val started: Instant, // 2025-10-20T01:46:42.719257027Z

    val config: JsonObject,
    val description: String, // Verifier - iso_mdl + x509_san_dns + request_uri_signed + direct_post
    val alias: String = "",

    val owner: Owner,

    val planId: String, // bq9fO1CBWVlPE
    val status: String, // INTERRUPTED
    val result: String?, // FAILED

    val version: String, // 5.1.36

    val summary: String, // Expects the verifier to make a valid OID4VP request that matches the configuration. The test creates and returns a valid credential that the verifier should accept.Depending on the test configuration the credential may be:	* SD-JWT VC credential with a vct of urn:eudi:pid:1 as defined in ARF 1.8 ( https://eu-digital-identity-wallet.github.io/eudi-doc-architecture-and-reference-framework/latest/annexes/annex-3/annex-3.01-pid-rulebook/#5-sd-jwt-vc-based-encoding-of-pid ).	* An mdl as per ISO 18013-5The presentation_definition must contain only one input_descriptor, or the DCQL must request only a single credential.The conformance suite acts as a mock web wallet. You must configure your verifier to use the authorization endpoint url below instead of 'openid4vp://' and then start the flow in your verifier as normal.

    val publish: JsonElement? // null
) {

    @Serializable
    data class Owner(
        val iss: String, // https://developer.com
        val sub: String // developer
    )

}
