package id.walt.issuer.issuance.openapi.issuerapi

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object IssuerSessionDocs {
    @Serializable
    data class SwaggerIssuanceSessionInfo(
        val id: String,
        val enterpriseStatusState: JsonObject? = null,
        val customParameters: Map<String, JsonElement> = mapOf(),
    )

    fun getIssuanceSessionDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Issuance")
        summary = "Get info about OIDC issuance session, that was previously initialized"
        description =
            "Session info, containing current state and result information about an ongoing OIDC issuance session"
        request {
            pathParameter<String>("id") {
                description = "Issuance Session ID"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Issuance session info"
                body<SwaggerIssuanceSessionInfo>()
            }
            HttpStatusCode.NotFound to {
                description = "Issuance session not found or invalid"
                body<String> {}
            }
        }
    }
}
