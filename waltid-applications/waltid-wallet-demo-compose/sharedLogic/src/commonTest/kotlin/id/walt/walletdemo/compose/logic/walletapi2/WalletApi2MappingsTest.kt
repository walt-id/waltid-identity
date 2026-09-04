package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDeepLinkScheme
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import kotlin.test.Test
import kotlin.test.assertEquals
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
