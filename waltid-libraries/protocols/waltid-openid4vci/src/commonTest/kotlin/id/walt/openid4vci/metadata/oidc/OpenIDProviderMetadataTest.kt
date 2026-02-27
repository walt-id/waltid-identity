package id.walt.openid4vci.metadata.oidc

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenIDProviderMetadataTest {

    @Test
    fun `issuer must not include query or fragment`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example?x=1",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example#frag",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `response types supported is required`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(""),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `authorization endpoint is required`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `token endpoint is required`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `subject types must be public or pairwise`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("private"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `id token signing algs must include RS256`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("ES256"),
            )
        }
    }

    @Test
    fun `scopes supported must include openid when present`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                scopesSupported = setOf("email"),
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
            )
        }
    }

    @Test
    fun `token endpoint auth signing algs required for jwt auth methods`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
                tokenEndpointAuthMethodsSupported = setOf("private_key_jwt"),
            )
        }
    }

    @Test
    fun `fromBaseUrl populates required defaults`() {
        val metadata = OpenIDProviderMetadata.fromBaseUrl("https://issuer.example")
        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", metadata.jwksUri)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value), metadata.responseModesSupported)
        assertEquals(
            setOf(GrantType.AuthorizationCode.value, GrantType.PreAuthorizedCode.value),
            metadata.grantTypesSupported
        )
        assertEquals(setOf("public"), metadata.subjectTypesSupported)
        assertEquals(setOf("RS256"), metadata.idTokenSigningAlgValuesSupported)
    }

    @Test
    fun `supports all registered metadata fields`() {
        val metadata = OpenIDProviderMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            jwksUri = "https://issuer.example/jwks",
            userinfoEndpoint = "https://issuer.example/userinfo",
            registrationEndpoint = "https://issuer.example/register",
            scopesSupported = setOf("openid", "email"),
            responseTypesSupported = setOf(ResponseType.CODE.value),
            responseModesSupported = setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value),
            grantTypesSupported = setOf(GrantType.AuthorizationCode.value),
            acrValuesSupported = setOf("urn:mace:incommon:iap:silver"),
            subjectTypesSupported = setOf("public"),
            idTokenSigningAlgValuesSupported = setOf("RS256"),
            idTokenEncryptionAlgValuesSupported = setOf("RSA-OAEP-256"),
            idTokenEncryptionEncValuesSupported = setOf("A128GCM"),
            userinfoSigningAlgValuesSupported = setOf("RS256"),
            userinfoEncryptionAlgValuesSupported = setOf("RSA-OAEP-256"),
            userinfoEncryptionEncValuesSupported = setOf("A128GCM"),
            requestObjectSigningAlgValuesSupported = setOf("none", "RS256"),
            requestObjectEncryptionAlgValuesSupported = setOf("RSA-OAEP-256"),
            requestObjectEncryptionEncValuesSupported = setOf("A128GCM"),
            tokenEndpointAuthMethodsSupported = setOf("client_secret_basic"),
            tokenEndpointAuthSigningAlgValuesSupported = setOf("RS256"),
            displayValuesSupported = setOf("page", "popup"),
            claimTypesSupported = setOf("normal"),
            claimsSupported = setOf("sub", "email"),
            serviceDocumentation = "https://issuer.example/docs",
            claimsLocalesSupported = setOf("en"),
            uiLocalesSupported = setOf("en"),
            claimsParameterSupported = true,
            requestParameterSupported = true,
            requestUriParameterSupported = true,
            requireRequestUriRegistration = false,
            opPolicyUri = "https://issuer.example/policy",
            opTosUri = "https://issuer.example/tos",
        )

        assertEquals("https://issuer.example", metadata.issuer)
        assertEquals("https://issuer.example/authorize", metadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", metadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", metadata.jwksUri)
        assertEquals("https://issuer.example/userinfo", metadata.userinfoEndpoint)
        assertEquals("https://issuer.example/register", metadata.registrationEndpoint)
        assertEquals(setOf("openid", "email"), metadata.scopesSupported)
        assertEquals(setOf(ResponseType.CODE.value), metadata.responseTypesSupported)
        assertEquals(setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value), metadata.responseModesSupported)
        assertEquals(setOf(GrantType.AuthorizationCode.value), metadata.grantTypesSupported)
        assertEquals(setOf("urn:mace:incommon:iap:silver"), metadata.acrValuesSupported)
        assertEquals(setOf("public"), metadata.subjectTypesSupported)
        assertEquals(setOf("RS256"), metadata.idTokenSigningAlgValuesSupported)
        assertEquals(setOf("RSA-OAEP-256"), metadata.idTokenEncryptionAlgValuesSupported)
        assertEquals(setOf("A128GCM"), metadata.idTokenEncryptionEncValuesSupported)
        assertEquals(setOf("RS256"), metadata.userinfoSigningAlgValuesSupported)
        assertEquals(setOf("RSA-OAEP-256"), metadata.userinfoEncryptionAlgValuesSupported)
        assertEquals(setOf("A128GCM"), metadata.userinfoEncryptionEncValuesSupported)
        assertEquals(setOf("none", "RS256"), metadata.requestObjectSigningAlgValuesSupported)
        assertEquals(setOf("RSA-OAEP-256"), metadata.requestObjectEncryptionAlgValuesSupported)
        assertEquals(setOf("A128GCM"), metadata.requestObjectEncryptionEncValuesSupported)
        assertEquals(setOf("client_secret_basic"), metadata.tokenEndpointAuthMethodsSupported)
        assertEquals(setOf("RS256"), metadata.tokenEndpointAuthSigningAlgValuesSupported)
        assertEquals(setOf("page", "popup"), metadata.displayValuesSupported)
        assertEquals(setOf("normal"), metadata.claimTypesSupported)
        assertEquals(setOf("sub", "email"), metadata.claimsSupported)
        assertEquals("https://issuer.example/docs", metadata.serviceDocumentation)
        assertEquals(setOf("en"), metadata.claimsLocalesSupported)
        assertEquals(setOf("en"), metadata.uiLocalesSupported)
        assertEquals(true, metadata.claimsParameterSupported)
        assertEquals(true, metadata.requestParameterSupported)
        assertEquals(true, metadata.requestUriParameterSupported)
        assertEquals(false, metadata.requireRequestUriRegistration)
        assertEquals("https://issuer.example/policy", metadata.opPolicyUri)
        assertEquals("https://issuer.example/tos", metadata.opTosUri)
    }
}
