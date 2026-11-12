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
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.AllowedIssuerPolicy
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class IssuerAuthorizationHttpTest {
    @Test
    fun `HTTP presentations enforce outer issuer allowlists and default assertion authorization`() {
        E2ETest("127.0.0.1", 0, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = { ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(
                urlPrefix = "http://127.0.0.1/verification-session", urlHost = "openid4vp://authorize",
            )) },
            init = { DidService.minimalInit() },
            module = Application::verifierModule,
        ) {
            useBoundVerifierAddress()
            val http = testHttpClient()
            val issuerDid = "did:example:http-policy-issuer"
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
                for (case in listOf("allowed", "disallowed", "authentication-only")) testAndReturn(case) {
                    val policies = if (case == "authentication-only") Verification2Session.DefinedVerificationPolicies()
                    else Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(listOf(
                        CredentialSignaturePolicy(), AllowedIssuerPolicy(JsonPrimitive(if (case == "allowed") issuerDid else "did:example:trusted")),
                    )))
                    val created = http.post("/verification-session/create") { setBody<VerificationSessionSetup>(CrossDeviceFlowSetup(core = GeneralFlowConfig(
                        dcqlQuery = DcqlQuery(listOf(CredentialQuery("credential", CredentialFormat.JWT_VC_JSON,
                            meta = JwtVcJsonMeta(typeValues = listOf(listOf("VerifiableCredential")))))),
                        policies = policies,
                    ))) }.body<VerificationSessionCreationResponse>()
                    val uri = Url(requireNotNull(created.bootstrapAuthorizationRequestUrl).toString()).parameters["request_uri"]!!
                    val request = http.get(uri).body<JsonObject>()
                    val now = Clock.System.now().epochSeconds
                    val vc = buildJsonObject {
                        put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
                        put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                        put("credentialSubject", buildJsonObject { put("id", holderDid) })
                    }
                    val credential = (if (case == "authentication-only") authentication else assertion).signJws(buildJsonObject {
                        put("iss", issuerDid); put("sub", holderDid); put("nbf", now - 10); put("exp", now + 3600); put("vc", vc)
                    }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("JWT"),
                        "kid" to JsonPrimitive("$issuerDid#${if (case == "authentication-only") "authentication" else "assertion"}")))
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
                    assertEquals(if (case == "allowed") HttpStatusCode.OK else HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                    val session = http.get("/verification-session/${created.sessionId}/info").body<Verification2Session>()
                    assertEquals(if (case == "allowed") Verification2Session.VerificationSessionStatus.SUCCESSFUL else Verification2Session.VerificationSessionStatus.FAILED, session.status)
                    assertEquals(case == "allowed", assertNotNull(session.policyResults).overallSuccess)
                }
            } finally {
                if (previous == null) DidService.resolverMethods.remove("example") else DidService.registerResolverForMethod("example", previous)
            }
        }
    }
}
