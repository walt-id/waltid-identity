package id.walt.verifier2.sdjwt

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.ShaUtils.sha256
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.did.dids.DidService
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.freePort
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.serialization.json.*

class SdJwtVcValidityIntegrationTest {
    private data class Case(
        val name: String,
        val claim: String? = null,
        val offset: Long = 0,
        val disclosed: Boolean = false,
        val withheld: Boolean = false,
        val accepted: Boolean,
    )

    @Test
    fun `default startup policies enforce validity and reject reserved disclosures`() {
        val port = freePort()
        E2ETest("127.0.0.1", port, failEarly = true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(
                    urlPrefix = "http://127.0.0.1:$port/verification-session",
                    urlHost = "openid4vp://authorize",
                ))
            },
            init = { DidService.minimalInit() },
            module = Application::verifierModule,
        ) {
            val http = testHttpClient()
            val issuer = JWKKey.generate(KeyType.secp256r1)
            val holder = JWKKey.generate(KeyType.secp256r1)
            val issuerDid = DidService.registerByKey("jwk", issuer).did
            val issuerKid = DidService.resolveAuthenticationMethodId(issuerDid, issuer.getKeyId())
            val vct = "https://example.com/validity-test"
            val cases = listOf(
                Case("clear valid exp", "exp", 3600, accepted = true),
                Case("clear expired exp", "exp", -3600, accepted = false),
                Case("clear valid nbf", "nbf", -3600, accepted = true),
                Case("clear future nbf", "nbf", 3600, accepted = false),
                Case("disclosed expired exp", "exp", -3600, disclosed = true, accepted = false),
                Case("disclosed valid exp", "exp", 3600, disclosed = true, accepted = false),
                Case("disclosed future nbf", "nbf", 3600, disclosed = true, accepted = false),
                Case("disclosed valid nbf", "nbf", -3600, disclosed = true, accepted = false),
                Case("disclosed iat remains allowed", "iat", -3600, disclosed = true, accepted = true),
                Case("optional exp absent", accepted = true),
                Case("withheld optional exp is not inferred", "exp", -3600, disclosed = true, withheld = true, accepted = true),
            )
            for (case in cases) testAndReturn(case.name) {
                val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(listOf(CredentialQuery("c1", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(vctValues = listOf(vct))))),
                ))
                val created = http.post("/verification-session/create") { setBody(setup) }.body<VerificationSessionCreationResponse>()
                val requestUri = Url(requireNotNull(created.bootstrapAuthorizationRequestUrl).toString()).parameters["request_uri"]!!
                val request = http.get(requestUri).body<JsonObject>()
                val now = Clock.System.now().epochSeconds
                val givenName = SdJwtSelectiveDisclosure("name-salt", "given_name", JsonPrimitive("Alice"))
                val timeDisclosure = case.claim?.takeIf { case.disclosed }?.let {
                    SdJwtSelectiveDisclosure("time-salt", it, JsonPrimitive(now + case.offset))
                }
                val disclosures = listOfNotNull(givenName, timeDisclosure)
                val payload = buildJsonObject {
                    put("iss", issuerDid)
                    put("vct", vct)
                    put("cnf", buildJsonObject { put("jwk", holder.getPublicKey().exportJWKObject()) })
                    if (!case.disclosed) case.claim?.let { put(it, now + case.offset) }
                    put("_sd_alg", "sha-256")
                    put("_sd", JsonArray(disclosures.map { JsonPrimitive(it.asHashedFromEncoded()) }))
                }
                // Sign raw claims so the tests can exercise credentials that violate the issuer's profile.
                val jwt = issuer.signJws(payload.toString().encodeToByteArray(), mapOf(
                    "typ" to JsonPrimitive("dc+sd-jwt"), "kid" to JsonPrimitive(issuerKid),
                ))
                val presented = disclosures.filterNot { case.withheld && it == timeDisclosure }
                val hashable = (listOf(jwt) + presented.map { it.encoded }).joinToString("~", postfix = "~")
                val kbJwt = holder.signJws(buildJsonObject {
                    put("aud", request.getValue("client_id"))
                    put("nonce", request.getValue("nonce"))
                    put("iat", now)
                    put("sd_hash", sha256(hashable.encodeToByteArray()).encodeToBase64Url())
                }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("kb+jwt")))
                val response = http.submitForm(request.getValue("response_uri").jsonPrimitive.content, Parameters.build {
                    append("vp_token", buildJsonObject { put("c1", buildJsonArray { add(hashable + kbJwt) }) }.toString())
                    append("state", request.getValue("state").jsonPrimitive.content)
                })
                assertEquals(if (case.accepted) HttpStatusCode.OK else HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                val session = http.get("/verification-session/${created.sessionId}/info").body<Verification2Session>()
                assertEquals(
                    if (case.accepted) Verification2Session.VerificationSessionStatus.SUCCESSFUL else Verification2Session.VerificationSessionStatus.FAILED,
                    session.status,
                )
            }
        }
    }
}
