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
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.issuer2.domain.CredentialStatusConfigurationTest.Companion.tokenStatus
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCredential
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.repository.IssuanceSessionStorageCodec
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.ConfiguredCredentialStatusConsumptionRepository
import id.walt.issuer2.repository.CredentialStatusConsumption
import id.walt.issuer2.repository.CredentialStatusConsumptionRepository
import id.walt.issuer2.service.openid4vci.decodeExternalLoginAuthorizationParameters
import id.walt.issuer2.testsupport.*
import id.walt.ktornotifications.SseNotifier
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.IssuerStateMode
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.sdjwt.SDJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.get
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
import kotlin.time.Instant

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
    fun flatW3cArrayRemainsOneSharedMultiPurposeStatus() = verifySharedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS.copy(
            runtimeOverrides = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS
                .runtimeOverrides!!.copy(credentialStatus =
                    Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_W3C_STATUS
                        .runtimeOverrides!!.credentialStatus!!.jsonArray.first()),
        ),
    )

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

    @Test
    fun sdJwtPreallocatedGroupsStayFreshAcrossRequestsAndOffers() = verifyPreallocatedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS,
    )

    @Test
    fun w3cPreallocatedGroupsKeepRevocationAndSuspensionTogether() = verifyPreallocatedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_W3C_STATUS,
    )

    @Test
    fun mdocPreallocatedGroupsStayFreshAcrossRequestsAndOffers() = verifyPreallocatedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS,
    )

    @Test
    fun preallocatedProfileDefaultsApplyWithoutRuntimeOverrides() = verifyPreallocatedStatus(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS,
        profileDefault = true,
    )

    @Test
    fun sdJwtKeepsLiteralBusinessClaimsAlongsideDistinctSignedStatuses() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val overrides = requireNotNull(example.runtimeOverrides)
        val fixture = installStatusIssuer()
        val offer = fixture.client.createCredentialOffer(example.copy(runtimeOverrides = overrides.copy(
            mapping = buildJsonObject {
                put("status-label", "active")
                put("status.label", "verified")
            },
        )))
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val response = fixture.issue(resolved, token.access_token,
            credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId)))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.body<JsonObject>()
        assertPreallocatedStatuses(body, "dc+sd-jwt", overrides.credentialStatus!!.jsonArray.take(2))
        for (copy in body.getValue("credentials").jsonArray) {
            val claims = SDJwt.parse(copy.jsonObject.getValue("credential").jsonPrimitive.content).fullPayload
            assertEquals(JsonPrimitive("active"), claims["status-label"])
            assertEquals(JsonPrimitive("verified"), claims["status.label"])
        }
    }

    @Test
    fun overlappingProfilesConsumeDistinctStatusesAcrossOffers() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val statuses = example.runtimeOverrides!!.credentialStatus!!.jsonArray
        val alias = "secondStatusProfile"
        val fixture = installStatusIssuer(profileDefault = example.profileId to statuses,
            additionalProfile = alias to example.profileId)
        for ((index, profileId) in listOf(example.profileId, alias).withIndex()) {
            val offer = fixture.client.createCredentialOffer(example.copy(profileId = profileId, runtimeOverrides = null))
            val resolved = fixture.wallet.resolve(offer)
            val configurationId = resolved.offer.credentialConfigurationIds.single()
            val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
            val response = fixture.issue(resolved, token.access_token,
                credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId)))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertPreallocatedStatuses(response.body(), "dc+sd-jwt", statuses.drop(index * 2).take(2))
            if (index == 1) {
                val exhausted = fixture.issue(resolved, token.access_token,
                    credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId)))
                assertEquals(HttpStatusCode.BadRequest, exhausted.status)
                val single = fixture.twoProofs(resolved, configurationId).let { it.copy(jwt = it.jwt!!.take(1)) }
                val last = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, single))
                assertEquals(HttpStatusCode.OK, last.status, last.bodyAsText())
                assertPreallocatedStatuses(last.body(), "dc+sd-jwt", statuses.takeLast(1))
            }
        }
    }

    @Test
    fun malformedStatusGroupsAreRejectedBeforeCreatingAnOffer() = testApplication {
        val fixture = installStatusIssuer()
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val invalid = example.copy(runtimeOverrides = example.runtimeOverrides!!.copy(
            credentialStatus = JsonArray(listOf(JsonArray(emptyList()))),
        ))
        val response = fixture.client.post("/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(invalid))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertTrue(fixture.repository.list().isEmpty())
    }

    @Test
    fun mdocOfferRejectsDuplicateEncodedReferencesAndUnencodableUrls() = testApplication {
        val fixture = installStatusIssuer()
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS
        val overrides = requireNotNull(example.runtimeOverrides)
        val inventories = listOf(
            listOf(tokenStatus(7, "https://status.example.com/list"), tokenStatus(7, "https://status.example.com:443/list")),
            listOf(tokenStatus(7, "https://status.example.com:70000/list")),
        )
        for (inventory in inventories) {
            val request = example.copy(runtimeOverrides = overrides.copy(credentialStatus = JsonArray(inventory)))
            val response = fixture.client.post("/issuer2/credential-offers") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }
        assertTrue(fixture.repository.list().isEmpty())
    }

    @Test
    fun signedMdocsAcrossProfilesUseDistinctCanonicalReferences() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS
        val url = "https://status.example.com/list"
        val aliasUrl = "HTTPS://STATUS.EXAMPLE.COM:443/list"
        val secondProfile = "otherMdocProfile"
        val fixture = installStatusIssuer(
            profileDefault = example.profileId to JsonArray(listOf(tokenStatus(7, aliasUrl), tokenStatus(8, aliasUrl))),
            additionalProfile = secondProfile to example.profileId,
        )
        val first = example.copy(runtimeOverrides = null)
        val second = example.copy(profileId = secondProfile, runtimeOverrides = example.runtimeOverrides!!.copy(
            credentialStatus = JsonArray(listOf(tokenStatus(7, url), tokenStatus(9, url), tokenStatus(10, url))),
        ))
        for ((request, indexes) in listOf(first to listOf(7, 8), second to listOf(9, 10))) {
            val offer = fixture.wallet.resolve(fixture.client.createCredentialOffer(request))
            val configurationId = offer.offer.credentialConfigurationIds.single()
            val token = fixture.wallet.exchangePreAuthorizedCode(offer, null)
            val response = fixture.issue(offer, token.access_token,
                credentialRequest(configurationId, fixture.twoProofs(offer, configurationId)))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertPreallocatedStatuses(response.body(), "mso_mdoc", indexes.map { tokenStatus(it, url) })
        }
    }

    @Test
    fun unencodableReservedMdocStatusFailsTheWholeCredentialRequest() = testApplication {
        val fixture = installStatusIssuer(statusRepository = object : CredentialStatusConsumptionRepository {
            override val durable = true
            override suspend fun reserve(
                profileId: String, operationId: String, holderKeys: List<String>,
                groups: List<CredentialStatusConfiguration.Group>, expiresAt: Instant,
            ) = listOf(groups.first().statuses, JsonArray(listOf(tokenStatus(8, "https://status.example.com:70000/list"))))
        })
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS
        val receipt = fixture.client.createCredentialOffer(example)
        val offer = fixture.wallet.resolve(receipt)
        val configurationId = offer.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(offer, null)
        val response = fixture.issue(offer, token.access_token,
            credentialRequest(configurationId, fixture.twoProofs(offer, configurationId)))
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.body<JsonObject>()
        assertEquals("server_error", body["error"]?.jsonPrimitive?.content)
        assertNull(body["credentials"])
        assertTrue(fixture.repository.get(receipt.offerId)!!.issuanceResults.isEmpty())
    }

    private fun verifyPreallocatedStatus(
        example: CredentialOfferCreateRequest,
        profileDefault: Boolean = false,
    ) = testApplication {
        val groups = example.runtimeOverrides!!.credentialStatus!!.jsonArray
        val fixture = installStatusIssuer(
            profileDefault = if (profileDefault) example.profileId to groups else null,
        )
        val request = if (profileDefault) example.copy(runtimeOverrides = null) else example
        val offer = fixture.client.createCredentialOffer(request)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val format = resolved.issuerMetadata.getCredentialConfiguration(configurationId)!!.format.value
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val bad = fixture.issue(resolved, token.access_token, credentialRequest(configurationId,
            fixture.twoProofs(resolved, configurationId).copy(jwt = listOf("invalid-proof"))))
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        repeat(2) { batch ->
            val response = fixture.issue(resolved, token.access_token,
                credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId)))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertPreallocatedStatuses(response.body(), format, groups.drop(batch * 2).take(2))
        }
        val exhausted = fixture.issue(resolved, token.access_token,
            credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId)))
        assertEquals(HttpStatusCode.BadRequest, exhausted.status)
        assertEquals("invalid_credential_request", exhausted.body<JsonObject>()["error"]!!.jsonPrimitive.content)

        // Failed two-copy reservation leaves the final group available to a separate offer/session.
        val nextOffer = fixture.client.createCredentialOffer(request)
        val nextResolved = fixture.wallet.resolve(nextOffer)
        val nextToken = fixture.wallet.exchangePreAuthorizedCode(nextResolved, null)
        val singleProof = fixture.twoProofs(nextResolved, configurationId).let { it.copy(jwt = it.jwt!!.take(1)) }
        val last = fixture.issue(nextResolved, nextToken.access_token, credentialRequest(configurationId, singleProof))
        assertEquals(HttpStatusCode.OK, last.status, last.bodyAsText())
        assertPreallocatedStatuses(last.body(), format, groups.takeLast(1))

        val public = fixture.client.get("/issuer2/sessions/" + offer.offerId).body<JsonObject>()
        assertFalse("credentialStatus" in public)
        assertFalse("credentialStatusIssuance" in public)
    }

    @Test
    fun offerlessAndAuthorizedSdJwtOffersUseTheProfileStatusGroups() = verifyProfileAuthorization(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS,
        Issuer2CredentialScenarios.identitySdJwt,
    )

    @Test
    fun offerlessAndAuthorizedW3cOffersKeepStatusPurposesTogether() = verifyProfileAuthorization(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_W3C_STATUS,
        Issuer2CredentialScenarios.openBadgeCredential,
    )

    @Test
    fun offerlessAndAuthorizedMdocOffersUseTheProfileStatusGroups() = verifyProfileAuthorization(
        Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS,
        Issuer2CredentialScenarios.isoMdl,
    )

    private fun verifyProfileAuthorization(
        example: CredentialOfferCreateRequest,
        scenario: Issuer2CredentialScenario,
    ) = testApplication {
        val groups = example.runtimeOverrides!!.credentialStatus!!.jsonArray
        val fixture = installStatusIssuer(profileDefault = example.profileId to groups)
        val metadata = IssuerMetadataResolver(fixture.client)
        val issuerMetadata = metadata.resolveCredentialIssuerMetadata("http://localhost/openid4vci").metadata
        val authorizationMetadata = metadata.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        val format = issuerMetadata.getCredentialConfiguration(scenario.credentialConfigurationId)!!.format.value
        val resolved = ResolvedCredentialOffer(
            CredentialOffer.withAuthorizationCodeGrant("http://localhost/openid4vci",
                listOf(scenario.credentialConfigurationId), null),
            issuerMetadata, authorizationMetadata,
        )
        for ((index, stateMode) in listOf(null, IssuerStateMode.OMIT, IssuerStateMode.INCLUDE).withIndex()) {
            val created = if (stateMode != null) {
                fixture.client.createCredentialOffer(example.copy(
                    authMethod = AuthenticationMethod.AUTHORIZED, issuerStateMode = stateMode, runtimeOverrides = null,
                ))
            } else null
            val requestMode = if (index == 1) Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS
                else Issuer2AuthorizationRequestMode.SCOPE
            val redirect = if (stateMode == IssuerStateMode.INCLUDE) {
                fixture.wallet.startAuthorizationCodeFlowWithIssuerState(
                    assertNotNull(created), fixture.wallet.resolve(created), scenario, requestMode,
                )
            } else fixture.wallet.startOfferlessAuthorizationCodeFlow(scenario, requestMode)
            val parameters = redirect.substringAfter("/external_login/").decodeExternalLoginAuthorizationParameters()
            val sessionId = parameters.getValue("_issuer2_session_id").single()
            val saved = assertNotNull(fixture.repository.get(sessionId))
            if (stateMode == IssuerStateMode.INCLUDE) assertEquals(created!!.offerId, sessionId)
            else assertNull(saved.credentialOffer, "Session was derived from the profile")
            val code = UUID.randomUUID().toString()
            fixture.authorizationCodes.save(DefaultAuthorizationCodeRecord(
                pkceBinding = id.walt.openid4vci.pkce.PkceBinding.NotUsed,
                code = code, clientId = "issuer2-wallet-test", redirectUri = "https://wallet.example/callback",
                grantedScopes = setOf(scenario.credentialConfigurationId), grantedAudience = emptySet(),
                session = DefaultSession(subject = sessionId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to saved.expiresAt)),
                expiresAt = saved.expiresAt,
            ))
            val token = fixture.wallet.exchangeAuthorizationCode(resolved, code)
            val count = if (index == 2) 1 else 2
            val proofs = fixture.twoProofs(resolved, scenario.credentialConfigurationId)
                .let { it.copy(jwt = it.jwt!!.take(count)) }
            if (count == 2) {
                assertInvalidProof(fixture.issue(resolved, token.access_token,
                    credentialRequest(scenario.credentialConfigurationId, proofs.repeatingFirstKey())))
            }
            val response = fixture.issue(resolved, token.access_token,
                credentialRequest(scenario.credentialConfigurationId, proofs))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertPreallocatedStatuses(response.body(), format, groups.drop(index * 2).take(count))
        }
    }

    @Test
    fun deferredBatchRejectsRepeatedKeysBeforeCreatingATransaction() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val fixture = installStatusIssuer()
        val offer = fixture.client.createCredentialOffer(example.copy(deferred = true))
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val proofs = fixture.twoProofs(resolved, configurationId)
        suspend fun issue(batch: Proofs) = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, batch))

        assertInvalidProof(issue(proofs.repeatingFirstKey()))
        assertTrue(fixture.repository.get(offer.offerId)!!.deferredState!!.transactions.isEmpty())
        val pending = issue(proofs)
        assertEquals(HttpStatusCode.Accepted, pending.status, pending.bodyAsText())
        val transaction = pending.body<JsonObject>().getValue("transaction_id")
        val approved = fixture.client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete")
        assertEquals(HttpStatusCode.NoContent, approved.status)
        val issued = fixture.client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", transaction) })
        }
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        assertPreallocatedStatuses(issued.body(), "dc+sd-jwt", example.runtimeOverrides!!.credentialStatus!!.jsonArray.take(2))
    }

    @Test
    fun deferredCapacityRejectionLetsTheWalletRetryWithFewerProofs() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val overrides = requireNotNull(example.runtimeOverrides)
        val groups = overrides.credentialStatus!!.jsonArray.take(1)
        val fixture = installStatusIssuer()
        val offer = fixture.client.createCredentialOffer(example.copy(
            deferred = true,
            runtimeOverrides = overrides.copy(credentialStatus = JsonArray(groups)),
        ))
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        suspend fun issue(proofs: Proofs) =
            fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs))

        val pending = issue(fixture.twoProofs(resolved, configurationId))
        assertEquals(HttpStatusCode.Accepted, pending.status)
        val transaction = pending.body<JsonObject>().getValue("transaction_id")
        val approved = fixture.client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete")
        assertEquals(HttpStatusCode.NoContent, approved.status)
        suspend fun poll() = fixture.client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", transaction) })
        }

        val exhausted = poll()
        assertEquals(HttpStatusCode.BadRequest, exhausted.status)
        assertEquals("invalid_credential_request", exhausted.body<JsonObject>().getValue("error").jsonPrimitive.content)
        assertEquals("invalid_transaction_id", poll().body<JsonObject>().getValue("error").jsonPrimitive.content)
        val issued = issue(fixture.wallet.buildJwtProofs(resolved.issuerMetadata, configurationId))
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        assertPreallocatedStatuses(issued.body(), "dc+sd-jwt", groups)
    }

    @Test
    fun reservationFailureReturnsASafeErrorAndAllowsRetry() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val ledger = ConfiguredCredentialStatusConsumptionRepository(
            InMemoryPersistence<CredentialStatusConsumption>("status-storage-failure", Duration.INFINITE),
            durable = true,
        )
        var failReservation = true
        val repository = object : CredentialStatusConsumptionRepository by ledger {
            override suspend fun reserve(
                profileId: String,
                operationId: String,
                holderKeys: List<String>,
                groups: List<CredentialStatusConfiguration.Group>,
                expiresAt: Instant,
            ): List<JsonArray> {
                if (failReservation) error("Private status backend diagnostics")
                return ledger.reserve(profileId, operationId, holderKeys, groups, expiresAt)
            }
        }
        val fixture = installStatusIssuer(statusRepository = repository)
        val offer = fixture.client.createCredentialOffer(example)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val request = credentialRequest(configurationId, fixture.twoProofs(resolved, configurationId))
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

        val failed = fixture.issue(resolved, token.access_token, request)
        assertEquals(HttpStatusCode.InternalServerError, failed.status)
        val publicError = failed.body<JsonObject>()
        assertEquals("server_error", publicError.getValue("error").jsonPrimitive.content)
        assertEquals("Credential request processing failed", publicError.getValue("error_description").jsonPrimitive.content)
        val event = events.replayCache.single { it.target == offer.offerId && it.error == "server_error" }
        assertEquals("Credential request processing failed", event.errorDescription)

        failReservation = false
        val issued = fixture.issue(resolved, token.access_token, request)
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        assertPreallocatedStatuses(issued.body(), "dc+sd-jwt", example.runtimeOverrides!!.credentialStatus!!.jsonArray.take(2))
    }

    @Test
    fun immediateCapacityCleanupFailureReturnsASafeErrorAndKeepsTheLastStatusAvailable() = testApplication {
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val overrides = requireNotNull(example.runtimeOverrides)
        val lastGroup = overrides.credentialStatus!!.jsonArray.take(1)
        val offerRequest = example.copy(runtimeOverrides = overrides.copy(credentialStatus = JsonArray(lastGroup)))
        var cleanupFailed = false
        val sessionStorage = JsonSessions { expected, replacement ->
            if (!cleanupFailed && expected?.credentialStatusIssuance?.operations?.isNotEmpty() == true &&
                replacement.credentialStatusIssuance?.operations?.isEmpty() == true &&
                replacement.issuanceResults.isEmpty()
            ) {
                cleanupFailed = true
                error("Private session-store diagnostics")
            }
        }
        val fixture = installStatusIssuer(sessionStorage = sessionStorage)
        val offer = fixture.client.createCredentialOffer(offerRequest)
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val proofs = fixture.twoProofs(resolved, configurationId)
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

        val failed = fixture.issue(resolved, token.access_token, credentialRequest(configurationId, proofs))
        assertTrue(cleanupFailed, "Capacity rejection must reach the session cleanup")
        assertEquals(HttpStatusCode.InternalServerError, failed.status)
        assertEquals(buildJsonObject {
            put("error", "server_error")
            put("error_description", "Credential request processing failed")
        }, failed.body<JsonObject>())
        val event = events.replayCache.single { it.target == offer.offerId && it.error == "server_error" }
        assertEquals("Credential request processing failed", event.errorDescription)

        val nextOffer = fixture.wallet.resolve(fixture.client.createCredentialOffer(offerRequest))
        val nextToken = fixture.wallet.exchangePreAuthorizedCode(nextOffer, null)
        val singleProof = fixture.wallet.buildJwtProofs(nextOffer.issuerMetadata, configurationId)
        val issued = fixture.issue(nextOffer, nextToken.access_token, credentialRequest(configurationId, singleProof))
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        assertPreallocatedStatuses(issued.body(), "dc+sd-jwt", lastGroup)
    }

    private fun Proofs.repeatingFirstKey() = copy(jwt = List(2) { jwt!!.first() })

    private suspend fun assertInvalidProof(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertEquals("invalid_proof", response.body<JsonObject>().getValue("error").jsonPrimitive.content)
    }

    @Test
    fun fixedAndPreallocatedItemsRespectTheSameSessionLease() = testApplication {
        val fixture = installStatusIssuer()
        val fixed = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS
        val preallocated = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val offer = fixture.client.createCredentialOffer(MultiCredentialOfferCreateRequest(
            credentials = listOf(
                CredentialOfferCredential(fixed.profileId, fixed.runtimeOverrides),
                CredentialOfferCredential(preallocated.profileId, preallocated.runtimeOverrides),
            ), authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        ))
        val resolved = fixture.wallet.resolve(offer)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val token = fixture.wallet.exchangePreAuthorizedCode(resolved, null)
        val requests = fixture.repository.get(offer.offerId)!!.issuanceRequests
        val sessions = IssuanceSessionService(fixture.repository)
        val held = assertNotNull(sessions.claimCredentialStatusSession(offer.offerId))
        val blocked = fixture.issue(resolved, token.access_token, credentialRequestByIdentifier(
            requests.first().credentialIdentifier, fixture.twoProofs(resolved, configurationId),
        ))
        assertEquals(HttpStatusCode.BadRequest, blocked.status)
        assertEquals(held.credentialStatusIssuance!!.leaseId,
            fixture.repository.get(offer.offerId)!!.credentialStatusIssuance!!.leaseId)
        sessions.releaseCredentialStatusSession(held)
        val fixedResponse = fixture.issue(resolved, token.access_token, credentialRequestByIdentifier(
            requests.first().credentialIdentifier, fixture.twoProofs(resolved, configurationId),
        ))
        assertEquals(HttpStatusCode.OK, fixedResponse.status, fixedResponse.bodyAsText())
        val fixedCredentialResponse = fixedResponse.body<JsonObject>()
        assertStatuses(fixedCredentialResponse, "dc+sd-jwt", fixed.runtimeOverrides!!.credentialStatus!!)
        assertNull(fixture.repository.get(offer.offerId)!!.credentialStatusIssuance!!.leaseId)
        val notificationId = fixedCredentialResponse.getValue("notification_id")
        val allocationClaim = assertNotNull(sessions.claimCredentialStatusSession(offer.offerId))
        val reported = fixture.client.post(assertNotNull(resolved.issuerMetadata.notificationEndpoint)) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("notification_id", notificationId)
                put("event", "credential_accepted")
            })
        }
        assertEquals(HttpStatusCode.NoContent, reported.status, reported.bodyAsText())
        sessions.releaseCredentialStatusSession(allocationClaim)

        val allocatedResponse = fixture.issue(resolved, token.access_token, credentialRequestByIdentifier(
            requests.last().credentialIdentifier, fixture.twoProofs(resolved, configurationId),
        ))
        assertEquals(HttpStatusCode.OK, allocatedResponse.status, allocatedResponse.bodyAsText())
        assertPreallocatedStatuses(allocatedResponse.body(), "dc+sd-jwt",
            preallocated.runtimeOverrides!!.credentialStatus!!.jsonArray.take(2))
        val storedReport = fixture.repository.get(offer.offerId)!!.issuanceResults
            .getValue(requests.first().credentialIdentifier).walletNotification
        assertEquals(notificationId.jsonPrimitive.content, storedReport?.notificationId)
        assertEquals("credential_accepted", storedReport?.event?.wireValue)
    }

    private fun assertPreallocatedStatuses(response: JsonObject, format: String, expected: List<JsonElement>) {
        val credentials = response.getValue("credentials").jsonArray
        assertEquals(expected.size, credentials.size)
        credentials.forEachIndexed { index, credential ->
            assertStatuses(buildJsonObject { put("credentials", JsonArray(listOf(credential))) }, format, expected[index], count = 1)
        }
    }

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

        val repeated = fixture.wallet.buildJwtProofs(resolved.issuerMetadata, configurationId).repeatingFirstKey()
        assertInvalidProof(fixture.issue(resolved, token.access_token, credentialRequest(configurationId, repeated)))

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
                pkceBinding = id.walt.openid4vci.pkce.PkceBinding.NotUsed,
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
        additionalProfile: Pair<String, String>? = null,
        statusRepository: CredentialStatusConsumptionRepository = ConfiguredCredentialStatusConsumptionRepository(
            InMemoryPersistence<CredentialStatusConsumption>("preallocated-status-test", Duration.INFINITE),
            durable = true,
        ),
        sessionStorage: JsonSessions = JsonSessions(),
    ): Fixture {
        loadIssuer2ConfigFiles()
        // A test backend alias exercises the legacy provider branch without weakening JWK migration.
        if (legacy) KeyManager.types[LEGACY_BACKEND] = typeOf<JWKKey>()
        val configuredProfiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val profiles = configuredProfiles.profiles.mapValues { (id, profile) ->
            profile.copy(
                issuerKey = if (legacy) JsonObject(profile.issuerKey + ("type" to JsonPrimitive(LEGACY_BACKEND))) else profile.issuerKey,
                credentialStatus = profileDefault?.takeIf { it.first == id }?.second ?: profile.credentialStatus,
            )
        }.toMutableMap()
        additionalProfile?.let { (alias, source) -> profiles[alias] = profiles.getValue(source) }
        val repository = ConfiguredIssuanceSessionRepository(
            sessions = sessionStorage,
            crypto2Keys = InMemoryPersistence("status-test-keys", 5.minutes),
        )
        val attestation = createIssuer2ClientAttestationTestMaterial()
        val module = Issuer2Module(
            ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
                clientAuthenticationConfig = attestation.clientAuthenticationConfig,
                batchCredentialIssuance = batchSize?.let(::BatchCredentialIssuance),
            ),
            ConfigManager.getConfig(), configuredProfiles.copy(profiles = profiles), issuanceSessionRepository = repository,
            credentialStatusConsumptionRepository = statusRepository,
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

    private class JsonSessions(
        private val beforeCompareAndSet: (IssuanceSession?, IssuanceSession) -> Unit = { _, _ -> },
    ) : Persistence<IssuanceSession>("status-test-sessions", 5.minutes) {
        private val raw = InMemoryPersistence<String>(discriminator, 5.minutes)
        override fun get(id: String) = raw[id]?.let(IssuanceSessionStorageCodec::decode)
        override fun getAndRemove(id: String) = raw.getAndRemove(id)?.let(IssuanceSessionStorageCodec::decode)
        override fun set(id: String, value: IssuanceSession) = set(id, value, null)
        override fun set(id: String, value: IssuanceSession, ttl: Duration?) = raw.set(id, Json.encodeToString(value), ttl)
        override fun compareAndSet(id: String, expected: IssuanceSession?, replacement: IssuanceSession, ttl: Duration?): Boolean {
            val stored = raw[id]
            val current = stored?.let(IssuanceSessionStorageCodec::decode)
            if (Json.encodeToString(current) != Json.encodeToString(expected)) return false
            beforeCompareAndSet(expected, replacement)
            return raw.compareAndSet(id, stored, Json.encodeToString(replacement), ttl)
        }
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
