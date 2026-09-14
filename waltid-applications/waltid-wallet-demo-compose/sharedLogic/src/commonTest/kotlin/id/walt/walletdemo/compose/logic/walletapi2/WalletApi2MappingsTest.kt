package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDeepLinkScheme
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
            credentialConfigurationId = "UniversityDegree",
        )
        val decoded = walletApi2Json.decodeFromString<PersistedAuthorizationIssuance>(
            walletApi2Json.encodeToString(original),
        )
        assertEquals(original, decoded)
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

    @Test
    fun replaceWalletAfterSuccessfulDeleteDoesNotCreateWhenDeleteFails() = runTest {
        var created = false
        val result = runCatching {
            replaceWalletAfterSuccessfulDelete(
                deleteCurrent = { error("HTTP 500") },
                createReplacement = {
                    created = true
                    "wallet-2"
                },
            )
        }
        assertTrue(result.isFailure)
        assertFalse(created)
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
}
