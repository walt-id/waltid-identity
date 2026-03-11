package id.walt.openid4vci.metadata.oauth

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationServerMetadataTest {

    @Test
    fun `issuer must not include query or fragment`() {
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

    // jwks_uri can be any scheme; validation only ensures host and no fragment
    @Test
    fun `fromBaseUrl populates required defaults`() {
        val metadata = AuthorizationServerMetadata.fromBaseUrl("https://issuer.example")
        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", metadata.jwksUri)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value), metadata.responseModesSupported)
        assertEquals(setOf("attest_jwt_client_auth"), metadata.tokenEndpointAuthMethodsSupported)
        assertEquals(setOf("ES256"), metadata.clientAttestationSigningAlgValuesSupported)
        assertEquals(setOf("ES256"), metadata.clientAttestationPopSigningAlgValuesSupported)
        assertEquals(setOf("ES256"), metadata.dpopSigningAlgValuesSupported)
        assertEquals(null, metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(
            setOf(GrantType.AuthorizationCode.value, GrantType.PreAuthorizedCode.value),
            metadata.grantTypesSupported,
        )
    }

    @Test
    fun `fromBaseUrl applies provided values correctly`() {
        val metadata = AuthorizationServerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example/",
            authorizationEndpointPath = "/oauth2/authorize",
            tokenEndpointPath = "/oauth2/token",
            jwksUriPath = "/oauth2/jwks",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            responseModesSupported = setOf(ResponseMode.QUERY.value),
            grantTypesSupported = setOf(GrantType.AuthorizationCode.value),
            tokenEndpointAuthMethodsSupported = setOf("private_key_jwt"),
            tokenEndpointAuthSigningAlgValuesSupported = setOf("RS256"),
            clientAttestationSigningAlgValuesSupported = setOf("ES384"),
            clientAttestationPopSigningAlgValuesSupported = setOf("ES512"),
            dpopSigningAlgValuesSupported = setOf("ES384"),
            pushedAuthorizationRequestEndpointPath = "/oauth2/par",
            requirePushedAuthorizationRequests = true,
        )

        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/oauth2/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/oauth2/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/oauth2/jwks", metadata.jwksUri)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value), metadata.responseModesSupported)
        assertEquals(setOf(GrantType.AuthorizationCode.value), metadata.grantTypesSupported)
        assertEquals(setOf("private_key_jwt"), metadata.tokenEndpointAuthMethodsSupported)
        assertEquals(setOf("RS256"), metadata.tokenEndpointAuthSigningAlgValuesSupported)
        assertEquals(setOf("ES384"), metadata.clientAttestationSigningAlgValuesSupported)
        assertEquals(setOf("ES512"), metadata.clientAttestationPopSigningAlgValuesSupported)
        assertEquals(setOf("ES384"), metadata.dpopSigningAlgValuesSupported)
        assertEquals("https://issuer.example/oauth2/par", metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(true, metadata.requirePushedAuthorizationRequests)
    }

    @Test
    fun `token endpoint JWT auth methods require signing algs`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                tokenEndpointAuthMethodsSupported = setOf("private_key_jwt"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                tokenEndpointAuthMethodsSupported = setOf("client_secret_jwt"),
            )
        }
    }

    @Test
    fun `token endpoint signing algs must not include none`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                tokenEndpointAuthMethodsSupported = setOf("private_key_jwt"),
                tokenEndpointAuthSigningAlgValuesSupported = setOf("none"),
            )
        }
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
            clientAttestationSigningAlgValuesSupported = setOf("ES256"),
            clientAttestationPopSigningAlgValuesSupported = setOf("ES256"),
            dpopSigningAlgValuesSupported = setOf("ES256"),
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
            authorizationDetailsTypesSupported = setOf("openid_credential"),
            preAuthorizedGrantAnonymousAccessSupported = true,
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
        assertEquals(setOf("ES256"), metadata.clientAttestationSigningAlgValuesSupported)
        assertEquals(setOf("ES256"), metadata.clientAttestationPopSigningAlgValuesSupported)
        assertEquals(setOf("ES256"), metadata.dpopSigningAlgValuesSupported)
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
        assertEquals(setOf("openid_credential"), metadata.authorizationDetailsTypesSupported)
        assertEquals(true, metadata.preAuthorizedGrantAnonymousAccessSupported)
    }
}
