@file:Suppress("DEPRECATION")

package id.walt.issuer2.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.*
import id.walt.issuer2.controller.Issuer2RouteSurface
import id.walt.issuer2.controller.OpenId4VciController
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.issuer2.testsupport.*
import id.walt.issuer2.web.plugins.configureHTTP
import id.walt.issuer2.web.plugins.configureMonitoring
import id.walt.ktornotifications.SseNotifier
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeConfig
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeService
import id.walt.openid4vci.clientauth.attestation.challenge.JwtAttestationChallengeService
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.openid4vci.metadata.issuer.signing.SignedMetadataConfig
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceValidationResult
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class Issuer2AttestationChallengeTest {
    @AfterEach
    fun clear() = clearIssuer2TestEnvironment()

    @Test
    fun `production module starts with challenges and includes endpoint in signed discovery`() = testApplication {
        val material = createIssuer2ClientAttestationTestMaterial()
        val metadataKey = JWKKey.generate(KeyType.secp256r1)
        val signing = SignedMetadataConfig(MetadataSigningMethod.StaticJwk(metadataKey.exportJWKObject()))
        installIssuer2WithConfigFiles(configureServiceConfig = {
            it.copy(clientAuthenticationConfig = material.withChallenges(), signedMetadata = signing)
        })
        val client = apiClient()
        val response = client.post("/openid4vci/challenge")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val challenge = response.body<JsonObject>().string("attestation_challenge")
        val metadata = client.get("/.well-known/oauth-authorization-server/openid4vci").body<JsonObject>()
        val endpoint = metadata.string("issuer") + "/challenge"
        assertEquals(endpoint, metadata.string("challenge_endpoint"))
        val signedMetadata = metadata.string("signed_metadata")
        assertTrue(metadataKey.getPublicKey().verifyJws(signedMetadata).isSuccess)
        assertEquals(endpoint, signedMetadata.decodeJws().payload.string("challenge_endpoint"))
        assertEquals(metadata.string("issuer"), challenge.decodeJws().payload.string("iss"))
    }

    @Test
    fun `challenge endpoint is mounted once for either protocol surface but absent for metadata only`() {
        for (surfaces in listOf(
            Issuer2RouteSurface.all,
            Issuer2RouteSurface.preAuthorizedCodeOnly,
            setOf(Issuer2RouteSurface.AUTHORIZATION_CODE),
            setOf(Issuer2RouteSurface.METADATA),
        )) testApplication {
            val fixture = fixture()
            val client = install(fixture, surfaces)
            val response = client.post("/openid4vci/challenge") { header(HttpHeaders.Origin, "https://wallet.example") }
            if (surfaces == setOf(Issuer2RouteSurface.METADATA)) {
                assertEquals(HttpStatusCode.NotFound, response.status)
            } else {
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                assertEquals(listOf("no-store"), response.headers.getAll(HttpHeaders.CacheControl))
                assertTrue(response.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty()
                    .contains(ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE, ignoreCase = true))
                assertTrue(fixture.challenges.validate(response.body<JsonObject>().string("attestation_challenge"), fixture.issuer))
            }
        }
    }

    @Test
    fun `disabled challenges are neither issued nor advertised and do not require challenge authentication`() = testApplication {
        val fixture = fixture(enabled = false)
        assertNull(fixture.module.attestationChallengeService)
        assertNull(fixture.metadata.getAuthorizationServerMetadata().challengeEndpoint)
        val client = install(fixture)
        assertEquals(HttpStatusCode.NotFound, client.post("/openid4vci/challenge").status)
        assertEquals(HttpStatusCode.Created, client.par(fixture, null).status)
    }

    @Test
    fun `PAR token and refresh reuse challenges and retry without consuming authorization grants`() = testApplication {
        val fixture = fixture()
        val client = install(fixture)
        val challenge = client.par(fixture, null).assertChallengeError(fixture)
        assertEquals(HttpStatusCode.Created, client.par(fixture, challenge).status)
        for (invalid in listOf("malformed", fixture.expiredChallenge())) {
            assertNotEquals(challenge, client.par(fixture, invalid).assertChallengeError(fixture))
        }
        assertEquals(HttpStatusCode.Created, client.par(fixture, challenge).status)
        val code = fixture.authorizationCode()
        val parameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("client_id", "wallet")
            append("redirect_uri", REDIRECT)
            append("code", code)
        }
        for (invalid in listOf(null, "malformed", fixture.expiredChallenge())) {
            client.token(fixture, parameters, invalid).assertChallengeError(fixture)
            assertEquals(0, fixture.codeConsumptions)
        }
        val token = client.token(fixture, parameters, challenge).assertToken()
        assertEquals(1, fixture.codeConsumptions)
        val refresh = Parameters.build {
            append("grant_type", "refresh_token")
            append("client_id", "wallet")
            append("refresh_token", token.string("refresh_token"))
        }
        for (invalid in listOf(null, "malformed")) {
            client.token(fixture, refresh, invalid).assertChallengeError(fixture)
        }
        val replacement = client.token(fixture, refresh, fixture.expiredChallenge()).assertChallengeError(fixture)
        assertNotEquals(challenge, replacement)
        val refreshed = client.token(fixture, refresh, challenge).assertToken()
        // A rejection's replacement challenge is also accepted with a freshly signed PoP.
        client.token(fixture, Parameters.build {
            append("grant_type", "refresh_token")
            append("client_id", "wallet")
            append("refresh_token", refreshed.string("refresh_token"))
        }, replacement).assertToken()
    }

    @Test
    fun `authenticated preauthorized code survives invalid challenges while anonymous access remains explicit`() = testApplication {
        val fixture = fixture(anonymous = true)
        val client = install(fixture)
        val code = fixture.preAuthorizedCode("wallet")
        val parameters = Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("client_id", "wallet")
            append("pre-authorized_code", code)
        }
        val otherKey = JWKKey.generate(KeyType.secp256r1)
        val wrongSignature = JwtAttestationChallengeService(
            JwtSigningKeyResolver { otherKey }, JwtVerificationKeyResolver { otherKey },
        ).issue(fixture.issuer)
        val nonce = fixture.module.credentialNonceService.issue(fixture.nonceBinding).nonce
        var challenge: String? = null
        for (invalid in listOf(null, "malformed", fixture.expiredChallenge(),
            fixture.challenges.issue("https://other.example/openid4vci"), wrongSignature, nonce)) {
            challenge = client.token(fixture, parameters, invalid).assertChallengeError(fixture)
            assertNotNull(fixture.preAuthorizedCodes.get(code))
        }
        client.token(fixture, parameters, challenge).assertToken()
        assertNull(fixture.preAuthorizedCodes.get(code))
        val anonymousCode = fixture.preAuthorizedCode(null)
        client.submitForm("/openid4vci/token", Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", anonymousCode)
        }).assertToken()
    }

    @Test
    fun `ordinary authentication failures retain invalid client and anonymous requests are rejected by default`() = testApplication {
        val fixture = fixture()
        val client = install(fixture)
        val missing = client.submitForm("/openid4vci/par", parParameters())
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("invalid_client", missing.body<JsonObject>().string("error"))
        assertNull(missing.headers[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
        val invalid = client.submitForm("/openid4vci/par", parParameters()) {
            header(ClientAttestationHeaders.CLIENT_ATTESTATION, "broken")
            header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, "broken")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertEquals("invalid_client", invalid.body<JsonObject>().string("error"))
        assertNull(invalid.headers[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
        val code = fixture.preAuthorizedCode(null)
        val anonymous = client.submitForm("/openid4vci/token", Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", code)
        })
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertNotNull(fixture.preAuthorizedCodes.get(code))
    }

    @Test
    fun `challenge failures are sanitized and cancellation propagates without session events`() = runTest {
        val fixture = fixture()
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val requestId = UUID.randomUUID().toString()
        val failed = fixture.protocol(challengeService { error("private signing details") }).processChallengeRequest(requestId)
        assertEquals(500, failed.status)
        assertEquals(buildJsonObject {
            put("error", "server_error")
            put("error_description", "Client attestation challenge service is unavailable")
        }, failed.payload)
        assertEquals("no-store", failed.headers[HttpHeaders.CacheControl])
        assertFailsWith<CancellationException> {
            fixture.protocol(challengeService { throw CancellationException("cancelled") }).processChallengeRequest(requestId)
        }
        assertFailsWith<NotFoundException> { fixture.protocol(null).processChallengeRequest(requestId) }
        fixture.protocol().processChallengeRequest(requestId)
        assertTrue(events.replayCache.none { it.requestId == requestId })
    }

    @Test
    fun `challenge signing failure maps to HTTP server error`() = testApplication {
        val fixture = fixture()
        val client = install(fixture, challengeService = challengeService { error("private signing details") })
        val response = client.post("/openid4vci/challenge")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(buildJsonObject {
            put("error", "server_error")
            put("error_description", "Client attestation challenge service is unavailable")
        }, response.body<JsonObject>())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `module uses configured lifetime and supports migrated and explicit crypto2 token keys`() = runTest {
        for (type in listOf(KeyType.secp256r1, KeyType.Ed25519, KeyType.RSA)) {
            for (explicit in listOf(false, true)) {
                val key = JWKKey.generate(type)
                val stored = if (explicit) StoredKeyCodec.encodeToString(V1KeyMigration().migrate(
                    recordId = KeyId(key.getKeyId()), serialized = KeySerialization.serializeKeyToJson(key).jsonObject,
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )) else null
                val fixture = fixture(lifetime = 42, tokenKey = key, storedKey = stored)
                val challenge = fixture.challenges.issue(fixture.issuer)
                val claims = challenge.decodeJws().payload
                assertEquals(42, claims.getValue("exp").jsonPrimitive.long - claims.getValue("iat").jsonPrimitive.long)
                assertTrue(fixture.challenges.validate(challenge, fixture.issuer))
                assertFalse(fixture.challenges.validate(challenge, "https://other.example/openid4vci"))
                assertTrue(key.getPublicKey().verifyJws(challenge).isSuccess)
                assertEquals(CredentialNonceValidationResult.INVALID,
                    fixture.module.credentialNonceService.validate(challenge, fixture.nonceBinding))
            }
        }
    }

    @Test
    fun `non-JWK backend retains legacy challenge signing and authentication`() = testApplication {
        // A local backend alias exercises the legacy branch without a live KMS dependency.
        val backend = "issuer2-challenge-test-key"
        KeyManager.register<JWKKey>(backend) { JWKKey.generate(it.keyType) }
        try {
            val original = fixture()
            val serialized = Json.parseToJsonElement(original.config.ciTokenKey).jsonObject
            val legacy = Fixture(
                original.config.copy(ciTokenKey = JsonObject(serialized + ("type" to JsonPrimitive(backend))).toString()),
                original.tokenKey, original.material, original.instanceKey,
            )
            assertNull(legacy.module.crypto2TokenSigningKey)
            val challenge = legacy.challenges.issue(legacy.issuer)
            assertTrue(legacy.challenges.validate(challenge, legacy.issuer))
            val client = install(legacy)
            assertEquals(HttpStatusCode.Created, client.par(legacy, challenge).status)
        } finally {
            KeyManager.types.remove(backend)
            KeyManager.keyTypeGeneration.remove(backend)
        }
    }

    @Test
    fun `enabled challenges reject invalid crypto2 keys at startup without downgrade`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val otherKey = JWKKey.generate(KeyType.secp256r1)
        val mismatched = V1KeyMigration().migrate(
            recordId = KeyId(key.getKeyId()), serialized = KeySerialization.serializeKeyToJson(otherKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        val signingOnly = V1KeyMigration().migrate(
            recordId = KeyId(key.getKeyId()), serialized = KeySerialization.serializeKeyToJson(key).jsonObject,
            usages = setOf(KeyUsage.SIGN),
        )
        for (stored in listOf("invalid", StoredKeyCodec.encodeToString(mismatched),
            StoredKeyCodec.encodeToString(signingOnly))) {
            assertFails { fixture(tokenKey = key, storedKey = stored) }
        }
    }

    private suspend fun fixture(
        enabled: Boolean = true,
        anonymous: Boolean = false,
        lifetime: Long = 300,
        tokenKey: JWKKey? = null,
        storedKey: String? = null,
    ): Fixture {
        val resolvedTokenKey = tokenKey ?: JWKKey.generate(KeyType.secp256r1)
        val material = createIssuer2ClientAttestationTestMaterial()
        val auth = if (enabled) material.withChallenges(lifetime) else material.clientAuthenticationConfig
        return Fixture(Issuer2ServiceConfig(
            baseUrl = "http://localhost",
            ciTokenKey = KeySerialization.serializeKey(resolvedTokenKey),
            ciTokenStoredKey = storedKey,
            clientAuthenticationConfig = auth.copy(supportedMethods = auth.supportedMethods +
                    if (anonymous) listOf(ClientAuthenticationMethodConfig.PreAuthAnonymous) else emptyList()),
        ), resolvedTokenKey, material, generateIssuer2WalletInstanceKey())
    }

    private fun ApplicationTestBuilder.install(
        fixture: Fixture,
        surfaces: Set<Issuer2RouteSurface> = Issuer2RouteSurface.all,
        challengeService: AttestationChallengeService? = fixture.module.attestationChallengeService,
    ): HttpClient {
        application {
            configureMonitoring()
            configureHTTP()
            install(ContentNegotiation) { json(issuer2TestJson) }
            install(Authentication) { bearer("auth-oauth") {} }
            routing { fixture.controller(challengeService).register(this, surfaces) }
        }
        return apiClient()
    }

    private class Fixture(
        val config: Issuer2ServiceConfig,
        val tokenKey: JWKKey,
        val material: Issuer2ClientAttestationTestMaterial,
        val instanceKey: id.walt.crypto2.keys.Key,
    ) {
        val issuer = config.openId4VciBaseUrl()
        var codeConsumptions = 0
        private val codeStore = InMemoryAuthorizationCodeRepository()
        val codes = object : id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository by codeStore {
            override suspend fun consume(code: String): id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord? {
                codeConsumptions++
                return codeStore.consume(code)
            }
        }
        val preAuthorizedCodes = InMemoryPreAuthorizedCodeRepository()
        val module = OpenId4VciModule.create(config, codes, preAuthorizedCodes, InMemoryPARRepository(), InMemoryRefreshTokenRepository())
        val challenges get() = assertNotNull(module.attestationChallengeService)
        val metadataConfig = Issuer2MetadataConfig(credentialConfigurations = mapOf("identity" to buildJsonObject {
            put("format", "dc+sd-jwt"); put("vct", "https://example.com/identity"); put("scope", "identity")
        }))
        val profiles = CredentialProfileService(Issuer2ProfilesConfig(), metadataConfig)
        val sessions = IssuanceSessionService(object : IssuanceSessionRepository {
            val records = mutableMapOf<String, IssuanceSession>()
            override suspend fun save(session: IssuanceSession) = session.also { records[it.sessionId] = it }
            override suspend fun get(sessionId: String) = records[sessionId]
            override suspend fun list() = records.values.toList()
            override suspend fun remove(sessionId: String) { records.remove(sessionId) }
            override suspend fun take(sessionId: String) = records.remove(sessionId)
        })
        val metadata = MetadataService(config, metadataConfig, profiles, sessions,
            preAuthorizedGrantAnonymousAccessSupported = module.preAuthorizedCodeIssuer.anonymousAccessSupported)
        val notifications = IssuanceNotificationService()
        val nonceBinding = CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")

        fun protocol(service: AttestationChallengeService? = module.attestationChallengeService) = OpenId4VciProtocolService(
            module.oauth2Provider, sessions, profiles, metadata, notifications, module.credentialNonceService,
            attestationChallengeService = service,
        )

        fun controller(service: AttestationChallengeService?) = OpenId4VciController(
            metadata, protocol(service), CredentialOfferService(profiles, sessions, module.preAuthorizedCodeIssuer, config, notifications), notifications,
        )

        suspend fun headers(challenge: String?) = material.attestationAssembler.buildAttestationHeaders(
            instanceKey, "wallet", issuer, challenge,
        )

        suspend fun session(method: AuthenticationMethod): DefaultSession {
            val session = IssuanceSession(
                sessionId = UUID.randomUUID().toString(), authenticationMethod = method,
                issuanceRequests = listOf(IssuanceRequest("credential", "profile", "identity", JsonObject(emptyMap()),
                    credentialData = JsonObject(emptyMap()))),
                expiresAt = Clock.System.now() + 10.minutes,
            )
            sessions.saveSession(session)
            return DefaultSession(subject = session.sessionId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to session.expiresAt))
        }

        suspend fun authorizationCode(): String {
            val session = session(AuthenticationMethod.AUTHORIZED)
            val code = UUID.randomUUID().toString()
            codes.save(DefaultAuthorizationCodeRecord(code, "wallet", REDIRECT, setOf("identity"), emptySet(), session,
                session.expiresAt.getValue(TokenType.ACCESS_TOKEN)))
            return code
        }

        suspend fun preAuthorizedCode(clientId: String?) = module.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
            clientId = clientId, scopes = setOf("identity"), session = session(AuthenticationMethod.PRE_AUTHORIZED),
        )).code

        suspend fun expiredChallenge() = JwtAttestationChallengeService(
            JwtSigningKeyResolver { tokenKey }, JwtVerificationKeyResolver { tokenKey },
            now = { Clock.System.now() - 10.minutes },
        ).issue(issuer)
    }

    private suspend fun HttpClient.par(fixture: Fixture, challenge: String?): HttpResponse {
        val headers = fixture.headers(challenge)
        return submitForm("/openid4vci/par", parParameters()) {
            header(ClientAttestationHeaders.CLIENT_ATTESTATION, headers.attestationJwt)
            header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, headers.popJwt)
            header(HttpHeaders.Origin, "https://wallet.example")
        }
    }

    private suspend fun HttpClient.token(fixture: Fixture, parameters: Parameters, challenge: String?): HttpResponse {
        val headers = fixture.headers(challenge)
        return submitForm("/openid4vci/token", parameters) {
            header(ClientAttestationHeaders.CLIENT_ATTESTATION, headers.attestationJwt)
            header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, headers.popJwt)
            header(HttpHeaders.Origin, "https://wallet.example")
        }
    }

    private suspend fun HttpResponse.assertChallengeError(fixture: Fixture): String {
        assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        assertEquals("use_attestation_challenge", body<JsonObject>().string("error"))
        assertEquals("no-store", headers[HttpHeaders.CacheControl])
        assertTrue(headers[HttpHeaders.AccessControlExposeHeaders].orEmpty()
            .contains(ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE, ignoreCase = true))
        return assertNotNull(headers[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE]).also {
            assertTrue(fixture.challenges.validate(it, fixture.issuer))
        }
    }

    private suspend fun HttpResponse.assertToken(): JsonObject {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        assertNull(headers[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
        return body<JsonObject>().also { assertTrue(it.string("access_token").isNotBlank()) }
    }

    private fun challengeService(issue: suspend () -> String) = object : AttestationChallengeService {
        override suspend fun issue(authorizationServerIssuer: String) = issue()
        override suspend fun validate(challenge: String, authorizationServerIssuer: String) = false
    }

    private fun Issuer2ClientAttestationTestMaterial.withChallenges(lifetime: Long = 300) =
        clientAuthenticationConfig.copy(supportedMethods = listOf(
            assertNotNull(clientAuthenticationConfig.clientAttestationMethod()).copy(challenge = AttestationChallengeConfig(lifetime)),
        ))

    private fun parParameters() = Parameters.build {
        append("client_id", "wallet"); append("response_type", "code")
        append("redirect_uri", REDIRECT); append("scope", "identity")
    }

    companion object {
        private const val REDIRECT = "https://wallet.example/callback"
        private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    }
}
