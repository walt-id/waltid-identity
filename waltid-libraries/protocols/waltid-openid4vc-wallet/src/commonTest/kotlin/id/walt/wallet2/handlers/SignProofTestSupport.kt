package id.walt.wallet2.handlers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object SignProofTestSupport {
    const val ISSUER = "https://issuer.example"
    const val CONFIG_ID = "test-credential"

    fun issuerMetadataClient(
        configurationId: String = CONFIG_ID,
        proofAlgorithms: Set<String> = setOf("ES256", "EdDSA"),
    ): HttpClient {
        val algorithmsJson = proofAlgorithms.joinToString(",") { "\"$it\"" }
        val body = """
            {
              "credential_issuer":"$ISSUER",
              "credential_endpoint":"$ISSUER/credential",
              "credential_configurations_supported":{
                "$configurationId":{
                  "format":"jwt_vc_json",
                  "cryptographic_binding_methods_supported":["jwk"],
                  "proof_types_supported":{
                    "jwt":{"proof_signing_alg_values_supported":[$algorithmsJson]}
                  }
                }
              }
            }
        """.trimIndent()
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    require(request.url.toString().contains("/.well-known/openid-credential-issuer")) {
                        "Unexpected metadata request: ${request.url}"
                    }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
