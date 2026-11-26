package id.walt.issuer2.openid4vci

import id.walt.credentials.issuance.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.credentials.issuance.InstantClock
import id.walt.credentials.issuance.IssuanceClock
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.KeyType
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCredential
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.testsupport.Issuer2AuthorizationRequestMode
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.MetadataCertificateFixture
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertIsoMdlCredentialPayload
import id.walt.issuer2.testsupport.assertRefreshToken
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createIssuer2ClientAttestationTestMaterial
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.listSessions
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.issuer2.testsupport.mdocValidityInfo
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.openid4vci.mdoc.MsoData
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class Issuer2MsoDataWalletFlowTest {

    @AfterEach
    fun tearDown() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun issuedMdocUsesDefaultValidityWhenMsoDataIsOmitted() = testApplication {
        val validity = issueIsoMdl()
        assertEquals(validity.signed.epochSeconds, validity.validFrom.epochSeconds)
        assertTrue(validity.validUntil - validity.signed in (364.days..366.days))
        assertTwelveHourUtcBoundary(validity.validUntil)
        assertNull(validity.expectedUpdate)
    }

    @Test
    fun issuedMdocUsesRuntimeMsoDataFunctions() = testApplication {
        val now = Clock.System.now()
        val validity = issueIsoMdl(
            CredentialOfferRuntimeOverrides(
                msoData = MsoData(
                    validFrom = "<timestamp>",
                    validUntil = "<timestamp-in:365d>",
                    expectedUpdate = "<timestamp-in:180d>",
                )
            )
        )

        assertTrue(validity.validFrom - now in (-2.days..2.days))
        assertTrue(validity.validUntil - validity.validFrom in (364.days..366.days))
        assertTrue(assertNotNull(validity.expectedUpdate) - validity.validFrom in (179.days..181.days))
    }

    @Test
    fun issuedMdocUsesStaticRuntimeMsoData() = testApplication {
        val validFrom = Clock.System.now().plus(1.days)
        val validUntil = validFrom.plus(365.days * 5)
        val expectedUpdate = validFrom.plus(180.days)
        val validity = issueIsoMdl(
            CredentialOfferRuntimeOverrides(
                msoData = MsoData(
                    validFrom = validFrom.toString(),
                    validUntil = validUntil.toString(),
                    expectedUpdate = expectedUpdate.toString(),
                )
            )
        )

        assertEquals(validFrom.epochSeconds, validity.validFrom.epochSeconds)
        assertEquals(validUntil.epochSeconds, validity.validUntil.epochSeconds)
        assertEquals(expectedUpdate.epochSeconds, validity.expectedUpdate?.epochSeconds)
    }

    @Test
    fun issuedMdocAppliesNamespaceDateMapping() = testApplication {
        val credentialPayload = issueIsoMdlCredential(
            CredentialOfferRuntimeOverrides(
                mapping = buildJsonObject {
                    putJsonObject(ISO_MDL_NAMESPACE) {
                        put("issue_date", JsonPrimitive("<date>"))
                        put("expiry_date", JsonPrimitive("<date-in:365d>"))
                        put("issuing_authority", JsonPrimitive("<issuerId>"))
                    }
                }
            )
        )
        val namespace = mdocNamespaceJson(credentialPayload, ISO_MDL_NAMESPACE)
        val issueDate = assertNotNull(namespace["issue_date"]).jsonPrimitive.content
        val expiryDate = assertNotNull(namespace["expiry_date"]).jsonPrimitive.content
        assertEquals(10, issueDate.length, issueDate)
        assertEquals(10, expiryDate.length, expiryDate)
        assertTrue(expiryDate > issueDate)
        assertTrue(assertNotNull(namespace["issuing_authority"]).jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun offerRejectsAgeOverrideInconsistentWithInheritedBirthDateAndValidity() = testApplication {
        installIssuer2WithConfigFiles(configureProfilesConfig = { config ->
            val id = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID
            val profile = config.profiles.getValue(id).copy(msoData = MsoData(validFrom = "2027-08-25T00:00:00Z"))
            config.copy(profiles = config.profiles + (id to profile))
        })
        val client = apiClient()
        val response = client.post("/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(
                CredentialOfferCreateRequest(
                    profileId = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID,
                    authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        credentialData = buildJsonObject {
                            putJsonObject(ISO_MDL_NAMESPACE) { put("age_in_years", 38) }
                        },
                    ),
                ),
            )
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.BadRequest, response.status, body)
        assertTrue(body.contains("credentialData.$ISO_MDL_NAMESPACE.age_in_years must be 39"), body)
        assertTrue(client.listSessions().isEmpty())
    }

    @Test
    fun signedMdlAgeUsesTheUtcValidityDateWhenAnOfferCrossesABirthday() = testApplication {
        val beforeBirthday = Instant.parse("2027-08-24T23:59:59Z")
        val birthday = Instant.parse("2027-08-25T00:00:00Z")
        var issuedAt = Instant.parse("2026-11-25T12:00:00Z")
        val clock = object : Clock {
            override fun now(): Instant = issuedAt
        }
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                withContext(IssuanceClock(clock)) { proceed() }
            }
        }

        val certificate = MetadataCertificateFixture(
            notBefore = java.time.Instant.parse("2026-11-01T00:00:00Z"),
            notAfter = java.time.Instant.parse("2029-01-01T00:00:00Z"),
        ).config()
        val key = JWKKey.importPEM(certificate.privateKeyPem).getOrThrow().exportJWKObject()
        installIssuer2WithConfigFiles(configureProfilesConfig = { config ->
            val id = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID
            val profile = config.profiles.getValue(id).copy(
                issuerKey = buildJsonObject {
                    put("type", "jwk")
                    put("jwk", key)
                },
                x5Chain = certificate.certificateChainPem,
            )
            config.copy(profiles = config.profiles + (id to profile))
        })

        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        suspend fun offer(overrides: CredentialOfferRuntimeOverrides? = null) = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.isoMdl,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
            runtimeOverrides = overrides,
        )

        suspend fun redeem(offer: CredentialOfferCreateResponse, age: Int, validFrom: Instant) {
            val resolved = walletFlow.resolve(offer)
            val token = walletFlow.exchangePreAuthorizedCode(resolved, txCode = null)
            val payload = walletFlow.requestCredential(resolved, token.access_token, includeDidInProof = false)
            assertIsoMdlCredentialPayload(payload)
            assertEquals(validFrom, mdocValidityInfo(payload).validFrom)
            val issuerSigned = decodeIssuerSigned(payload)
            val namespace = issuerSigned.namespacesToJson().getValue(ISO_MDL_NAMESPACE).jsonObject
            assertEquals(JsonPrimitive(age), namespace["age_in_years"])
            assertEquals(JsonPrimitive("AT"), namespace["nationality"])
            val signedElements = assertNotNull(issuerSigned.namespaces?.get(ISO_MDL_NAMESPACE)).entries
            assertTrue(signedElements.none { it.value.elementIdentifier.startsWith("biometric_template_") })
        }

        val crossingBirthday = offer()
        redeem(offer(), age = 38, validFrom = issuedAt)
        issuedAt = beforeBirthday
        redeem(
            offer(CredentialOfferRuntimeOverrides(msoData = MsoData(validFrom = beforeBirthday.toString()))),
            age = 38,
            validFrom = beforeBirthday,
        )
        redeem(
            offer(CredentialOfferRuntimeOverrides(msoData = MsoData(validFrom = birthday.toString()))),
            age = 39,
            validFrom = birthday,
        )
        issuedAt = birthday
        redeem(crossingBirthday, age = 39, validFrom = birthday)
        redeem(
            offer(CredentialOfferRuntimeOverrides(
                mapping = buildJsonObject {
                    putJsonObject(ISO_MDL_NAMESPACE) {
                        put("age_in_years", "<age-in-years>")
                        put("birth_date", "1990-08-26")
                    }
                },
            )),
            age = 36,
            validFrom = birthday,
        )
    }

    @Test
    fun offerRejectsMsoValidityFieldsInMapping() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val response = client.post("/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(
                CredentialOfferCreateRequest(
                    profileId = Issuer2CredentialScenarios.isoMdl.profileId,
                    authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        mapping = buildJsonObject {
                            put("validFrom", JsonPrimitive("<timestamp-before:30d>"))
                            put("validUntil", JsonPrimitive("<timestamp-in:10d>"))
                        },
                    ),
                ),
            )
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.BadRequest, response.status, body)
        assertTrue(body.contains("msoData.validFrom"), body)
        assertTrue(client.listSessions().isEmpty())
    }

    @Test
    fun authorizedMdocOfferAllowsANamespaceSuppliedByExternalIdentityClaims() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val scenario = Issuer2CredentialScenarios.isoMdl
        val mapping = buildJsonObject {
            putJsonObject(ISO_MDL_NAMESPACE) { put("issuing_authority", "<issuerId>") }
        }
        val offer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.AUTHORIZED,
            issuerStateMode = IssuerStateMode.INCLUDE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialData = buildJsonObject { put(ISO_MDL_NAMESPACE, "pending") },
                mapping = mapping,
                idTokenClaimsMapping = mapOf("$.mdl" to "$.['$ISO_MDL_NAMESPACE']"),
            ),
        )

        val issuance = client.getSession(offer.offerId).issuanceRequests.single()
        val claims = buildJsonObject {
            putJsonObject("mdl") { put("given_name", "Jane") }
        }
        val populatedData = JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
            source = claims,
            destination = issuance.credentialData,
            jsonPathMapConfig = assertNotNull(issuance.idTokenClaimsMapping),
        )
        assertEquals(claims.getValue("mdl"), populatedData.getValue(ISO_MDL_NAMESPACE))
        assertEquals(mapping, assertNotNull(issuance.mapping).mdocNamespaceMapping(populatedData))
    }

    @Test
    fun preAuthorizedOfferRejectsBirthDateAvailableOnlyFromLogin() = testApplication {
        pinMdlIssuanceClock()
        val codeStore = ConfiguredPreAuthorizedCodeRepository()
        var savedCodes = 0
        val recordingStore = object : PreAuthorizedCodeRepository by codeStore {
            override suspend fun save(record: PreAuthorizedCodeRecord) {
                codeStore.save(record)
                savedCodes++
            }
        }
        installIssuer2WithConfigFiles(preAuthorizedCodeRepository = recordingStore, configureProfilesConfig = { config ->
            val id = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID
            val profile = config.profiles.getValue(id)
            val namespace = profile.credentialData.getValue(ISO_MDL_NAMESPACE).jsonObject
            config.copy(profiles = config.profiles + (id to profile.copy(
                credentialData = JsonObject(profile.credentialData + (ISO_MDL_NAMESPACE to JsonObject(namespace - "birth_date"))),
                idTokenClaimsMapping = mapOf("$.birthdate" to "$.['$ISO_MDL_NAMESPACE'].birth_date"),
                msoData = MsoData(validFrom = AGE_VALID_FROM.toString()),
            )))
        })
        val client = apiClient()
        val id = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID
        val birthDateOverride = CredentialOfferRuntimeOverrides(
            credentialData = buildJsonObject {
                putJsonObject(ISO_MDL_NAMESPACE) { put("birth_date", "1990-08-26") }
            },
        )
        val requests = listOf(
            CredentialOfferCreateRequest(profileId = id, authMethod = AuthenticationMethod.PRE_AUTHORIZED),
            MultiCredentialOfferCreateRequest(
                credentials = listOf(CredentialOfferCredential(id, birthDateOverride), CredentialOfferCredential(id)),
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            ),
        )
        for (request in requests) {
            val response = client.post("/issuer2/credential-offers") {
                contentType(ContentType.Application.Json)
                when (request) {
                    is CredentialOfferCreateRequest -> setBody(request)
                    is MultiCredentialOfferCreateRequest -> setBody(request)
                }
            }
            val body = response.bodyAsText()
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertTrue(body.contains("credentialData.$ISO_MDL_NAMESPACE.birth_date"), body)
            assertTrue(body.contains("profile or runtime overrides"), body)
            assertTrue(client.listSessions().isEmpty())
            assertEquals(0, savedCodes)
        }
        val offer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.isoMdl,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
            runtimeOverrides = birthDateOverride,
        )
        assertEquals(1, savedCodes)
        val wallet = Issuer2WalletFlowDriver(client)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val payload = wallet.requestCredential(resolved, token.access_token, includeDidInProof = false)
        assertSignedBirthDateAndAge(payload)
    }

    @Test
    fun authorizedMdlUsesBirthDateFromLoginCallback() = testApplication {
        issueMdlWithLoginClaims(wholeNamespace = false)
    }

    @Test
    fun authorizedMdlUsesWholeNamespaceFromLoginCallback() = testApplication {
        issueMdlWithLoginClaims(wholeNamespace = true)
    }

    private suspend fun ApplicationTestBuilder.issueMdlWithLoginClaims(wholeNamespace: Boolean) {
        pinMdlIssuanceClock()
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val claims = buildJsonObject {
            put("sub", "test-subject")
            put("birthdate", "1990-08-26")
            putJsonObject("mdl") {
                put("given_name", "Jane")
                put("birth_date", "1990-08-26")
            }
        }
        val idToken = JWKKey.generate(KeyType.secp256r1).signJws(claims.toString().encodeToByteArray())
        val oauth = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/authorize") {
                    val request = call.request.queryParameters
                    val callback = URLBuilder(assertNotNull(request["redirect_uri"])).apply {
                        parameters.append("state", assertNotNull(request["state"]))
                        parameters.append("code", "local-login")
                    }
                    call.respondRedirect(callback.buildString())
                }
                post("/token") {
                    assertEquals("local-login", call.receiveParameters()["code"])
                    call.respondText(
                        buildJsonObject {
                            put("access_token", "local-access-token")
                            put("token_type", "Bearer")
                            put("id_token", idToken)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)
        try {
            val oauthUrl = "http://127.0.0.1:${oauth.engine.resolvedConnectors().single().port}"
            val claimsMapping = if (wholeNamespace) {
                mapOf("$.mdl" to "$.['$ISO_MDL_NAMESPACE']")
            } else {
                mapOf("$.birthdate" to """$["$ISO_MDL_NAMESPACE"]["birth_date"]""")
            }
            installIssuer2WithConfigFiles(
                configureServiceConfig = { it.copy(clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig) },
                configureAuthenticationConfig = {
                    it.copy(authorizeUrl = "$oauthUrl/authorize", accessTokenUrl = "$oauthUrl/token", clientSecret = "local-test")
                },
                configureProfilesConfig = { config ->
                    val id = Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID
                    val profile = config.profiles.getValue(id)
                    val namespace = profile.credentialData.getValue(ISO_MDL_NAMESPACE).jsonObject
                    val credentialData = if (wholeNamespace) {
                        JsonObject(profile.credentialData - ISO_MDL_NAMESPACE)
                    } else {
                        JsonObject(profile.credentialData + (ISO_MDL_NAMESPACE to JsonObject(namespace - "birth_date")))
                    }
                    config.copy(profiles = config.profiles + (id to profile.copy(
                        credentialData = credentialData,
                        idTokenClaimsMapping = claimsMapping,
                        msoData = MsoData(validFrom = AGE_VALID_FROM.toString()),
                    )))
                },
            )
            val client = apiClient()
            val scenario = Issuer2CredentialScenarios.isoMdl
            val wallet = Issuer2WalletFlowDriver(client, attestationAssembler = clientAttestation.attestationAssembler)
            val offer = client.createWalletFlowCredentialOffer(
                scenario = scenario,
                authenticationMethod = AuthenticationMethod.AUTHORIZED,
                issuerStateMode = IssuerStateMode.INCLUDE,
            )
            val issuance = client.getSession(offer.offerId).issuanceRequests.single()
            assertEquals(claimsMapping, issuance.idTokenClaimsMapping)
            if (wholeNamespace) {
                assertNull(issuance.credentialData[ISO_MDL_NAMESPACE])
            } else {
                assertNull(issuance.credentialData.getValue(ISO_MDL_NAMESPACE).jsonObject["birth_date"])
            }
            val resolved = wallet.resolve(offer)
            val login = wallet.startAuthorizationCodeFlowWithIssuerState(offer, resolved, scenario)
            val providerUrl = client.get(login).redirectLocation()
            val callback = HttpClient { followRedirects = false }.use { provider ->
                provider.get(providerUrl).redirectLocation()
            }
            val walletRedirect = client.get(callback).redirectLocation()
            val code = assertNotNull(Url(walletRedirect).parameters["code"], walletRedirect)
            val token = wallet.exchangeAuthorizationCode(resolved, code)
            val credential = wallet.requestCredential(resolved, token.access_token, includeDidInProof = false)
            assertSignedBirthDateAndAge(credential)
        } finally {
            oauth.stop(500, 500)
        }
    }

    private fun ApplicationTestBuilder.pinMdlIssuanceClock() {
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                withContext(IssuanceClock(InstantClock(AGE_VALID_FROM - 1.days))) { proceed() }
            }
        }
    }

    private suspend fun HttpResponse.redirectLocation(): String {
        assertEquals(HttpStatusCode.Found, status, bodyAsText())
        return assertNotNull(headers[HttpHeaders.Location])
    }

    private fun assertSignedBirthDateAndAge(payload: JsonObject) {
        assertEquals(AGE_VALID_FROM, mdocValidityInfo(payload).validFrom)
        val namespace = mdocNamespaceJson(payload, ISO_MDL_NAMESPACE)
        assertEquals(JsonPrimitive("1990-08-26"), namespace["birth_date"])
        assertEquals(JsonPrimitive(36), namespace["age_in_years"])
    }

    @Test
    fun issuedMdocUsesProfileMsoDataWhenOfferOmitsOverrides() = testApplication {
        val now = Clock.System.now()
        val validity = issueIsoMdl(configureProfiles = withIsoMdlMsoData())
        assertTrue(validity.validFrom - now in (-2.days..2.days))
        assertTrue(validity.validUntil - validity.validFrom in (364.days..366.days))
        assertTrue(assertNotNull(validity.expectedUpdate) - validity.validFrom in (179.days..181.days))
    }

    @Test
    fun offerJsonNullExpectedUpdateClearsProfileValue() = testApplication {
        val validity = issueIsoMdl(
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                msoData = MsoData(expectedUpdateCleared = true),
            ),
            configureProfiles = withIsoMdlMsoData(),
        )
        assertNull(validity.expectedUpdate)
        assertTrue(validity.validUntil - validity.validFrom in (364.days..366.days))
    }

    @Test
    fun offerlessScopeSessionCopiesProfileMsoData() = testApplication {
        assertOfferlessSessionCopiesProfileMsoData(Issuer2AuthorizationRequestMode.SCOPE)
    }

    @Test
    fun offerlessAuthorizationDetailsSessionCopiesProfileMsoData() = testApplication {
        assertOfferlessSessionCopiesProfileMsoData(Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS)
    }

    @Test
    fun authorizationCodeOfferWithIssuerStateKeepsOfferMsoData() = testApplication {
        val scenario = Issuer2CredentialScenarios.isoMdl
        val offerMsoData = MsoData(validUntil = "<timestamp-in:730d>")
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.AUTHORIZED,
            issuerStateMode = IssuerStateMode.INCLUDE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(msoData = offerMsoData),
        )
        walletFlow.startAuthorizationCodeFlowWithIssuerState(
            createdOffer = createdOffer,
            resolvedOffer = walletFlow.resolve(createdOffer),
            scenario = scenario,
        )
        assertEquals(offerMsoData, client.getSession(createdOffer.offerId).issuanceRequests.single().msoData)
    }

    @Test
    fun authorizationCodeOfferWithoutIssuerStateCopiesProfileMsoData() = testApplication {
        val scenario = Issuer2CredentialScenarios.isoMdl
        installIssuer2WithConfigFiles(configureProfilesConfig = withIsoMdlMsoData())
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.AUTHORIZED,
            issuerStateMode = IssuerStateMode.OMIT,
        )
        walletFlow.startAuthorizationCodeFlowWithoutIssuerState(
            resolvedOffer = walletFlow.resolve(createdOffer),
            scenario = scenario,
        )
        val authorizationSession = client.listSessions().single { session ->
            session.issuanceRequests.single().profileId == scenario.profileId && session.sessionId != createdOffer.offerId
        }
        assertNotEquals(createdOffer.offerId, authorizationSession.sessionId)
        assertEquals(PROFILE_MSO_DATA, authorizationSession.issuanceRequests.single().msoData)
    }

    private suspend fun ApplicationTestBuilder.assertOfferlessSessionCopiesProfileMsoData(
        requestMode: Issuer2AuthorizationRequestMode,
    ) {
        val scenario = Issuer2CredentialScenarios.isoMdl
        installIssuer2WithConfigFiles(configureProfilesConfig = withIsoMdlMsoData())
        val client = apiClient()
        Issuer2WalletFlowDriver(client).startOfferlessAuthorizationCodeFlow(scenario, requestMode)
        val session = client.listSessions().single { it.issuanceRequests.single().profileId == scenario.profileId }
        assertEquals(PROFILE_MSO_DATA, session.issuanceRequests.single().msoData)
    }

    private suspend fun ApplicationTestBuilder.issueIsoMdl(
        runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
        configureProfiles: (Issuer2ProfilesConfig) -> Issuer2ProfilesConfig = { it },
    ): ValidityInfo = mdocValidityInfo(issueIsoMdlCredential(runtimeOverrides, configureProfiles))

    private suspend fun ApplicationTestBuilder.issueIsoMdlCredential(
        runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
        configureProfiles: (Issuer2ProfilesConfig) -> Issuer2ProfilesConfig = { it },
    ): JsonObject {
        val scenario = Issuer2CredentialScenarios.isoMdl
        installIssuer2WithConfigFiles(configureProfilesConfig = configureProfiles)
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
            runtimeOverrides = runtimeOverrides,
        )
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertRefreshToken(tokenResponse)

        val credentialPayload = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            includeDidInProof = false,
        )
        assertIsoMdlCredentialPayload(credentialPayload)
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
        return credentialPayload
    }

    private fun mdocNamespaceJson(credentialPayload: JsonObject, namespace: String): JsonObject =
        decodeIssuerSigned(credentialPayload).namespacesToJson().getValue(namespace).jsonObject

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeIssuerSigned(credentialPayload: JsonObject): IssuerSigned {
        val issuedCredentialBytes = credentialPayload.getValue("credentials")
            .let { it as JsonArray }
            .single()
            .jsonObject
            .getValue("credential")
            .jsonPrimitive
            .content
            .decodeFromBase64Url()
        return coseCompliantCbor.decodeFromByteArray<IssuerSigned>(issuedCredentialBytes)
    }

    private fun withIsoMdlMsoData(): (Issuer2ProfilesConfig) -> Issuer2ProfilesConfig = { config ->
        val isoMdl = config.profiles.getValue(Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID)
        config.copy(
            profiles = config.profiles + (Issuer2CredentialScenarios.ISO_MDL_PROFILE_ID to isoMdl.copy(msoData = PROFILE_MSO_DATA)),
        )
    }

    private companion object {
        const val ISO_MDL_NAMESPACE = "org.iso.18013.5.1"
        val AGE_VALID_FROM = Instant.parse("2027-08-25T00:00:00Z")
        val PROFILE_MSO_DATA = MsoData(
            validFrom = "<timestamp>",
            validUntil = "<timestamp-in:365d>",
            expectedUpdate = "<timestamp-in:180d>",
        )

        private fun assertTwelveHourUtcBoundary(instant: kotlin.time.Instant) {
            val text = instant.toString()
            assertTrue(
                text.endsWith("T00:00:00Z") || text.endsWith("T12:00:00Z"),
                "expected 12-hour UTC bucket, got $text",
            )
        }
    }
}
