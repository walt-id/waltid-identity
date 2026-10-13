package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.requests.credential.CredentialAuthorization
import id.walt.openid4vci.responses.token.authorizedByVerifiedToken
import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.validation.NotificationRequestValidator
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.requests.notification.*
import id.walt.openid4vci.responses.notification.NotificationResponseResult
import id.walt.openid4vci.tokens.access.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class NotificationProviderTest {
    private val request = DefaultNotificationRequest("notification-a", NotificationEvent.CREDENTIAL_ACCEPTED, "stored")
    private val records = listOf(
        IssuedCredentialNotification("a", "notification-a"),
        IssuedCredentialNotification("b", "notification-b"),
    )
    private val provider = buildOAuth2Provider(createTestConfig())

    @Test
    fun configuredValidatorRunsAfterVerificationAndSupportsCustomRequestImplementations() = runTest {
        val calls = mutableListOf<String>()
        val claims = buildJsonObject { put("sub", "session") }
        val customRequest = object : NotificationRequest {
            override val notificationId = "notification-a"
            override val event = NotificationEvent.CREDENTIAL_ACCEPTED
            override val eventDescription = "custom"
        }
        val parameters = mapOf("custom" to listOf("input"))
        val verifier = object : AccessTokenVerifier {
            override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?): JsonObject {
                calls += "verify"
                require(token == "valid")
                return claims
            }
        }
        val validator = NotificationRequestValidator { received ->
            calls += "validate"
            assertEquals(parameters, received)
            NotificationRequestValidationResult.Success(customRequest)
        }
        val provider = buildOAuth2Provider(createTestConfig(accessTokenVerifier = verifier).copy(notificationRequestValidator = validator))
        fun context(token: String) = ProtectedResourceAccessTokenContext(
            authorization = AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, token),
            expectedIssuer = "https://issuer.example",
        )
        val invalid = assertIs<NotificationRequestResult.OAuthFailure>(provider.createNotificationRequest(parameters, context("invalid")))
        assertEquals(OAuthErrorCodes.INVALID_TOKEN, invalid.error.error)
        assertEquals(listOf("verify"), calls)
        calls.clear()
        val success = assertIs<NotificationRequestResult.Success>(provider.createNotificationRequest(parameters, context("valid")))
        assertEquals(listOf("verify", "validate"), calls)
        assertSame(customRequest, success.request)
        assertSame(claims, success.tokenClaims)
        val response = assertIs<NotificationResponseResult.Success>(provider.createNotificationResponse(success.request, records, setOf("a")))
        assertEquals(IssuedCredentialNotificationUpdate.Changed("a", customRequest.event, "custom"), response.change)

        calls.clear()
        val unconfigured = buildOAuth2Provider(createTestConfig().copy(notificationRequestValidator = validator))
        assertIs<NotificationRequestResult.OAuthFailure>(unconfigured.createNotificationRequest(parameters, context("valid")))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun configuredValidatorFailureBecomesANotificationRequestFailure() = runTest {
        val error = NotificationError(NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST)
        val verifier = object : AccessTokenVerifier {
            override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?) = JsonObject(emptyMap())
        }
        val provider = buildOAuth2Provider(createTestConfig(accessTokenVerifier = verifier).copy(
            notificationRequestValidator = NotificationRequestValidator { NotificationRequestValidationResult.Failure(error) },
        ))
        val result = assertIs<NotificationRequestResult.Failure>(provider.createNotificationRequest(
            mapOf("notification_id" to listOf("notification-a"), "event" to listOf("credential_accepted")),
            ProtectedResourceAccessTokenContext(
                authorization = AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, "valid"),
                expectedIssuer = "https://issuer.example",
            ),
        ))
        assertSame(error, result.error)
    }

    @Test
    fun responseRequiresAuthorizationAndReturnsOnlyChangesWithoutMutatingRecords() {
        for ((id, authorized) in listOf("unknown" to setOf("a"), "notification-a" to setOf("b"), "notification-a" to emptySet())) {
            val failure = assertIs<NotificationResponseResult.Failure>(provider.createNotificationResponse(request.copy(notificationId = id), records, authorized))
            assertEquals(NotificationErrorCodes.INVALID_NOTIFICATION_ID, failure.error.error)
            assertEquals(400, provider.writeNotificationResponse(failure).status)
        }
        val first = assertIs<NotificationResponseResult.Success>(provider.createNotificationResponse(request, records, setOf("a")))
        assertEquals(IssuedCredentialNotificationUpdate.Changed("a", request.event, "stored"), first.change)
        assertNull(records.first().event)
        assertNull(records.last().event)
        val saved = listOf(records.first().copy(event = request.event, eventDescription = "stored"), records.last())
        val retry = assertIs<NotificationResponseResult.Success>(provider.createNotificationResponse(request, saved, setOf("a")))
        assertNull(retry.change)
        for (changed in listOf(request.copy(event = NotificationEvent.CREDENTIAL_FAILURE), request.copy(eventDescription = "changed"), request.copy(eventDescription = null))) {
            assertNotNull(assertIs<NotificationResponseResult.Success>(provider.createNotificationResponse(changed, saved, setOf("a"))).change)
        }
        assertEquals(204, provider.writeNotificationResponse(first).status)
        assertNull(provider.writeNotificationResponse(first).payload)
        assertEquals(204, provider.writeNotificationResponse(retry).status)
        assertNull(provider.writeNotificationResponse(retry).payload)
    }

    @Test
    fun notificationAuthorizationHonorsSelectionScopeFallbackAndBatchIsolation() {
        val candidates = listOf(CredentialAuthorization("a", "config-a"), CredentialAuthorization("b", "config-b"))
        fun response(claims: JsonObject, selected: List<String>?, scopes: Set<String>, single: Boolean = false): NotificationResponseResult {
            val authorized = (if (single) candidates.take(1) else candidates).authorizedByVerifiedToken(claims, selected, scopes)
                .map { it.credentialIdentifier }.toSet()
            return provider.createNotificationResponse(request, records, authorized)
        }
        val noDetails = JsonObject(emptyMap())
        assertIs<NotificationResponseResult.Success>(response(noDetails, null, emptySet(), single = true))
        assertIs<NotificationResponseResult.Failure>(response(noDetails, listOf("a"), emptySet()))
        assertIs<NotificationResponseResult.Success>(response(noDetails, listOf("a"), setOf("config-a")))
        assertIs<NotificationResponseResult.Failure>(response(noDetails, listOf("b"), setOf("config-a")))
        assertIs<NotificationResponseResult.Failure>(response(noDetails, null, setOf("config-b")))
        val grant = Json.parseToJsonElement("""{"authorization_details":[{"type":"openid_credential","credential_configuration_id":"config-a","credential_identifiers":["a"]}]}""").jsonObject
        assertIs<NotificationResponseResult.Success>(response(grant, listOf("a", "b"), emptySet()))
        assertIs<NotificationResponseResult.Failure>(response(grant, listOf("b"), setOf("config-a")))
        assertIs<NotificationResponseResult.Failure>(response(Json.parseToJsonElement("""{"authorization_details":[]}""").jsonObject, null, setOf("config-a")))
    }

    @Test
    fun storedModelRoundTripsWithProtocolEventNames() {
        val saved = records.first().copy(event = request.event, eventDescription = request.eventDescription)
        val json = Json.encodeToString(saved)
        assertEquals(saved, Json.decodeFromString<IssuedCredentialNotification>(json))
        assertEquals("credential_accepted", Json.parseToJsonElement(json).jsonObject["event"]?.jsonPrimitive?.content)
        assertEquals(records.last(), Json.decodeFromString<IssuedCredentialNotification>(Json.encodeToString(records.last())))
    }

    @Test
    fun tokenVerificationPrecedesInvalidParametersAndReturnsVerifiedClaims() = runTest {
        val claims = buildJsonObject { put("sub", "session"); put("scope", "credential") }
        val verifier = object : AccessTokenVerifier {
            override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?): JsonObject {
                require(token == "valid")
                assertEquals("https://issuer.example", expectedIssuer)
                return claims
            }
        }
        val provider = buildOAuth2Provider(createTestConfig(accessTokenVerifier = verifier))
        fun context(token: String) = ProtectedResourceAccessTokenContext(
            authorization = AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, token),
            expectedIssuer = "https://issuer.example",
        )
        val invalid = notificationRequestParameters("{")
        val tokenFailure = assertIs<NotificationRequestResult.OAuthFailure>(provider.createNotificationRequest(invalid, context("invalid")))
        assertEquals(OAuthErrorCodes.INVALID_TOKEN, tokenFailure.error.error)
        val parameterFailure = assertIs<NotificationRequestResult.Failure>(provider.createNotificationRequest(invalid, context("valid")))
        assertEquals(NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST, parameterFailure.error.error)
        val success = assertIs<NotificationRequestResult.Success>(provider.createNotificationRequest(
            mapOf("notification_id" to listOf("notification-a"), "event" to listOf("credential_accepted")), context("valid"),
        ))
        assertEquals(claims, success.tokenClaims)
        assertEquals(request.copy(eventDescription = null), success.request)
        assertIs<NotificationRequestResult.OAuthFailure>(this@NotificationProviderTest.provider.createNotificationRequest(invalid, context("valid")))
    }
}
