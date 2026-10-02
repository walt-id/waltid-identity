package id.walt.wallet2.handlers

import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleasedTokenResultTest {
    @Test
    fun bothGrantsKeepReleasedResponsesFixedAndExposeGrantsOnlyThroughDetailedApis() = runTest {
        val wallet = batchTestFixture(true).wallet
        val json = Json { encodeDefaults = true }
        var tokenCalls = 0
        val http = batchTestClient(token = {
            tokenCalls++
            """{"access_token":"access","expires_in":60,"token_type":"Bearer","scope":"identity",
              "authorization_details":[{"type":"openid_credential","credential_configuration_id":"identity",
              "credential_identifiers":["dataset-a","dataset-b"]}]}"""
        }, credential = { error("Token exchange must not request a credential") })
        try {
            val preAuthorized = RequestTokenRequest(Url("$BATCH_TEST_ISSUER/token"), "pre-code",
                credentialIssuer = BATCH_TEST_ISSUER, credentialConfigurationIds = listOf("identity"))
            val authorized = ExchangeCodeRequest("code", BATCH_TEST_ISSUER)
            for (authCode in listOf(false, true)) {
                val released = if (authCode) WalletIssuanceHandler.exchangeCode(wallet, authorized, httpClient = http)
                    else WalletIssuanceHandler.requestToken(wallet, preAuthorized, httpClient = http)
                assertEquals(RequestTokenResult("access", 60, "Bearer"), released)
                assertEquals(setOf("accessToken", "expiresIn", "tokenType"),
                    json.parseToJsonElement(json.encodeToString(released)).jsonObject.keys)
                val detailed = if (authCode) WalletIssuanceHandler.exchangeCodeDetailed(wallet, authorized, httpClient = http)
                    else WalletIssuanceHandler.requestTokenDetailed(wallet, preAuthorized, httpClient = http)
                assertEquals(released, detailed.toReleasedResult())
                assertEquals("identity", detailed.scope)
                assertEquals(listOf("dataset-a", "dataset-b"), detailed.authorizationDetails?.single()?.credentialIdentifiers)
            }
            assertEquals(4, tokenCalls)
        } finally {
            http.close()
        }
    }
}
