package id.walt.verifier

import COSE.AlgorithmID
import COSE.OneKey
import cbor.Cbor
import com.auth0.jwk.Jwk
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.credentials.verification.PolicyManager
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKeyCreator
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.toDE
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.*
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.LspPotentialInteropEvent
import id.walt.verifier.oidc.VerificationUseCase
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Serializable
data class DescriptorMappingFormParam(val id: String, val format: VCFormat, val path: String)

@Serializable
data class PresentationSubmissionFormParam(
    val id: String, val definition_id: String, val descriptor_map: List<DescriptorMappingFormParam>
)

@Serializable
data class TokenResponseFormParam(
    val vp_token: JsonElement,
    val presentation_submission: PresentationSubmissionFormParam
)

data class LSPPotentialIssueFormDataParam(
    val jwk: JsonObject
)

@Serializable
data class CredentialVerificationRequest(
    @SerialName("vp_policies")
    val vpPolicies: List<JsonElement>,

    @SerialName("vc_policies")
    val vcPolicies: List<JsonElement>,

    @SerialName("request_credentials")
    val requestCredentials: List<JsonElement>
)

const val defaultAuthorizeBaseUrl = "openid4vp://authorize"

private val prettyJson = Json { prettyPrint = true }
private val httpClient = HttpClient() {
    install(ContentNegotiation) {
        json()
    }
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
    }
}

val verifiableIdPresentationDefinitionExample = JsonObject(
    mapOf(
        "policies" to JsonArray(listOf(JsonPrimitive("signature"))),
        "presentation_definition" to
                PresentationDefinition(
                    "<automatically assigned>", listOf(
                        InputDescriptor(
                            "VerifiableId",
                            format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition(alg = setOf("EdDSA"))),
                            constraints = InputDescriptorConstraints(
                                fields = listOf(InputDescriptorField(path = listOf("$.type"), filter = buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("pattern", JsonPrimitive("VerifiableId"))
                                }))
                            )
                        )
                    )
                ).toJSON(),
    )
).let { prettyJson.encodeToString(it) }

private val verificationUseCase = VerificationUseCase(httpClient, SimpleJWTCryptoProvider(JWSAlgorithm.EdDSA, null, null))


fun Application.verfierApi() {
    routing {

        route("openid4vc", {
        }) {
            post("verify", {
                tags = listOf("Credential Verification")
                summary = "Initialize OIDC presentation session"
                description =
                    "Initializes an OIDC presentation session, with the given presentation definition and parameters. The URL returned can be rendered as QR code for the holder wallet to scan, or called directly on the holder if the wallet base URL is given."
                request {
                    headerParameter<String>("authorizeBaseUrl") {
                        description = "Base URL of wallet authorize endpoint, defaults to: $defaultAuthorizeBaseUrl"
                        example = defaultAuthorizeBaseUrl
                        required = false
                    }
                    headerParameter<ResponseMode>("responseMode") {
                        description = "Response mode, for vp_token response, defaults to ${ResponseMode.direct_post}"
                        example = ResponseMode.direct_post.name
                        required = false
                    }
                    headerParameter<String>("successRedirectUri") {
                        description = "Redirect URI to return when all policies passed. \"\$id\" will be replaced with the session id."
                        example = ""
                        required = false
                    }
                    headerParameter<String>("errorRedirectUri") {
                        description = "Redirect URI to return when a policy failed. \"\$id\" will be replaced with the session id."
                        example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackUri") {
                        description = "Callback to push state changes of the presentation process to"
                        example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackApiKey") {
                        description = ""
                        example = ""
                        required = false
                    }
                    headerParameter<String>("stateId") {
                        description = ""
                        example = ""
                        required = false
                    }
                    body<JsonObject> {
                        description =
                            "Presentation definition, describing the presentation requirement for this verification session. ID of the presentation definition is automatically assigned randomly."
                        //example("Verifiable ID example", verifiableIdPresentationDefinitionExample)
                        example("Minimal example", VerifierApiExamples.minimal)
                        example("Example with VP policies", VerifierApiExamples.vpPolicies)
                        example("Example with VP & global VC policies", VerifierApiExamples.vpGlobalVcPolicies)
                        example("Example with VP, VC & specific credential policies", VerifierApiExamples.vcVpIndividualPolicies)
                        example(
                            "Example with VP, VC & specific policies, and explicit presentation_definition  (maximum example)",
                            VerifierApiExamples.maxExample
                        )
                        example("Example with presentation definition policy", VerifierApiExamples.presentationDefinitionPolicy)
                    }
                }
            }) {
                val authorizeBaseUrl = context.request.header("authorizeBaseUrl") ?: defaultAuthorizeBaseUrl
                val responseMode =
                    context.request.header("responseMode")?.let { ResponseMode.valueOf(it) } ?: ResponseMode.direct_post
                val successRedirectUri = context.request.header("successRedirectUri")
                val errorRedirectUri = context.request.header("errorRedirectUri")
                val statusCallbackUri = context.request.header("statusCallbackUri")
                val statusCallbackApiKey = context.request.header("statusCallbackApiKey")
                val stateId = context.request.header("stateId")
                val body = context.receive<JsonObject>()

                val session = verificationUseCase.createSession(
                    vpPoliciesJson = body["vp_policies"],
                    vcPoliciesJson = body["vc_policies"],
                    requestCredentialsJson = body["request_credentials"]!!,
                    presentationDefinitionJson = body["presentation_definition"],
                    responseMode = responseMode,
                    successRedirectUri = successRedirectUri,
                    errorRedirectUri = errorRedirectUri,
                    statusCallbackUri = statusCallbackUri,
                    statusCallbackApiKey = statusCallbackApiKey,
                    stateId = stateId,
                    authorizeBaseUrl = authorizeBaseUrl
                )

                context.respond(authorizeBaseUrl.plus("?").plus(
                    when(session.authorizationRequest!!.clientIdScheme) {
                        ClientIdScheme.X509SanDns -> session.authorizationRequest!!.toRequestObjectByReferenceHttpQueryString(ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/request/${session.id}" })
                        else -> session.authorizationRequest!!.toHttpQueryString()
                    })
                )
            }
            post("/verify/{state}", {
                tags = listOf("OIDC")
                summary = "Verify vp_token response, for a verification request identified by the state"
                description =
                    "Called in direct_post response mode by the SIOP provider (holder wallet) with the verifiable presentation in the vp_token and the presentation_submission parameter, describing the submitted presentation. The presentation session is identified by the given state parameter."
                request {
                    pathParameter<String>("state") {
                        description =
                            "State, i.e. session ID, identifying the presentation session, this response belongs to."
                        required = true
                    }
                    body<TokenResponseFormParam> {
                        mediaType(ContentType.Application.FormUrlEncoded)
                        example(
                            "simple vp_token response", TokenResponseFormParam(
                                JsonPrimitive("abc.def.ghi"), PresentationSubmissionFormParam(
                                    "1", "1", listOf(
                                        DescriptorMappingFormParam("1", VCFormat.jwt_vc_json, "$.type")
                                    )
                                )
                            )
                        )
                    }
                }
            }) {
                val sessionId = call.parameters["state"]
                verificationUseCase.verify(sessionId, context.request.call.receiveParameters().toMap())
                    .onSuccess {
                        call.respond(HttpStatusCode.OK, it)
                    }.onFailure {
                        call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                    }.also {
                        sessionId?.run { verificationUseCase.notifySubscribers(this) }
                    }
            }
            get("/session/{id}", {
                tags = listOf("Credential Verification")
                summary = "Get info about OIDC presentation session, that was previously initialized"
                description =
                    "Session info, containing current state and result information about an ongoing OIDC presentation session"
                request {
                    pathParameter<String>("id") {
                        description = "Session ID"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Session info"
                    }
                }
            }) {
                val id = call.parameters.getOrFail("id")
                verificationUseCase.getResult(id).onSuccess {
                    call.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            get("/pd/{id}", {
                tags = listOf("OIDC")
                summary = "Get presentation definition object by ID"
                description =
                    "Gets a presentation definition object, previously uploaded during initialization of OIDC presentation session."
                request {
                    pathParameter<String>("id") {
                        description = "ID of presentation definition object to retrieve"
                        required = true
                    }
                }
            }) {
                val id = call.parameters["id"]

                verificationUseCase.getPresentationDefinition(id ?: "").onSuccess {
                    call.respond(it.toJSON())
                }.onFailure {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            get("policy-list", {
                tags = listOf("Credential Verification")
                summary = "List registered policies"
                response { HttpStatusCode.OK to { body<Map<String, String?>>() } }
            }) {
                call.respond(PolicyManager.listPolicyDescriptions())
            }
            get("/request/{id}", {
                tags = listOf("OIDC")
                summary = "Get request object for session by session id"
                description = "Gets the signed request object for the session given by the session id parameter"
                request {
                    pathParameter<String>("id") {
                        description = "ID of the presentation session"
                        required = true
                    }
                }
            }) {
                val id = call.parameters.getOrFail("id")
                verificationUseCase.getSignedAuthorizationRequestObject(id).onSuccess {
                    call.respondText(it, ContentType.parse("application/oauth-authz-req+jwt"), HttpStatusCode.OK)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
        }

        route("lsp-potential") {
            post("issueMdl", {
                tags = listOf("LSP POTENTIAL Interop Event")
                summary = "Issue MDL for given device key, using internal issuer keys"
                description = "Give device public key JWK in form body."
                request {
                    body<LSPPotentialIssueFormDataParam> {
                        mediaType(ContentType.Application.FormUrlEncoded)
                        example("jwk", LSPPotentialIssueFormDataParam(
                            Json.parseToJsonElement(ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toString().also {
                                println(it)
                            }).jsonObject
                        ))
                    }
                }
            }) {
                val deviceJwk = context.request.call.receiveParameters().toMap().get("jwk")
                val devicePubKey = JWK.parse(deviceJwk!!.first()).toECKey().toPublicKey()

                val mdoc = MDocBuilder("org.iso.18013.5.1.mDL")
                    .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDE())
                    .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDE())
                    .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
                    .sign(
                        ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365*24, DateTimeUnit.HOUR)),
                        DeviceKeyInfo(DataElement.fromCBOR(OneKey(devicePubKey, null).AsCBOR().EncodeToBytes())),
                        SimpleCOSECryptoProvider(listOf(
                        LspPotentialInteropEvent.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO)), LspPotentialInteropEvent.POTENTIAL_ISSUER_KEY_ID
                    )
                println("SIGNED MDOC (mDL):")
                println(Cbor.encodeToHexString(mdoc))
                call.respond(mdoc.toCBORHex())
            }
        }
    }
}



