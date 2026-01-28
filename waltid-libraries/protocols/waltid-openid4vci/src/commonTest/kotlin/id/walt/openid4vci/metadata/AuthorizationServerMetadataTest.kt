package id.walt.openid4vci.metadata

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationServerMetadataTest {

    @Test
    fun `issuer must be https without query or fragment`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "http://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example?x=1",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example#frag",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
            )
        }
    }

    @Test
    fun `response types supported is required`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(""),
            )
        }
    }

    @Test
    fun `authorization endpoint required when authorization code is supported`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                grantTypesSupported = setOf(GrantType.AuthorizationCode.value),
            )
        }
    }

    @Test
    fun `token endpoint required when supported grant types use it`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                responseTypesSupported = setOf("code"),
                grantTypesSupported = setOf(GrantType.PreAuthorizedCode.value),
            )
        }
    }

    @Test
    fun `jwks uri must be https if present`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf("code"),
                jwksUri = "http://issuer.example/jwks",
            )
        }
    }

    @Test
    fun `fromBaseUrl populates required defaults`() {
        val metadata = AuthorizationServerMetadata.fromBaseUrl("https://issuer.example")
        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", metadata.jwksUri)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value), metadata.responseModesSupported)
        assertEquals(null, metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(
            setOf(GrantType.AuthorizationCode.value, GrantType.PreAuthorizedCode.value),
            metadata.grantTypesSupported,
        )
    }

    @Test
    fun `supports all registered metadata fields`() {
        val metadata = AuthorizationServerMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            jwksUri = "https://issuer.example/jwks",
            registrationEndpoint = "https://issuer.example/register",
            scopesSupported = setOf("openid", "email"),
            responseTypesSupported = setOf(ResponseType.CODE.value),
            responseModesSupported = setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value),
            grantTypesSupported = setOf(GrantType.AuthorizationCode.value),
            tokenEndpointAuthMethodsSupported = setOf("client_secret_basic"),
            tokenEndpointAuthSigningAlgValuesSupported = setOf("RS256"),
            pushedAuthorizationRequestEndpoint = "https://issuer.example/par",
            requirePushedAuthorizationRequests = true,
            serviceDocumentation = "https://issuer.example/docs",
            uiLocalesSupported = setOf("en", "de"),
            opPolicyUri = "https://issuer.example/policy",
            opTosUri = "https://issuer.example/tos",
            revocationEndpoint = "https://issuer.example/revoke",
            revocationEndpointAuthMethodsSupported = setOf("client_secret_basic"),
            revocationEndpointAuthSigningAlgValuesSupported = setOf("RS256"),
            introspectionEndpoint = "https://issuer.example/introspect",
            introspectionEndpointAuthMethodsSupported = setOf("client_secret_basic"),
            introspectionEndpointAuthSigningAlgValuesSupported = setOf("RS256"),
            codeChallengeMethodsSupported = listOf("S256", "plain"),
        )

        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", metadata.jwksUri)
        assertEquals("https://issuer.example/register", metadata.registrationEndpoint)
        assertEquals(setOf("openid", "email"), metadata.scopesSupported)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value), metadata.responseModesSupported)
        assertEquals(setOf(GrantType.AuthorizationCode.value), metadata.grantTypesSupported)
        assertEquals(setOf("client_secret_basic"), metadata.tokenEndpointAuthMethodsSupported)
        assertEquals(setOf("RS256"), metadata.tokenEndpointAuthSigningAlgValuesSupported)
        assertEquals("https://issuer.example/par", metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(true, metadata.requirePushedAuthorizationRequests)
        assertEquals("https://issuer.example/docs", metadata.serviceDocumentation)
        assertEquals(setOf("en", "de"), metadata.uiLocalesSupported)
        assertEquals("https://issuer.example/policy", metadata.opPolicyUri)
        assertEquals("https://issuer.example/tos", metadata.opTosUri)
        assertEquals("https://issuer.example/revoke", metadata.revocationEndpoint)
        assertEquals(setOf("client_secret_basic"), metadata.revocationEndpointAuthMethodsSupported)
        assertEquals(setOf("RS256"), metadata.revocationEndpointAuthSigningAlgValuesSupported)
        assertEquals("https://issuer.example/introspect", metadata.introspectionEndpoint)
        assertEquals(setOf("client_secret_basic"), metadata.introspectionEndpointAuthMethodsSupported)
        assertEquals(setOf("RS256"), metadata.introspectionEndpointAuthSigningAlgValuesSupported)
        assertEquals(listOf("S256", "plain"), metadata.codeChallengeMethodsSupported)
    }
}
