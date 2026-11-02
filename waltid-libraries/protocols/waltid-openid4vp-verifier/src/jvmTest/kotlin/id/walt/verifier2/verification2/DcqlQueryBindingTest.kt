package id.walt.verifier2.verification2

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier2.data.DcqlFulfillmentFailure
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.UnsatisfiedSet
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.FAILED
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.SUCCESSFUL
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Every presented credential has to satisfy the Credential Query whose id it is returned under
 * (OpenID4VP 1.0 §8.1, §8.6), and the vp_token has to have the shape the query allows.
 */
class DcqlQueryBindingTest {

    private val nonce = "nonce-binding"
    private val clientId = "redirect_uri:https://verifier.example/response"

    private fun session(dcqlQuery: String) = Json.decodeFromString<Verification2Session>(
        // language=JSON
        """
        {
          "id": "session-binding",
          "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": $dcqlQuery}},
          "creationDate": "2026-11-02T08:00:00Z",
          "status": "IN_USE",
          "authorizationRequestUrl": "openid4vp://authorize",
          "requestMode": "REQUEST_URI_SIGNED",
          "authorizationRequest": {
            "response_type": "vp_token", "client_id": "$clientId", "response_mode": "direct_post",
            "response_uri": "https://verifier.example/response", "nonce": "$nonce",
            "client_metadata": {"vp_formats_supported": {"jwt_vc_json": {}}},
            "dcql_query": $dcqlQuery
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

    private suspend fun credential(type: String, subject: JsonObjectBuilder.() -> Unit = {}): String = issuer.signJws(
        buildJsonObject {
            put("iss", "did:example:issuer"); put("sub", "did:example:holder"); put("jti", "urn:uuid:$type")
            put("nbf", Clock.System.now().minus(10.days).epochSeconds)
            put("exp", Clock.System.now().plus(30.days).epochSeconds)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential"); add(type) }
                put("expirationDate", Clock.System.now().plus(30.days).toString())
                putJsonObject("credentialSubject") { put("id", "did:example:holder"); subject() }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )

    private suspend fun presentation(vararg credentials: String): String = holder.signJws(
        buildJsonObject {
            put("iss", "did:example:holder"); put("aud", clientId); put("nonce", nonce)
            putJsonObject("vp") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiablePresentation") }
                putJsonArray("verifiableCredential") { credentials.forEach { add(it) } }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )

    private suspend fun verify(dcqlQuery: String, vpToken: Map<String, List<String>>): Verification2Session {
        val session = session(dcqlQuery)
        runCatching {
            PresentationVerificationEngine.executeAllVerification(
                vpTokenContents = vpToken,
                session = session,
                updateSessionCallback = { s, _, block -> s.block() },
                failSessionCallback = { s, event, update -> update(s, event) { status = FAILED } },
            )
        }.exceptionOrNull()?.let { assertIs<PresentationRejectionException>(it) }
        return session
    }

    private fun Verification2Session.assertFailedWith(failure: kotlin.reflect.KClass<out SessionFailure>) {
        assertEquals(FAILED, status, "session must fail")
        assertTrue(failure.isInstance(this.failure), "expected ${failure.simpleName}, got ${this.failure}")
    }

    private val Verification2Session.dcqlFailure: DcqlFulfillmentFailure
        get() = assertIs<SessionFailure.DcqlFulfillment>(failure).failure

    // language=JSON
    private val administratorQuery = """
        {"credentials": [{"id": "admin", "format": "jwt_vc_json", "meta": {"type_values": [["AdministratorCredential"]]}}]}
    """.trimIndent()

    @Test
    fun `a credential of another type under a query id is rejected`() = runTest {
        val session = verify(administratorQuery, mapOf("admin" to listOf(presentation(credential("MembershipCredential")))))

        session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
        assertEquals(setOf("admin"), session.dcqlFailure.missingQueryIds)
        assertNull(session.policyResults, "credential policies must not run for an unmatched credential")
    }

    @Test
    fun `a credential of the requested type passes`() = runTest {
        val session = verify(administratorQuery, mapOf("admin" to listOf(presentation(credential("AdministratorCredential")))))

        assertEquals(SUCCESSFUL, session.status, session.failure.toString())
    }

    @Test
    fun `a query id that was not requested is rejected`() = runTest {
        val session = verify(
            administratorQuery,
            mapOf(
                "admin" to listOf(presentation(credential("AdministratorCredential"))),
                "unrequested" to listOf(presentation(credential("MembershipCredential"))),
            ),
        )

        session.assertFailedWith(SessionFailure.PresentationValidation::class)
    }

    @Test
    fun `an empty presentation array is rejected`() = runTest {
        // language=JSON
        val query = """
            {
              "credentials": [
                {"id": "admin", "format": "jwt_vc_json", "meta": {"type_values": [["AdministratorCredential"]]}},
                {"id": "member", "format": "jwt_vc_json", "meta": {"type_values": [["MembershipCredential"]]}}
              ],
              "credential_sets": [{"options": [["admin"]]}, {"required": false, "options": [["member"]]}]
            }
        """.trimIndent()

        val session = verify(
            query,
            mapOf("admin" to listOf(presentation(credential("AdministratorCredential"))), "member" to emptyList()),
        )

        session.assertFailedWith(SessionFailure.PresentationValidation::class)
    }

    @Test
    fun `two presentations for a query without multiple are rejected`() = runTest {
        val session = verify(
            administratorQuery,
            mapOf(
                "admin" to listOf(
                    presentation(credential("AdministratorCredential")),
                    presentation(credential("MembershipCredential")),
                )
            ),
        )

        session.assertFailedWith(SessionFailure.PresentationValidation::class)
    }

    @Test
    fun `one presentation carrying two credentials for a query without multiple is rejected`() = runTest {
        val session = verify(
            administratorQuery,
            mapOf("admin" to listOf(presentation(credential("AdministratorCredential"), credential("AdministratorCredential")))),
        )

        session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
    }

    @Test
    fun `a presentation without a credential is rejected`() = runTest {
        val session = verify(administratorQuery, mapOf("admin" to listOf(presentation())))

        session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
    }

    @Test
    fun `a claim value outside the requested values is rejected`() = runTest {
        // language=JSON
        val query = """
            {"credentials": [{
              "id": "employee", "format": "jwt_vc_json", "meta": {"type_values": [["EmployeeCredential"]]},
              "claims": [{"path": ["credentialSubject", "role"], "values": ["administrator"]}]
            }]}
        """.trimIndent()

        val rejected = verify(query, mapOf("employee" to listOf(presentation(credential("EmployeeCredential") { put("role", "user") }))))
        rejected.assertFailedWith(SessionFailure.DcqlFulfillment::class)

        val accepted = verify(query, mapOf("employee" to listOf(presentation(credential("EmployeeCredential") { put("role", "administrator") }))))
        assertEquals(SUCCESSFUL, accepted.status, accepted.failure.toString())
    }

    @Test
    fun `a credential satisfying no claim set is rejected`() = runTest {
        // language=JSON
        val query = """
            {"credentials": [{
              "id": "employee", "format": "jwt_vc_json", "meta": {"type_values": [["EmployeeCredential"]]},
              "claims": [
                {"id": "staff", "path": ["credentialSubject", "staff_number"]},
                {"id": "contractor", "path": ["credentialSubject", "contractor_number"]}
              ],
              "claim_sets": [["staff"], ["contractor"]]
            }]}
        """.trimIndent()

        val rejected = verify(query, mapOf("employee" to listOf(presentation(credential("EmployeeCredential") { put("name", "x") }))))
        rejected.assertFailedWith(SessionFailure.DcqlFulfillment::class)

        val accepted = verify(query, mapOf("employee" to listOf(presentation(credential("EmployeeCredential") { put("contractor_number", "c-1") }))))
        assertEquals(SUCCESSFUL, accepted.status, accepted.failure.toString())
    }

    // language=JSON
    private val alternativesQuery = """
        {
          "credentials": [
            {"id": "admin", "format": "jwt_vc_json", "meta": {"type_values": [["AdministratorCredential"]]}},
            {"id": "member", "format": "jwt_vc_json", "meta": {"type_values": [["MembershipCredential"]]}}
          ],
          "credential_sets": [{"options": [["admin"], ["member"]]}]
        }
    """.trimIndent()

    @Test
    fun `a credential set option is satisfied only by credentials matching its queries`() = runTest {
        val relabelled = verify(alternativesQuery, mapOf("admin" to listOf(presentation(credential("MembershipCredential")))))
        relabelled.assertFailedWith(SessionFailure.DcqlFulfillment::class)
        assertEquals(setOf("admin"), relabelled.dcqlFailure.missingQueryIds)
        assertEquals(listOf(UnsatisfiedSet(listOf(listOf("admin"), listOf("member")))), relabelled.dcqlFailure.unsatisfiedSets)

        val matching = verify(alternativesQuery, mapOf("member" to listOf(presentation(credential("MembershipCredential")))))
        assertEquals(SUCCESSFUL, matching.status, matching.failure.toString())
    }
}
