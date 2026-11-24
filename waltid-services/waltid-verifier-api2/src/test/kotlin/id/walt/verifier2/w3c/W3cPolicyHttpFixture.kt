@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.w3c

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.SameDeviceFlowSetup
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.events.Verifier2WebhookRecorder
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.client.plugins.sse.sseSession
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.useBoundVerifierAddress
import id.walt.verifier2.verifierModule
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.server.application.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

internal const val HTTP_POLICY_ISSUER_DID = "did:example:http-policy-issuer"

internal data class W3cPolicyHttpCase(
    val name: String,
    val accepted: Boolean,
    val policies: Verification2Session.DefinedVerificationPolicies = Verification2Session.DefinedVerificationPolicies(),
    val signingKeyId: String = "assertion",
    val notBeforeOffset: Long? = -10,
    val expirationOffset: Long? = 3600,
    val sameDevice: Boolean = false,
)

/** Runs real service startup and signed wallet HTTP presentations against the configured policies. */
internal fun runW3cPolicyHttpCases(cases: List<W3cPolicyHttpCase>, port: Int = 0, webhook: Verifier2WebhookRecorder? = null) {
    E2ETest("127.0.0.1", port, true).testBlock(
        features = listOf(OSSVerifier2FeatureCatalog),
        preload = { ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(
            urlPrefix = "http://127.0.0.1/verification-session", urlHost = "openid4vp://authorize",
        )) },
        init = { DidService.minimalInit() },
        module = Application::verifierModule,
    ) {
        useBoundVerifierAddress()
        val http = testHttpClient()
        val issuerDid = HTTP_POLICY_ISSUER_DID
        val assertion = JWKKey.generate(KeyType.secp256r1)
        val authentication = JWKKey.generate(KeyType.secp256r1)
        val holder = JWKKey.generate(KeyType.secp256r1)
        val holderDid = DidService.registerByKey("jwk", holder).did
        val document = buildJsonObject {
            put("id", issuerDid)
            put("verificationMethod", JsonArray(listOf("assertion" to assertion, "authentication" to authentication).map { (id, key) ->
                buildJsonObject {
                    put("id", "$issuerDid#$id"); put("type", "JsonWebKey2020"); put("controller", issuerDid)
                    put("publicKeyJwk", key.getPublicKey().exportJWKObject())
                }
            }))
            put("assertionMethod", JsonArray(listOf(JsonPrimitive("$issuerDid#assertion"))))
            put("authentication", JsonArray(listOf(JsonPrimitive("$issuerDid#authentication"))))
        }
        val previous = DidService.resolverMethods["example"]
        DidService.registerResolverForMethod("example", object : DidResolver {
            override val name = "http-policy-issuer"
            override suspend fun getSupportedMethods() = Result.success(setOf("example"))
            override suspend fun resolve(did: String) = Result.success(document)
            @Suppress("OVERRIDE_DEPRECATION")
            override suspend fun resolveToKey(did: String): Result<Key> = error("Legacy resolution must not be used")
            @Suppress("OVERRIDE_DEPRECATION")
            override suspend fun resolveToKeys(did: String): Result<Set<Key>> = error("Legacy resolution must not be used")
        })
        try {
            for (case in cases) testAndReturn(case.name) {
                val core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(listOf(CredentialQuery("credential", CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(typeValues = listOf(listOf("VerifiableCredential")))))),
                    policies = case.policies, notifications = webhook?.notifications(),
                )
                val setup: VerificationSessionSetup = if (case.sameDevice) SameDeviceFlowSetup(core,
                    redirects = Verification2Session.VerificationSessionRedirects(successRedirectUri = Url("https://example.com/success")))
                    else CrossDeviceFlowSetup(core = core)
                val created = http.post("/verification-session/create") { setBody(setup) }.body<VerificationSessionCreationResponse>()
                if (case.sameDevice) SseNotifier.getSseFlow(created.sessionId)
                val uri = Url(requireNotNull(created.bootstrapAuthorizationRequestUrl).toString()).parameters["request_uri"]!!
                val request = http.get(uri).body<JsonObject>()
                val now = Clock.System.now().epochSeconds
                val vc = buildJsonObject {
                    put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                    put("credentialSubject", buildJsonObject { put("id", holderDid) })
                }
                val credential = (if (case.signingKeyId == "authentication") authentication else assertion).signJws(buildJsonObject {
                    put("iss", issuerDid); put("sub", holderDid); put("vc", vc)
                    case.notBeforeOffset?.let { put("nbf", now + it) }
                    case.expirationOffset?.let { put("exp", now + it) }
                }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("JWT"),
                    "kid" to JsonPrimitive("$issuerDid#${case.signingKeyId}")))
                val vp = holder.signJws(buildJsonObject {
                    put("iss", holderDid); put("sub", holderDid); put("aud", request.getValue("client_id"))
                    put("nonce", request.getValue("nonce")); put("iat", now); put("exp", now + 300)
                    put("vp", buildJsonObject {
                        put("@context", vc.getValue("@context")); put("type", JsonArray(listOf(JsonPrimitive("VerifiablePresentation"))))
                        put("holder", holderDid); put("verifiableCredential", JsonArray(listOf(JsonPrimitive(credential))))
                    })
                }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("JWT"), "kid" to JsonPrimitive("$holderDid#0")))
                val response = http.submitForm(request.getValue("response_uri").jsonPrimitive.content, Parameters.build {
                    append("state", request.getValue("state").jsonPrimitive.content)
                    append("vp_token", buildJsonObject { put("credential", JsonArray(listOf(JsonPrimitive(vp)))) }.toString())
                })
                assertEquals(if (case.accepted) HttpStatusCode.OK else HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                val sessionJson = http.get("/verification-session/${created.sessionId}/info").body<JsonObject>()
                val session = Json.decodeFromJsonElement<Verification2Session>(sessionJson)
                assertEquals(if (case.accepted) Verification2Session.VerificationSessionStatus.SUCCESSFUL else Verification2Session.VerificationSessionStatus.FAILED, session.status)
                assertEquals(case.accepted, assertNotNull(session.policyResults).overallSuccess)
                if (case.sameDevice && !case.accepted) {
                    assertIs<SessionFailure.VcPolicyViolations>(session.failure)
                    assertNotNull(session.statusReason)
                    assertNull(session.presentedCredentials)
                    assertNull(session.presentedRawData)
                    assertNull(session.responseCode)
                    assertEquals(sessionJson,
                        http.get("/verification-session/${created.sessionId}/info?response_code=wrong").body<JsonObject>())
                    assertFalse(vp in Json.encodeToString(session))
                    assertFalse(credential in Json.encodeToString(session))
                    webhook?.let { recorder ->
                        val terminal = recorder.received().last { it.target == created.sessionId && it.event == SessionEvent.credential_policy_results_available.name }
                        val notified = Json.decodeFromJsonElement<Verification2Session>(terminal.session)
                        assertEquals(session.statusReason, notified.statusReason)
                        assertEquals(Json.encodeToJsonElement(session.failure), Json.encodeToJsonElement(notified.failure))
                        assertEquals(Json.encodeToJsonElement(session.policyResults), Json.encodeToJsonElement(notified.policyResults))
                        assertEquals(Json.encodeToJsonElement(session.presentationValidationResults), Json.encodeToJsonElement(notified.presentationValidationResults))
                        assertNull(notified.presentedCredentials)
                        assertNull(notified.presentedRawData)
                        assertNull(notified.responseCode)
                        val eventsClient = http.config { install(ClientSSE) }
                        try {
                            val streamed = withTimeout(5.seconds) {
                                val stream = eventsClient.sseSession("/verification-session/${created.sessionId}/events")
                                try {
                                    stream.incoming.filter { it.data != null && it.data != "{}" }
                                        .map { Json.decodeFromString<KtorSessionUpdate>(requireNotNull(it.data)) }
                                        .first { it.event == SessionEvent.credential_policy_results_available.name }
                                } finally { stream.cancel() }
                            }
                            assertEquals(terminal, streamed)
                        } finally { eventsClient.close() }
                    }
                }
            }
        } finally {
            if (previous == null) DidService.resolverMethods.remove("example") else DidService.registerResolverForMethod("example", previous)
        }
    }
}
