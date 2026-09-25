package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.InMemoryPersistence
import id.walt.commons.persistence.Persistence
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.repository.IssuanceSessionStorageCodec
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.testsupport.*
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.sdjwt.SDJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.reflect.typeOf
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Exercise the documented offers through real signing and JSON-persisted issuance sessions. */
class Issuer2SharedCredentialStatusTest {
    @AfterEach
    fun clear() {
        KeyManager.types.remove(LEGACY_BACKEND)
        clearIssuer2TestEnvironment()
    }

    @Test
    fun w3cCopiesShareStatusWithCrypto2() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS)

    @Test
    fun sdJwtCopiesShareStatusWithCrypto2() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS)

    @Test
    fun mdocCopiesShareStatusWithCrypto2() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_MDOC_STATUS)

    @Test
    fun w3cCopiesShareStatusWithLegacySigning() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS, legacy = true)

    @Test
    fun sdJwtCopiesShareStatusWithLegacySigning() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS, legacy = true)

    @Test
    fun mdocCopiesShareStatusWithLegacySigning() = verifySharedStatus(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_MDOC_STATUS, legacy = true)

    @Test
    fun profileDefaultStatusIsSharedWithoutRuntimeOverride() = verifySharedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS,
        useProfileDefault = true,
    )

    @Test
    fun runtimeStatusOverridesDifferentProfileDefaultForEveryCopy() = verifySharedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS,
        profileDefault = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 1)
                put("uri", "https://status.example.com/profile-default")
            }
        },
    )

    private fun verifySharedStatus(
        example: CredentialOfferCreateRequest,
        legacy: Boolean = false,
        useProfileDefault: Boolean = false,
        profileDefault: JsonElement? = null,
    ) = testApplication {
        val status = assertNotNull(example.runtimeOverrides?.credentialStatus)
        val fixture = installStatusIssuer(legacy, profileDefault = example.profileId to (profileDefault ?: status))
        val offer = fixture.client.createCredentialOffer(if (useProfileDefault) example.copy(runtimeOverrides = null) else example)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val format = resolved.issuerMetadata.getCredentialConfiguration(configurationId)!!.format.value
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val saved = assertNotNull(fixture.repository.get(offer.offerId))
        assertEquals(legacy, saved.issuanceRequests.single().crypto2IssuerStoredKey == null)

        // Repeated successful requests keep the same configured status and the session stays open.
        repeat(2) {
            val proofs = fixture.twoProofs(resolved, configurationId)
            val response = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertStatuses(response.body(), format, status)
            val reloaded = assertNotNull(fixture.repository.get(offer.offerId))
            assertEquals(Json.encodeToJsonElement(saved.issuanceRequests), Json.encodeToJsonElement(reloaded.issuanceRequests))
            assertEquals("SUCCESSFUL", reloaded.status.name)
            assertFalse(reloaded.isClosed)
        }
    }

    @Test
    fun preAuthorizedItemsKeepTheirOwnStatuses() = verifySeparateItems(AuthenticationMethod.PRE_AUTHORIZED)

    @Test
    fun authorizedItemsKeepTheirOwnStatuses() = verifySeparateItems(AuthenticationMethod.AUTHORIZED)

    private fun verifySeparateItems(authMethod: AuthenticationMethod) = testApplication {
        val fixture = installStatusIssuer()
        val example = if (authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES
        } else {
            Issuer2RequestExamples.PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES
        }
        val offer = fixture.client.createCredentialOffer(example)
        val saved = assertNotNull(fixture.repository.get(offer.offerId))
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = if (authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            fixture.wallet.exchangePreAuthorizedCode(resolved, null, mapOf("authorization_details" to buildJsonArray {
                addJsonObject {
                    put("type", "openid_credential")
                    put("credential_configuration_id", configurationId)
                }
            }.toString()))
        } else {
            // Start at the authorization-code storage boundary; no external login is required.
            val code = UUID.randomUUID().toString()
            fixture.authorizationCodes.save(DefaultAuthorizationCodeRecord(
                code = code, clientId = "issuer2-wallet-test", redirectUri = "https://wallet.example/callback",
                grantedScopes = setOf(configurationId), grantedAudience = emptySet(),
                session = DefaultSession(subject = offer.offerId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to saved.expiresAt)),
                expiresAt = saved.expiresAt,
            ))
            fixture.wallet.exchangeAuthorizationCode(resolved, code)
        }
        val permissions = token.access_token.decodeJws().payload.getValue("authorization_details").jsonArray
        val identifiers = permissions.flatMap { it.jsonObject.getValue("credential_identifiers").jsonArray.map { id -> id.jsonPrimitive.content } }
        assertEquals(saved.issuanceRequests.map { it.credentialIdentifier }.toSet(), identifiers.toSet())

        saved.issuanceRequests.forEachIndexed { index, item ->
            val response = fixture.issue(
                resolved, token.access_token,
                credentialRequestByIdentifier(item.credentialIdentifier, fixture.twoProofs(resolved, configurationId)),
            )
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertStatuses(response.body(), "dc+sd-jwt", assertNotNull(example.credentials[index].runtimeOverrides?.credentialStatus))
            val reloaded = assertNotNull(fixture.repository.get(offer.offerId))
            assertEquals(Json.encodeToJsonElement(saved.issuanceRequests), Json.encodeToJsonElement(reloaded.issuanceRequests))
            assertEquals(index + 1, reloaded.issuanceResults.size)
            assertEquals(if (index == 0) "ACTIVE" else "SUCCESSFUL", reloaded.status.name)
            assertFalse(reloaded.isClosed)
        }
    }

    @Test
    fun invalidProofAndOversizedBatchCanBeCorrectedWithoutLosingStatus() = testApplication {
        val fixture = installStatusIssuer()
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS
        val offer = fixture.client.createCredentialOffer(example)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val saved = assertNotNull(fixture.repository.get(offer.offerId))
        val proofs = fixture.twoProofs(resolved, configurationId)
        val cases = listOf(
            proofs.copy(jwt = proofs.jwt!! + proofs.jwt!!.first()) to "invalid_credential_request",
            proofs.copy(jwt = listOf(proofs.jwt!!.first(), "invalid-proof")) to "invalid_proof",
        )
        for ((invalidProofs, expectedError) in cases) {
            val response = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, invalidProofs))
            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
            assertEquals(expectedError, response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
            assertEquals(Json.encodeToJsonElement(saved), Json.encodeToJsonElement(assertNotNull(fixture.repository.get(offer.offerId))))
        }
        val response = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertStatuses(response.body(), "dc+sd-jwt", example.runtimeOverrides!!.credentialStatus!!)
    }

    @Test
    fun disabledBatchStillAllowsSingleProofWithStatus() = testApplication {
        val fixture = installStatusIssuer(batchSize = null)
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS
        val offer = fixture.client.createCredentialOffer(example)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val proofs = fixture.twoProofs(resolved, configurationId)
        val rejected = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs))
        assertEquals(HttpStatusCode.BadRequest, rejected.status, rejected.bodyAsText())
        assertEquals("invalid_credential_request", rejected.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        val response = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs.copy(jwt = proofs.jwt!!.take(1))))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertStatuses(response.body(), "dc+sd-jwt", example.runtimeOverrides!!.credentialStatus!!, count = 1)
    }

    private suspend fun ApplicationTestBuilder.installStatusIssuer(
        legacy: Boolean = false,
        batchSize: Int? = 2,
        profileDefault: Pair<String, JsonElement>? = null,
    ): Fixture {
        loadIssuer2ConfigFiles()
        // A test backend alias exercises the legacy provider branch without weakening JWK migration.
        if (legacy) KeyManager.types[LEGACY_BACKEND] = typeOf<JWKKey>()
        val configuredProfiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val profiles = configuredProfiles.copy(profiles = configuredProfiles.profiles.mapValues { (id, profile) ->
            profile.copy(
                issuerKey = if (legacy) JsonObject(profile.issuerKey + ("type" to JsonPrimitive(LEGACY_BACKEND))) else profile.issuerKey,
                credentialStatus = profileDefault?.takeIf { it.first == id }?.second ?: profile.credentialStatus,
            )
        })
        val repository = ConfiguredIssuanceSessionRepository(
            sessions = JsonSessions(),
            crypto2Keys = InMemoryPersistence("status-test-keys", 5.minutes),
        )
        val attestation = createIssuer2ClientAttestationTestMaterial()
        val module = Issuer2Module(
            ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
                clientAuthenticationConfig = attestation.clientAuthenticationConfig,
                batchCredentialIssuance = batchSize?.let(::BatchCredentialIssuance),
            ),
            ConfigManager.getConfig(), profiles, issuanceSessionRepository = repository,
        )
        val codes = Issuer2Module::class.java.getDeclaredField("authorizationCodeRepository").let {
            it.isAccessible = true
            it.get(module) as ConfiguredAuthorizationCodeRepository
        }
        application {
            install(ContentNegotiation) { json(issuer2TestJson) }
            installIssuer2AuthenticationForTests()
            configurePlugins()
            routing {
                module.managementController.register(this)
                module.openId4VciController.register(this)
            }
        }
        val client = apiClient()
        return Fixture(client, Issuer2WalletFlowDriver(client, attestationAssembler = attestation.attestationAssembler), repository, codes)
    }

    private data class Fixture(
        val client: HttpClient,
        val wallet: Issuer2WalletFlowDriver,
        val repository: ConfiguredIssuanceSessionRepository,
        val authorizationCodes: ConfiguredAuthorizationCodeRepository,
    ) {
        suspend fun twoProofs(offer: ResolvedCredentialOffer, configurationId: String): Proofs {
            val first = wallet.buildJwtProofs(offer.issuerMetadata, configurationId)
            val second = wallet.buildJwtProofs(offer.issuerMetadata, configurationId)
            return first.copy(jwt = first.jwt!! + second.jwt!!)
        }

        suspend fun issue(offer: ResolvedCredentialOffer, token: String, body: JsonObject): HttpResponse =
            client.post(offer.issuerMetadata.credentialEndpoint) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun assertStatuses(response: JsonObject, format: String, expected: JsonElement, count: Int = 2) {
        val credentials = response.getValue("credentials").jsonArray.map { it.jsonObject.getValue("credential").jsonPrimitive.content }
        assertEquals(count, credentials.size)
        assertEquals(count, credentials.distinct().size)
        credentials.forEach { credential ->
            val actual = when (format) {
                "jwt_vc_json" -> credential.decodeJws().payload.let { (it["vc"]?.jsonObject ?: it)["credentialStatus"] }
                "dc+sd-jwt" -> SDJwt.parse(credential).fullPayload["status"]
                "mso_mdoc" -> {
                    val mso = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.decodeFromBase64Url()).decodeMobileSecurityObject()
                    Json.encodeToJsonElement(assertNotNull(mso.status))
                }
                else -> error("Unexpected format: $format")
            }
            assertEquals(expected, actual)
        }
    }

    private class JsonSessions : Persistence<IssuanceSession>("status-test-sessions", 5.minutes) {
        private val raw = InMemoryPersistence<String>(discriminator, 5.minutes)
        override fun get(id: String) = raw[id]?.let(IssuanceSessionStorageCodec::decode)
        override fun getAndRemove(id: String) = raw.getAndRemove(id)?.let(IssuanceSessionStorageCodec::decode)
        override fun set(id: String, value: IssuanceSession) = set(id, value, null)
        override fun set(id: String, value: IssuanceSession, ttl: Duration?) = raw.set(id, Json.encodeToString(value), ttl)
        override fun remove(id: String) = raw.remove(id)
        override fun contains(id: String) = id in raw
        override fun listAllKeys() = raw.listAllKeys()
        override fun getAll() = raw.getAll().map(IssuanceSessionStorageCodec::decode)
        override fun listSize(id: String) = raw.listSize(id)
        override fun listAdd(id: String, value: IssuanceSession, ttl: Duration?) = raw.listAdd(id, Json.encodeToString(value), ttl)
    }

    private companion object {
        const val LEGACY_BACKEND = "issuer2-status-test-legacy-jwk"
    }
}
