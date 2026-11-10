package id.walt.verifier2.verification2

import id.walt.credentials.CredentialParser
import id.walt.dcql.models.DcqlQuery
import id.walt.policies2.vc.policies.CredentialTypeMetadataResolver
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.io.encoding.Base64
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier2.verification.DcqlFulfillmentChecker
import id.walt.verifier2.data.DcqlFulfillmentFailure
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.UnsatisfiedSet
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.FAILED
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.SUCCESSFUL
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.reflect.KClass
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

    private suspend fun credential(type: String, scalarType: Boolean = false, subject: JsonObjectBuilder.() -> Unit = {}): String = issuer.signJws(
        buildJsonObject {
            put("iss", "did:example:issuer"); put("sub", "did:example:holder"); put("jti", "urn:uuid:$type")
            put("nbf", Clock.System.now().minus(10.days).epochSeconds)
            put("exp", Clock.System.now().plus(30.days).epochSeconds)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                if (scalarType) put("type", type)
                else putJsonArray("type") { add("VerifiableCredential"); add(type) }
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
        try {
            PresentationVerificationEngine.executeAllVerification(
                vpTokenContents = vpToken,
                session = session,
                updateSessionCallback = { s, _, block -> s.block() },
                failSessionCallback = { s, event, update -> update(s, event) { status = FAILED } },
            )
        } catch (e: PresentationRejectionException) {
            assertEquals(FAILED, session.status, e.message)
        }
        return session
    }

    private fun Verification2Session.assertFailedWith(failure: KClass<out SessionFailure>) {
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
    fun `independently resolved ancestry must satisfy the requested query id`() = runTest { withContext(Dispatchers.Default) {
        val wire = listOf("""{"alg":"ES256","typ":"dc+sd-jwt"}""", """{
            "vct":"https://issuer.example/leaf","vct#integrity":"${TypeMetadataFixture.LEAF_PIN}","_sd":[],"_sd_alg":"sha-256"
        }""", "synthetic-signature").joinToString(".") {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(it.encodeToByteArray())
        } + "~"
        val credential = CredentialParser.detectAndParse(wire).second
        val fetched = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            fetched += request.url.toString()
            respond(TypeMetadataFixture.documents.getValue(request.url.toString()))
        })
        try {
            val types = CredentialTypeMetadataResolver(http).resolve(credential).getOrThrow().types
            assertEquals(setOf("https://issuer.example/leaf", "https://types.example/sca-user", "https://types.example/sca"), types)
            assertEquals(3, fetched.size)
            fun query(type: String) = Json.decodeFromString<DcqlQuery>("""{
                "credentials":[{"id":"legitimate-query","format":"dc+sd-jwt","meta":{"vct_values":["$type"]}}]
            }""")
            fun match(type: String) = PresentationVerificationEngine.matchCredentialsToQueries(
                query(type), mapOf("legitimate-query" to listOf(credential)), true, mapOf(credential to types),
            )
            assertEquals(setOf("legitimate-query"), match("https://types.example/sca").getOrThrow())
            val failure = assertIs<DcqlFulfillmentChecker.DcqlFulfillmentException>(
                match("https://types.example/unrelated").exceptionOrNull(),
            )
            assertEquals(setOf("legitimate-query"), failure.details.missingQueryIds)
        } finally { http.close() }
    } }

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

    @Test
    fun `wildcard claim values match individual selected claims`() = runTest {
        val query = """{"credentials":[{"id":"admin","format":"jwt_vc_json","meta":{},
            "claims":[{"path":["credentialSubject","roles",null],"values":["admin"]}]}]}"""
        for ((roles, accepted) in listOf(listOf("admin") to true, listOf("user", "admin") to true, listOf("user") to false)) {
            val session = verify(query, mapOf("admin" to listOf(presentation(credential("AdministratorCredential") {
                putJsonArray("roles") { roles.forEach { add(it) } }
            }))))
            if (accepted) assertEquals(SUCCESSFUL, session.status, session.failure.toString())
            else session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
        }
    }

    @Test
    fun `invalid claim path does not prevent a matching alternative claim set`() = runTest {
        val query = """{"credentials":[{"id":"admin","format":"jwt_vc_json","meta":{},
            "claims":[{"id":"address","path":["credentialSubject","address","street"]},
                      {"id":"email","path":["credentialSubject","email"]}],
            "claim_sets":[["address"],["email"]]}]}"""
        for (hasEmail in listOf(true, false)) {
            val session = verify(query, mapOf("admin" to listOf(presentation(credential("AdministratorCredential") {
                put("address", "unstructured address")
                if (hasEmail) put("email", "alice@example.com")
            }))))
            if (hasEmail) assertEquals(SUCCESSFUL, session.status, session.failure.toString())
            else session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
        }
    }

    @Test
    fun `scalar W3C type is expanded and matched`() = runTest {
        for ((requiredType, accepted) in listOf(
            "https://www.w3.org/2018/credentials#VerifiableCredential" to true,
            "https://example.com/OtherCredential" to false,
        )) {
            val query = """{"credentials":[{"id":"admin","format":"jwt_vc_json",
                "meta":{"type_values":[["$requiredType"]]}}]}"""
            val session = verify(query, mapOf("admin" to listOf(presentation(credential("VerifiableCredential", scalarType = true)))))
            if (accepted) assertEquals(SUCCESSFUL, session.status, session.failure.toString())
            else session.assertFailedWith(SessionFailure.DcqlFulfillment::class)
        }
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
