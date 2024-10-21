package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OIDC : AuthenticationMethod("oidc") {

//    override val config = OidcAuthConfiguration::class

    /*
    Steps:
    1. redirect to OP auth endpoint
        - generate state, fill redirect_uri with callback
        - OP will redirect to redirect_uri callback with auth code (on success) or error code
    2. At callback, RP checks if state matches and then exchanges code for ID token
        - code for token exchange to token endpoint
        - same callback uri as redirect_uri
        - OP responds with ID token, access token, possibly refresh token
    3. Validate returned ID token, retrieve claims
    */


    /**
     * minimal open id configuration - unused fields are omitted, use Json with ignoreUnknownKeys = true when deserializing into this
     */
    @Serializable
    data class OpenIdConfiguration(
        val issuer: String? = null,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") val jwksUri: String? = null,
        @SerialName("response_types_supported") val responseTypesSupported: ArrayList<String> = arrayListOf(),
        @SerialName("subject_types_supported") val subjectTypesSupported: ArrayList<String> = arrayListOf(),
        @SerialName("id_token_signing_alg_values_supported") val idTokenSigningAlgValuesSupported: ArrayList<String> = arrayListOf(),
        @SerialName("scopes_supported") val scopesSupported: ArrayList<String> = arrayListOf(),
        @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: ArrayList<String> = arrayListOf(),
        @SerialName("claims_supported") val claimsSupported: ArrayList<String> = arrayListOf(),
    ) {
        companion object {
            val INVALID = OpenIdConfiguration(
                issuer = "invalid",
                authorizationEndpoint = "invalid",
                tokenEndpoint = "invalid"
            )
        }
    }

    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun resolveConfiguration(configUrl: String): OpenIdConfiguration {
        return http.get(configUrl).response<OpenIdConfiguration>()
    }

    fun getOpenidProviderAuth(config: OidcAuthConfiguration, state: String, redirectUri: String): Url {
        val url = URLBuilder(config.openIdConfiguration.authorizationEndpoint).apply {
            parameters.apply {
                append("response_type", "code")
                append("scope", "openid")
                append("client_id", config.clientId)
                append("state", state)
                append("redirect_uri", redirectUri)
            }
        }.build()

        return url
    }

    @Serializable
    data class TokenResponse(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") val expiresIn: Int,
    )

    suspend fun codeToToken(contextConfig: OidcAuthConfiguration, code: String): TokenResponse {
        val formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code) // from /auth
            append("redirect_uri", redirectUri) // callback from /auth
        }

        return http.submitForm(contextConfig.openIdConfiguration.tokenEndpoint, formParameters) {
            basicAuth(contextConfig.clientId, contextConfig.clientSecret)
        }.response<TokenResponse>()
    }

    @Serializable
    data class OidcAuthSession(
        val id: String,
        val authUrl: String,
        val state: String,
    )

    val oidcSessions = HashMap<String, OidcAuthSession>()
    val oidcSessionState = HashMap<String, String>() // state -> session id

    val redirectUri = "http://localhost:8088/auth/oidc/callback"

    @OptIn(ExperimentalUuidApi::class)
    fun createOidcSession(context: Unit, config: OidcAuthConfiguration): OidcAuthSession {
        val newSessionId = Uuid.random().toString()
        val newState = Uuid.random().toString()

        val authUrl = getOpenidProviderAuth(config, newState, redirectUri).toString()

        val newSession = OidcAuthSession(
            id = newSessionId,
            authUrl = authUrl,
            state = newState
        )
        oidcSessions[newSession.id] = newSession
        oidcSessionState[newSession.state] = newSession.id

        return newSession
    }

    suspend fun userInfo(openIdConfiguration: OpenIdConfiguration, accessToken: String): JsonObject {
        return http.get(openIdConfiguration.userinfoEndpoint!!) {
            bearerAuth(accessToken)
        }.body<JsonObject>()
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {


        route("oidc") {
            get("auth") {
                val session = getSession(authContext)
                val config = session.lookupConfiguration<OidcAuthConfiguration>(this@OIDC)

                val oidcAuthSession = createOidcSession(context = Unit, config)
                context.respondRedirect(oidcAuthSession.authUrl)
            }
            get("callback") {
                val session = getSession(authContext)
                val config = session.lookupConfiguration<OidcAuthConfiguration>(this@OIDC)

                val params = context.parameters
                val code = params.getOrFail("code")
                val state = params.getOrFail("state")

                val oidcSession = oidcSessionState[state] ?: error("No session for state")
                val tokenResponse = codeToToken(config, code)
                println(tokenResponse)

                val user = userInfo(config.openIdConfiguration, tokenResponse.accessToken)
                val sub = user["sub"]!!.jsonPrimitive.content

                // TODO: better OIDC Identifier (make sure malicious cannot generate a clash per URL)
                val identifier = OIDCIdentifier(config.openIdConfiguration.authorizationEndpoint, sub)

                context.handleAuthSuccess(session, identifier.resolveToAccountId())
            }
        }

    }


}

suspend inline fun <reified T> HttpResponse.response(): T {
    require(call.response.status.isSuccess()) { "Non successful call: ${call.request.url}, ${call.response.bodyAsText()}" }

    val json = call.body<JsonObject>()
    return Json.decodeFromJsonElement<T>(json)
}
