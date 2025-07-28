@file:OptIn(ExperimentalUuidApi::class)

package id.walt.verifier

import id.walt.commons.config.ConfigManager
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.policies.PolicyManager
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.RequestSigningCryptoProvider
import id.walt.verifier.oidc.VerifierService
import id.walt.verifier.oidc.models.presentedcredentials.PresentedCredentialsViewMode
import id.walt.verifier.openapi.PresentedCredentialsDocs
import id.walt.verifier.openapi.VerifierApiDocs
import id.walt.w3c.utils.VCFormat
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val SERVER_URL by lazy {
    runBlocking {
        ConfigManager.loadConfigs(arrayOf())
        ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl
    }
}

@Serializable
data class DescriptorMappingFormParam(val id: String, val format: VCFormat, val path: String)

@Serializable
data class PresentationSubmissionFormParam(
    val id: String, val definition_id: String, val descriptor_map: List<DescriptorMappingFormParam>,
)

@Serializable
data class TokenResponseFormParam(
    val vp_token: JsonElement?,
    val presentation_submission: PresentationSubmissionFormParam?,
    val response: String?,
)

const val defaultAuthorizeBaseUrl = "openid4vp://authorize"

private val logger = logger("Verifier API")

private const val fixedPresentationDefinitionForEbsiConformanceTest =
    "{\"id\":\"any\",\"format\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}},\"input_descriptors\":[{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}}]}"

@OptIn(ExperimentalUuidApi::class)
fun Application.verifierApi() {
    routing {

        route("openid4vc", {
        }) {
            post("verify", VerifierApiDocs.getVerifyDocs()) {
                val authorizeBaseUrl = call.request.header("authorizeBaseUrl") ?: defaultAuthorizeBaseUrl
                val responseMode =
                    call.request.header("responseMode")?.let { ResponseMode.fromString(it) }
                        ?: ResponseMode.direct_post
                val successRedirectUri = call.request.header("successRedirectUri")
                val errorRedirectUri = call.request.header("errorRedirectUri")
                val statusCallbackUri = call.request.header("statusCallbackUri")
                val statusCallbackApiKey = call.request.header("statusCallbackApiKey")
                val stateId = call.request.header("stateId")
                val openId4VPProfileHeaderParam = call.request.header("openId4VPProfile")
                // Parse session TTL from header if provided
                val sessionTtl = call.request.header("sessionTtl")?.toLongOrNull()?.seconds

                val body = call.receive<JsonObject>()

                val session = VerifierService.createSession(
                    vpPoliciesJson = body["vp_policies"],
                    vcPoliciesJson = body["vc_policies"],
                    requestCredentialsJson = body["request_credentials"]
                        ?: throw BadRequestException("Field request_credentials is required"),
                    responseMode = responseMode,
                    successRedirectUri = successRedirectUri,
                    errorRedirectUri = errorRedirectUri,
                    statusCallbackUri = statusCallbackUri,
                    statusCallbackApiKey = statusCallbackApiKey,
                    stateId = stateId,
                    openId4VPProfile = (body["openid_profile"]?.jsonPrimitive?.content
                        ?: openId4VPProfileHeaderParam)?.let { OpenId4VPProfile.valueOf(it.uppercase()) }
                        ?: OpenId4VPProfile.fromAuthorizeBaseURL(authorizeBaseUrl),
                    trustedRootCAs = body["trusted_root_cas"]?.jsonArray,
                    sessionTtl = sessionTtl
                )

                call.respond(
                    authorizeBaseUrl.plus("?").plus(
                        when (session.openId4VPProfile) {
                            OpenId4VPProfile.ISO_18013_7_MDOC -> session.authorizationRequest!!.toRequestObjectByReferenceHttpQueryString(
                                ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/request/${session.id}" })

                            OpenId4VPProfile.EBSIV3 -> session.authorizationRequest!!.toEbsiRequestObjectByReferenceHttpQueryString(
                                SERVER_URL.let { "$it/openid4vc/request/${session.id}" })

                            else -> session.authorizationRequest!!.toHttpQueryString()
                        }
                    )
                )
            }

            post("/verify/{state}", VerifierApiDocs.getVerifyStateDocs()) {
                logger.trace { "POST verify/state" }

                val sessionId = call.parameters.getOrFail("state")
                logger.trace { "State: $sessionId" }

                VerifierService.verify(
                    sessionId = sessionId,
                    tokenResponseParameters = call.request.call.receiveParameters().toMap()
                ).also {
                    VerifierService.notifySubscribers(sessionId)
                }.onSuccess {
                    val session = VerifierService.getSession(sessionId)
                    if (session.walletInitiatedAuthState != null) {
                        val state = session.walletInitiatedAuthState
                        val code = Uuid.random().toString()
                        call.respondRedirect("openid://?code=$code&state=$state")
                    } else {
                        if(it.isNotBlank()) {
                            call.respond(
                                status = HttpStatusCode.OK,
                                message = mapOf("redirect_uri" to it),
                            )
                        } else {
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }.onFailure {
                    logger.debug(it) { "Verification failed ($it)" }
                    val errorDescription = it.message ?: "Verification failed"
                    logger.error { "Error: $errorDescription" }
                    val session = VerifierService.getSession(sessionId)
                    when {
                        session.walletInitiatedAuthState != null -> {
                            val state = session.walletInitiatedAuthState
                            call.respondRedirect(
                                "openid://?state=$state&error=invalid_request&error_description=${
                                    getErrorDescription(
                                        it
                                    )
                                }"
                            )
                        }

                        it is VerifierService.FailedVerificationException && it.redirectUrl != null -> {
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = mapOf("error_uri" to it.redirectUrl),
                            )
                        }

                        else -> {
                            throw it
                        }
                    }
                }
            }

            get("/session/{id}", VerifierApiDocs.getSessionDocs()) {
                val id = call.parameters.getOrFail("id")
                VerifierService.getResult(id).getOrThrow().let {
                    call.respond(HttpStatusCode.OK, it)
                }
            }

            get(
                path = "/session/{id}/presented-credentials",
                builder = PresentedCredentialsDocs.getPresentedCredentialsDocs()
            ) {
                val id = call.parameters.getOrFail("id")
                val viewMode = call.queryParameters["viewMode"]?.let {
                    PresentedCredentialsViewMode.valueOf(it)
                } ?: PresentedCredentialsViewMode.simple
                VerifierService
                    .getSessionPresentedCredentials(
                        sessionId = id,
                        viewMode = viewMode,
                    )
                    .onSuccess {
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = it,
                        )
                    }
                    .onFailure { ex ->
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = ex.stackTraceToString(),
                        )
                    }
            }

            get("/pd/{id}", VerifierApiDocs.getPdDocs()) {
                val id = call.parameters["id"]

                VerifierService.getPresentationDefinition(id ?: "").onSuccess {
                    call.respond(it.toJSON())
                }.onFailure {
                    throw NotFoundException("Presentation definition not found for id: $id")
                }
            }

            get("policy-list", VerifierApiDocs.getPolicyListDocs()) {
                call.respond(PolicyManager.listPolicyDescriptions())
            }

            get("/request/{id}", VerifierApiDocs.getRequestDocs()) {
                val id = call.parameters.getOrFail("id")
                VerifierService.getSignedAuthorizationRequestObject(id).onSuccess {
                    call.respondText(it, ContentType.parse("application/oauth-authz-req+jwt"), HttpStatusCode.OK)
                }.onFailure {
                    logger.debug(it) { "Cannot view request session ($it)" }
                    throw NotFoundException(it.message)
                }
            }
        }

        get("/.well-known/openid-configuration", { tags = listOf("Ebsi") }) {
            val metadata = buildJsonObject {
                put("authorization_endpoint", "$SERVER_URL/authorize")
                put("token_endpoint", "$SERVER_URL/token")
                put("issuer", SERVER_URL)
                put("jwks_uri", "$SERVER_URL/jwks")
                put("response_types_supported", buildJsonArray {
                    add(ResponseType.Code.value)
                    add(ResponseType.IdToken.value)
                    add(ResponseType.VpToken.value)
                })
                put("subject_types_supported", buildJsonArray { add("public") })
                put("id_token_signing_alg_values_supported", buildJsonArray { add("ES256") })
            }
            call.respond(metadata)
        }

        get("/jwks", { tags = listOf("Ebsi") }) {
            val jwks = buildJsonObject {
                put("keys", buildJsonArray {
                    val jwkWithKid = buildJsonObject {
                        RequestSigningCryptoProvider.signingKey.toPublicJWK().toJSONObject().forEach {
                            put(it.key, it.value.toJsonElement())
                        }
                        put("kid", RequestSigningCryptoProvider.signingKey.keyID)
                    }
                    add(jwkWithKid)
                })
            }

            call.respond(HttpStatusCode.OK, jwks)
        }

        get("authorize", {
            tags = listOf("Ebsi")
            description =
                "Authorize endpoint of OAuth Server as defined in EBSI Conformance Testing specifications. \nResponse is a 302 redirect with VP_TOKEN or ID_TOKEN request. \n" +
                        "Use the /oidc4vp/verify endpoint using the header openId4VPProfile to get an EBSI-compliant VP_TOKEN request without redirects."
        })
        {
            val params = call.parameters.toMap().toJsonObject()

            val walletInitiatedAuthState = params["state"]?.jsonArray?.get(0)?.jsonPrimitive?.content
            val scope = params["scope"]?.jsonArray.toString().replace("\"", "").replace("[", "").replace("]", "")

            val stateId = Uuid.random().toString()
            // Parse session TTL from query parameter if provided
            val sessionTtl =
                params["sessionTtl"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.seconds

            val session = VerifierService.createSession(
                vpPoliciesJson = null,
                vcPoliciesJson = buildJsonArray {
                    add("signature")
                    add("expired")
                    add("not-before")
                    add("revoked-status-list")
                },
                presentationDefinitionJson = when (scope.contains("openid ver_test:vp_token")) {
                    true -> Json.parseToJsonElement(fixedPresentationDefinitionForEbsiConformanceTest).jsonObject
                    else -> null
                } ?: throw IllegalArgumentException(""),
                responseMode = ResponseMode.direct_post,
                successRedirectUri = null,
                errorRedirectUri = null,
                statusCallbackUri = null,
                statusCallbackApiKey = null,
                stateId = stateId,
                walletInitiatedAuthState = walletInitiatedAuthState,
                responseType = when (scope.contains("openid ver_test:id_token")) {
                    true -> ResponseType.IdToken
                    else -> ResponseType.VpToken
                },
                openId4VPProfile = OpenId4VPProfile.EBSIV3,
                sessionTtl = sessionTtl
            )
            call.respondRedirect(
                "openid://?${
                    session.authorizationRequest!!.toEbsiRequestObjectByReferenceHttpQueryString(
                        SERVER_URL.let { "$it/openid4vc/request/${session.id}" })
                }"
            )
        }
    }
}

private fun getErrorDescription(it: Throwable): String? = when (it.message) {
    "Verification policies did not succeed: expired" ->
        "<\$presentation_submission.descriptor_map[x].id> is expired"

    "Verification policies did not succeed: not-before" ->
        "<\$presentation_submission.descriptor_map[x].id> is not yet valid"

    "Verification policies did not succeed: revoked-status-list" ->
        "<\$presentation_submission.descriptor_map[x].id> is revoked"

    else -> null
}
