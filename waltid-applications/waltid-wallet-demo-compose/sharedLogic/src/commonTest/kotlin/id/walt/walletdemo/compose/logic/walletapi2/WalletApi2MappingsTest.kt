package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDeepLinkScheme
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletApi2MappingsTest {
    @Test
    fun preAuthorizedGrantWhenCodePresent() {
        val response = ResolveOfferDetailedResponseDto(
            credentialIssuer = "https://issuer.example",
            grantType = "authorization_code",
            preAuthorizedCode = "abc",
            credentialEndpoint = "https://issuer.example/credential",
            issuer = OfferIssuerMetadataDto(credentialIssuer = "https://issuer.example"),
        )
        assertEquals(WalletDemoIssuanceGrant.PreAuthorizedCode, response.toDemoGrant())
    }

    @Test
    fun authorizationGrantFromGrantType() {
        val response = ResolveOfferDetailedResponseDto(
            credentialIssuer = "https://issuer.example",
            grantType = "authorization_code",
            credentialEndpoint = "https://issuer.example/credential",
            issuer = OfferIssuerMetadataDto(credentialIssuer = "https://issuer.example"),
        )
        assertEquals(WalletDemoIssuanceGrant.AuthorizationCode, response.toDemoGrant())
        assertTrue(response.toDemoPreview().requiresIssuerAuthentication)
    }

    @Test
    fun persistedAuthorizationIssuanceRoundTrip() {
        val original = PersistedAuthorizationIssuance(
            id = "session-1",
            offerUrl = "openid-credential-offer://issuer.example",
            redirectUri = "http://localhost:8080/",
            did = "did:jwk:test",
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            nonceEndpoint = "https://issuer.example/nonce",
            codeVerifier = "verifier",
            authorizationState = "state-1",
            walletId = "wallet-1",
            credentials = listOf(IssuanceCredentialSelectionDto("UniversityDegree", listOf(
                HolderBindingDto("holder-1", "did:key:one"), HolderBindingDto("holder-2", "did:key:two"),
            ))),
        )
        val decoded = walletApi2Json.decodeFromString<PersistedAuthorizationIssuance>(
            walletApi2Json.encodeToString(original),
        )
        assertEquals(original, decoded)
    }

    @Test
    fun authorizedRestRequestPreservesAllBindingsWithoutASingularConfigurationField() {
        val selections = listOf(IssuanceCredentialSelectionDto("pid", listOf(
            HolderBindingDto("key-1", "did:key:one"), HolderBindingDto("key-2", "did:key:two"),
        )))
        val request = ReceiveAuthorizedCredentialRequestDto(
            code = "code", credentialIssuer = "https://issuer.example", credentialEndpoint = "https://issuer.example/credential",
            redirectUri = "openid://", credentials = selections,
        )
        val json = walletApi2Json.parseToJsonElement(walletApi2Json.encodeToString(request)).jsonObject
        assertFalse("credentialConfigurationId" in json)
        assertEquals(WalletApi2DefaultClientId, json.getValue("clientId").jsonPrimitive.content)
        assertEquals(2, json.getValue("credentials").jsonArray.single().jsonObject.getValue("holderBindings").jsonArray.size)
        assertEquals(request, walletApi2Json.decodeFromString<ReceiveAuthorizedCredentialRequestDto>(json.toString()))
    }

    @Test
    fun authorizationUrlRequestAlwaysSendsTheSameClientId() {
        val request = GenerateAuthorizationUrlRequestDto(
            offerUrl = "openid-credential-offer://issuer.example/?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer",
            redirectUri = "http://localhost:8080/",
            credentialConfigurationIds = listOf("OpenBadgeCredential_jwt_vc_json"),
        )
        val json = walletApi2Json.parseToJsonElement(walletApi2Json.encodeToString(request)).jsonObject
        assertEquals(WalletApi2DefaultClientId, json.getValue("clientId").jsonPrimitive.content)
    }

    @Test
    fun repeatedDatasetResultsDoNotDependOnTheLegacyTransactionMap() {
        val result = walletApi2Json.decodeFromString<ReceiveCredentialResultDto>("""
            {"credentialIds":["saved"],"deferredTransactionIds":null,
             "deferredCredentials":[
               {"credentialConfigurationId":"pid","credentialIdentifier":"first","deferredCredentialId":"handle-1","transactionId":"tx-1","intervalSeconds":5},
               {"credentialConfigurationId":"pid","credentialIdentifier":"second","deferredCredentialId":"handle-2","transactionId":"tx-2","intervalSeconds":7}],
             "failure":{"target":{"credentialConfigurationId":"pid","credentialIdentifier":"third"},"stage":"REQUEST",
               "notAttempted":[{"credentialConfigurationId":"mdl"}]}}
        """.trimIndent())
        assertEquals(listOf("saved"), result.credentialIds)
        assertEquals(listOf("first", "second"), result.deferredCredentials.map { it.credentialIdentifier })
        assertEquals(listOf("tx-1", "tx-2"), result.deferredCredentials.map { it.transactionId })
        val outcome = assertIs<WalletDemoIssuanceOutcome.Failed>(result.toOutcome())
        assertEquals(listOf("saved"), outcome.storedCredentialIds)
        assertEquals(listOf("handle-1", "handle-2"), outcome.deferredCredentials.map { it.id })
        assertEquals(listOf("first", "second"), outcome.deferredCredentials.map { it.credentialIdentifier })
        assertTrue(outcome.offerConsumed)
        assertEquals(1, outcome.failedTargetCount)
        assertEquals(1, outcome.notAttemptedTargetCount)
        assertEquals("third", result.failure?.target?.credentialIdentifier)
        assertEquals(listOf("mdl"), result.failure?.notAttempted?.map { it.credentialConfigurationId })
    }

    @Test
    fun fullFlowStorageRecoveryIsMergedWithIssuerDeferredTargetsWithoutDoubleCountingIds() {
        val result = walletApi2Json.decodeFromString<ReceiveCredentialResultDto>("""
            {"credentialIds":["earlier","saved"],
             "deferredCredentials":[{"credentialConfigurationId":"pid","credentialIdentifier":"first",
               "deferredCredentialId":"issuer-handle","transactionId":"tx","intervalSeconds":5}],
             "failure":{"target":{"credentialConfigurationId":"pid","credentialIdentifier":"second"},"stage":"STORAGE"},
             "storageOutcome":{"sessionId":"local-handle","error":{"code":"STORAGE","message":"Could not save"},
               "storedCredentialIds":["saved"],"deferredCredentials":[
                 {"id":"local-handle","credentialConfigurationId":"pid","credentialIdentifier":"second"}]}}
        """)
        val outcome = assertIs<WalletDemoIssuanceOutcome.Failed>(result.toOutcome())
        assertEquals(listOf("earlier", "saved"), outcome.storedCredentialIds)
        assertEquals(listOf("issuer-handle", "local-handle"), outcome.deferredCredentials.map { it.id })
        assertEquals(listOf("first", "second"), outcome.deferredCredentials.map { it.credentialIdentifier })
        assertNull(outcome.deferredCredentials.last().intervalSeconds)
        assertTrue(outcome.offerConsumed)
    }

    @Test
    fun deferredResumeWireOutcomesPreservePendingAndCommittedProgress() {
        fun decode(json: String) = walletApi2Json.decodeFromString<DeferredIssuanceOutcomeDto>(json).toOutcome()
        val pending = """{"id":"handle","credentialConfigurationId":"pid","credentialIdentifier":"record","intervalSeconds":9}"""
        val deferred = assertIs<WalletDemoIssuanceOutcome.Deferred>(decode("""
            {"type":"deferred","sessionId":"session","storedCredentialIds":["saved"],"credentials":[$pending]}
        """))
        assertEquals(listOf("saved"), deferred.storedCredentialIds)
        assertEquals("handle", deferred.credentials.single().id)
        assertEquals("record", deferred.credentials.single().credentialIdentifier)
        assertEquals(9L, deferred.credentials.single().intervalSeconds)
        val failed = assertIs<WalletDemoIssuanceOutcome.Failed>(decode("""
            {"type":"failed","sessionId":"session","error":{"code":"STORAGE","message":"Could not save"},
             "storedCredentialIds":["saved"],"deferredCredentials":[$pending],
             "failure":{"target":{"credentialConfigurationId":"pid"},"stage":"STORAGE",
               "notAttempted":[{"credentialConfigurationId":"mdl"},{"credentialConfigurationId":"employee"}]}}
        """))
        assertEquals(deferred.storedCredentialIds, failed.storedCredentialIds)
        assertEquals(deferred.credentials, failed.deferredCredentials)
        assertEquals("Could not save", failed.message)
        assertEquals(1, failed.failedTargetCount)
        assertEquals(2, failed.notAttemptedTargetCount)
        assertEquals(listOf("saved", "new"), assertIs<WalletDemoIssuanceOutcome.Stored>(decode("""
            {"type":"stored","sessionId":"session","credentialIds":["saved","new"]}
        """)).credentialIds)
        assertIs<WalletDemoIssuanceOutcome.Cancelled>(decode("""{"type":"cancelled","sessionId":"session"}"""))
    }

    @Test
    fun localSaveHandleWithoutConfigurationKeepsTheIdentityUnknown() {
        val handle = walletApi2Json.decodeFromString<DeferredCredentialHandleDto>("""{"id":"local-save"}""")
        assertNull(handle.toDemoDeferred().credentialConfigurationId)
        assertNull(handle.toDemoDeferred().credentialIdentifier)
    }

    @Test
    fun emptyDisclosureSelectionStaysEmpty() {
        assertEquals(
            emptyList(),
            emptyList<WalletDemoPresentationDisclosureSelection>().toDisclosureSelectionDtos(),
        )
    }

    @Test
    fun disclosureSelectionPathsRoundTrip() {
        val selected = listOf(
            WalletDemoPresentationDisclosureSelection(
                queryId = "query-1",
                credentialId = "cred-1",
                path = "$.given_name",
            ),
        )
        assertEquals(
            listOf(
                DisclosureSelectionDto(
                    queryId = "query-1",
                    credentialId = "cred-1",
                    path = "$.given_name",
                ),
            ),
            selected.toDisclosureSelectionDtos(),
        )
    }

    @Test
    fun emptyDisclosureSelectionIsEncodedAsEmptyArray() {
        val encoded = walletApi2Json.encodeToString(
            BuildVpTokenRequestDto(
                requestUrl = "https://verifier.example/request",
                selectedDisclosureOptions = emptyList(),
            ),
        )
        assertTrue("\"selectedDisclosureOptions\":[]" in encoded.replace(" ", ""))
    }

    @Test
    fun omittedDisclosureSelectionIsNotEncoded() {
        val encoded = walletApi2Json.encodeToString(
            BuildVpTokenRequestDto(requestUrl = "https://verifier.example/request"),
        )
        assertFalse("selectedDisclosureOptions" in encoded)
    }

}

class WalletDeepLinkSchemeWebTest {
    @Test
    fun httpsAuthorizationCallbackWithCode() {
        assertEquals(
            WalletDeepLinkScheme.AuthorizationCallback,
            WalletDeepLinkScheme.parse("http://localhost:8080/?code=abc&state=1"),
        )
    }

    @Test
    fun httpWithoutCodeIsIgnored() {
        assertEquals(null, WalletDeepLinkScheme.parse("http://localhost:8080/"))
    }

    @Test
    fun httpsAuthorizationCallbackWithError() {
        assertEquals(
            WalletDeepLinkScheme.AuthorizationCallback,
            WalletDeepLinkScheme.parse("http://localhost:8080/?error=access_denied&state=1"),
        )
    }
}
