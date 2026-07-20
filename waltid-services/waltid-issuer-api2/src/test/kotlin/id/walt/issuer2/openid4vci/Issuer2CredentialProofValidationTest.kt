package id.walt.issuer2.openid4vci

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.ResolvedCredentialOffer
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.prooftypes.Proofs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.io.encoding.Base64

class Issuer2CredentialProofValidationTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun `validates proof before key acceptance and signing`() = testApplication {
        val acceptanceCalls = AtomicInteger()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
                acceptanceCalls.incrementAndGet()
                true
            }
        )
        val flow = prepareFlow(apiClient())
        val key = JWKKey.generate(KeyType.secp256r1)
        val now = Clock.System.now().epochSeconds

        suspend fun assertRejected(proofs: Proofs) {
            assertInvalidProof(flow.request(proofs))
            assertEquals(0, acceptanceCalls.get())
            val session = flow.client.getSession(flow.sessionId)
            assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
            assertFalse(session.isClosed)
        }

        assertRejected(proof(key, nonce = flow.nonce(), audience = listOf("https://wrong.example")))
        assertRejected(proof(key, nonce = flow.nonce(), issuedAt = now - 6.minutes.inWholeSeconds))
        assertRejected(proof(key, nonce = flow.nonce(), issuedAt = now + 2.minutes.inWholeSeconds))
        assertRejected(proof(key, nonce = null))
        assertRejected(proof(key, nonce = UUID.randomUUID().toString()))
        assertRejected(proof(key, nonce = flow.nonce(), type = "JWT"))
        assertRejected(Proofs())
        assertRejected(Proofs(jwt = listOf("not-a-jwt")))
        assertRejected(proof(key, nonce = flow.nonce(), kid = "did:example:unrelated#key-1"))
        val holderDid = DidJwkRegistrar().registerByKey(key, DidJwkCreateOptions(KeyType.secp256r1)).did
        assertRejected(
            withConflictingMalformedJwk(proof(
                key = key,
                nonce = flow.nonce(),
                kid = "$holderDid#0",
                includeJwk = false,
            ), key)
        )
        assertRejected(
            proof(
                key = key,
                nonce = flow.nonce(),
                kid = "$holderDid#unrelated",
                includeJwk = false,
            )
        )

        val reusableNonce = flow.nonce()
        assertRejected(
            proof(
                key = key,
                signingKey = JWKKey.generate(KeyType.secp256r1),
                nonce = reusableNonce,
            )
        )

        val validProof = proof(
            key = key,
            nonce = reusableNonce,
            audience = listOf("https://other.example", flow.resolvedOffer.issuerMetadata.credentialIssuer),
        ).jwt.orEmpty().single()
        assertRejected(Proofs(jwt = listOf(validProof, validProof)))

        val response = flow.request(Proofs(jwt = listOf(validProof)))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, acceptanceCalls.get())
    }

    @Test
    fun `replayed nonce is rejected before key acceptance`() = testApplication {
        val acceptanceCalls = AtomicInteger()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
                acceptanceCalls.incrementAndGet()
                false
            }
        )
        val flow = prepareFlow(apiClient())
        val proofs = proof(
            key = JWKKey.generate(KeyType.secp256r1),
            nonce = flow.nonce(),
            audience = listOf(flow.resolvedOffer.issuerMetadata.credentialIssuer),
        )

        assertInvalidProof(flow.request(proofs))
        assertEquals(1, acceptanceCalls.get())

        assertInvalidProof(flow.request(proofs))
        assertEquals(1, acceptanceCalls.get())
        val session = flow.client.getSession(flow.sessionId)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
        assertFalse(session.isClosed)
    }

    private suspend fun prepareFlow(client: HttpClient): ProofTestFlow {
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.identitySdJwt,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        return ProofTestFlow(
            client = client,
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            sessionId = createdOffer.offerId,
        )
    }

    private suspend fun proof(
        key: Key,
        nonce: String?,
        signingKey: Key = key,
        audience: List<String> = listOf("http://localhost/openid4vci"),
        issuedAt: Long = Clock.System.now().epochSeconds,
        type: String = "openid4vci-proof+jwt",
        kid: String? = null,
        includeJwk: Boolean = true,
    ): Proofs {
        val payload = buildJsonObject {
            val audienceClaim = if (audience.size == 1) {
                JsonPrimitive(audience.single())
            } else {
                JsonArray(audience.map(::JsonPrimitive))
            }
            put("aud", audienceClaim)
            put("iat", issuedAt)
            nonce?.let { put("nonce", it) }
        }
        val header = buildJsonObject {
            put("typ", type)
            put("alg", key.keyType.jwsAlg)
            if (includeJwk) put("jwk", key.getPublicKey().exportJWKObject())
            kid?.let { put("kid", it) }
        }
        return Proofs(jwt = listOf(signingKey.signJws(payload.toString().encodeToByteArray(), header)))
    }

    private suspend fun assertInvalidProof(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_proof", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
    }

    private fun withConflictingMalformedJwk(proofs: Proofs, key: Key): Proofs {
        val parts = proofs.jwt.orEmpty().single().split('.')
        val header = buildJsonObject {
            put("typ", "openid4vci-proof+jwt")
            put("alg", key.keyType.jwsAlg)
            put("kid", "did:example:holder#key-1")
            put("jwk", "invalid")
        }
        val encodedHeader = Base64.UrlSafe.encode(header.toString().encodeToByteArray()).trimEnd('=')
        return Proofs(jwt = listOf("$encodedHeader.${parts[1]}.${parts[2]}"))
    }
}

private data class ProofTestFlow(
    val client: HttpClient,
    val resolvedOffer: ResolvedCredentialOffer,
    val accessToken: String,
    val sessionId: String,
) {
    suspend fun nonce(): String =
        client.post(requireNotNull(resolvedOffer.issuerMetadata.nonceEndpoint))
            .body<JsonObject>()["c_nonce"]?.jsonPrimitive?.content
            ?: error("Nonce endpoint returned no c_nonce")

    suspend fun request(proofs: Proofs): HttpResponse =
        client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }
}
