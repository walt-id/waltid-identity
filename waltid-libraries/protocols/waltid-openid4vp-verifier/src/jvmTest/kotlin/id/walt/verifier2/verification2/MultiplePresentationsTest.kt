package id.walt.verifier2.verification2

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * A DCQL query with `multiple: true` answered with two presentations (WAL-1582 / WAL-1588): every
 * presentation counts, not only the last one.
 */
class MultiplePresentationsTest {

    private val nonce = "nonce-1582"
    private val clientId = "redirect_uri:https://verifier.example/response"

    private fun session() = Json.decodeFromString<Verification2Session>(
        // language=JSON
        """
        {
          "id": "session-1582",
          "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": {"credentials": [
            {"id": "ids", "format": "jwt_vc_json", "multiple": true, "meta": {"type_values": [["VerifiedID"]]}}
          ]}}},
          "creationDate": "2026-10-19T08:00:00Z",
          "status": "IN_USE",
          "authorizationRequestUrl": "openid4vp://authorize",
          "requestMode": "REQUEST_URI_SIGNED",
          "authorizationRequest": {
            "response_type": "vp_token", "client_id": "$clientId", "response_mode": "direct_post",
            "response_uri": "https://verifier.example/response", "nonce": "$nonce",
            "client_metadata": {"vp_formats_supported": {"jwt_vc_json": {}}},
            "dcql_query": {"credentials": [
              {"id": "ids", "format": "jwt_vc_json", "multiple": true, "meta": {"type_values": [["VerifiedID"]]}}
            ]}
          },
          "policies": {
            "vp_policies": {"jwt_vc_json": ["jwt_vc_json/audience-check", "jwt_vc_json/nonce-check"]},
            "vc_policies": [{"policy": "expiration"}]
          }
        }
        """.trimIndent()
    )

    private lateinit var issuer: JWKKey
    private lateinit var holder: JWKKey

    @BeforeTest
    fun generateKeys() = runTest {
        issuer = JWKKey.generate(KeyType.secp256r1)
        holder = JWKKey.generate(KeyType.secp256r1)
    }

    private suspend fun credential(name: String, expiresInDays: Int): String = issuer.signJws(
        buildJsonObject {
            put("iss", "did:example:issuer"); put("sub", "did:example:holder"); put("jti", "urn:uuid:$name")
            put("nbf", Clock.System.now().minus(10.days).epochSeconds)
            put("exp", Clock.System.now().plus(expiresInDays.days).epochSeconds)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential"); add("VerifiedID") }
                put("expirationDate", Clock.System.now().plus(expiresInDays.days).toString())
                putJsonObject("credentialSubject") { put("id", "did:example:holder"); put("name", name) }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )

    private suspend fun presentation(credential: String, presentationNonce: String = nonce): String = holder.signJws(
        buildJsonObject {
            put("iss", "did:example:holder"); put("aud", clientId); put("nonce", presentationNonce)
            putJsonObject("vp") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiablePresentation") }
                putJsonArray("verifiableCredential") { add(credential) }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )

    private suspend fun verify(vararg presentations: String): Verification2Session {
        val session = session()
        runCatching {
            PresentationVerificationEngine.executeAllVerification(
                vpTokenContents = mapOf("ids" to presentations.toList()),
                session = session,
                updateSessionCallback = { s, _, block -> s.block() },
                failSessionCallback = { s, event, update -> update(s, event) { status = Verification2Session.VerificationSessionStatus.FAILED } },
            )
        }.exceptionOrNull()?.let { assertIs<PresentationRejectionException>(it) }
        return session
    }

    @Test
    fun `an expired first credential fails the session`() = runTest {
        val session = verify(presentation(credential("first", expiresInDays = -1)), presentation(credential("second", expiresInDays = 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        assertEquals(listOf("first", "second"), session.presentedCredentials!!.getValue("ids").map {
            (it.credentialData["vc"]?.jsonObject ?: it.credentialData)["credentialSubject"]!!.jsonObject["name"]!!.jsonPrimitive.content
        })
        val expiration = session.policyResults!!.vcPolicies.filter { it.policy.id == "expiration" }
        assertEquals(listOf(false, true), expiration.sortedBy { it.credentialIndex }.map { it.success })
    }

    @Test
    fun `every presentation is kept, and a VP policy failing for the first one is not lost`() = runTest {
        val session = verify(presentation(credential("first", 30), presentationNonce = "replayed"), presentation(credential("second", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        assertEquals(2, session.presentedPresentations!!.getValue("ids").size)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertFalse(nonceCheck.success)
        assertEquals(1, nonceCheck.errors.size)
        assertEquals(listOf(0), nonceCheck.failedPresentationIndexes())
    }

    @Test
    fun `a VP policy failing for several presentations reports every one of them`() = runTest {
        val session = verify(
            presentation(credential("first", 30), presentationNonce = "replayed"),
            presentation(credential("second", 30)),
            presentation(credential("third", 30), presentationNonce = "replayed"),
        )

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertEquals(2, nonceCheck.errors.size)
        assertEquals(listOf(0, 2), nonceCheck.failedPresentationIndexes())
        assertEquals(3, nonceCheck.multiplePresentations().getValue("count").jsonPrimitive.int)
    }

    @Test
    fun `two valid credentials pass, and both are verified`() = runTest {
        val session = verify(presentation(credential("first", 30)), presentation(credential("second", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
        assertEquals(2, session.presentedCredentials!!.getValue("ids").size)
        assertEquals(2, session.policyResults!!.vcPolicies.count { it.policy.id == "expiration" && it.success })
        assertEquals(2, session.presentedPresentations!!.getValue("ids").size)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertTrue(nonceCheck.success)
        assertEquals(emptyList(), nonceCheck.failedPresentationIndexes())
    }

    @Test
    fun `a single presentation keeps its VP policy results unchanged`() = runTest {
        val session = verify(presentation(credential("only", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
        session.presentationValidationResults!!.getValue("ids").values.forEach {
            assertFalse(PresentationVerificationEngine.MULTIPLE_PRESENTATIONS_RESULT in it.results)
        }
    }

    private fun VPPolicy2.PolicyRunResult.multiplePresentations() =
        results.getValue(PresentationVerificationEngine.MULTIPLE_PRESENTATIONS_RESULT).jsonObject

    private fun VPPolicy2.PolicyRunResult.failedPresentationIndexes() =
        multiplePresentations().getValue("failed_indexes").jsonArray.map { it.jsonPrimitive.int }
}
