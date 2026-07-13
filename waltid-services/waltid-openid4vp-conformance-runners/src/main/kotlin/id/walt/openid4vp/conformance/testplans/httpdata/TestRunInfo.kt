package id.walt.openid4vp.conformance.testplans.httpdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant


@Serializable
data class TestRunInfo(
    @SerialName("_id")
    val id: String, // hHufFDmFHEuLYrb
    val testId: String, // hHufFDmFHEuLYrb
    val testName: String, // oid4vp-1final-verifier-happy-flow
    val variant: JsonObject,
    val started: Instant, // 2025-10-20T01:46:42.719257027Z

    val config: JsonObject,
    val description: String? = null, // Verifier - iso_mdl + x509_san_dns + request_uri_signed + direct_post
    val alias: String = "",

    val owner: Owner,

    val planId: String, // bq9fO1CBWVlPE
    val status: String, // INTERRUPTED
    val result: String?, // FAILED

    val version: String, // 5.1.36

    val summary: String? = null, // May be null for wallet tests

    val publish: JsonElement? = null // null
) {

    @Serializable
    data class Owner(
        val iss: String, // https://developer.com
        val sub: String // developer
    )

}
