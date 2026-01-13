package id.walt.openid4vci.tokens

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenService
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import java.lang.ThreadLocal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asContextElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AccessResponseResult
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.createTestConfig

class TokenServiceTest {

    @Test
    fun `resolves key from resolver and signs token`() = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val service = JwtAccessTokenService ({ key })

        val token = service.createAccessToken(mapOf("sub" to "alice"))
        assertTrue(token.isNotBlank())

        val header = decodeHeader(token)
        assertEquals(key.keyType.jwsAlg, header["alg"])
        assertEquals(key.getKeyId(), header["kid"])
    }

    @Test
    fun `authorization code flow embeds granted scopes into JWT scope claim`(): Unit = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val tokenService = JwtAccessTokenService( { key })
        val provider = buildOAuth2Provider(createTestConfig(tokenService = tokenService))

        val issuerId = "issuer-scope"
        val scope = "openid email profile"
        val clientId = "client-scope"
        val redirectUri = "https://client.example/callback"

        val authorizeResult = provider.createAuthorizeRequest(
            mapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
            ),
        )
        require(authorizeResult is AuthorizeRequestResult.Success)
        val authorizeRequest = authorizeResult.request.also { it.setIssuerId(issuerId) }

        val authorizeResponse = provider.createAuthorizeResponse(
            authorizeRequest,
            DefaultSession(subject = "alice"),
        )
        require(authorizeResponse is AuthorizeResponseResult.Success)
        val code = authorizeResponse.response.parameters.getValue("code")

        val accessRequestResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GrantType.AuthorizationCode.value,
                "client_id" to clientId,
                "code" to code,
                "redirect_uri" to redirectUri,
            ),
        )
        require(accessRequestResult is AccessRequestResult.Success)
        val accessRequest = accessRequestResult.request.also { it.setIssuerId(issuerId) }

        val accessResponse = provider.createAccessResponse(accessRequest)
        require(accessResponse is AccessResponseResult.Success)
        val token = accessResponse.response.accessToken

        val payload = decodePayload(token)
        assertEquals("alice", payload["sub"])
        assertEquals(issuerId, payload["iss"])
        assertEquals(scope, payload["scope"])
        assertNotNull(payload["exp"])
        assertNotNull(payload["iat"])
    }

    @Test
    fun `single service signs with different keys per call`() = runBlocking {
        val keys = listOf(
            JWKKey.generate(KeyType.Ed25519),
            JWKKey.generate(KeyType.secp256r1),
            JWKKey.generate(KeyType.RSA),
        )

        val currentKey = ThreadLocal<Key?>()
        val service = JwtAccessTokenService ({ currentKey.get() ?: error("No key in context") })

        val tokens = keys.map { key ->
            async(currentKey.asContextElement(value = key)) {
                service.createAccessToken(mapOf("sub" to "alice"))
            }
        }.awaitAll()

        tokens.zip(keys).forEach { (token, key) ->
            val header = decodeHeader(token)
            assertEquals(key.keyType.jwsAlg, header["alg"])
            assertEquals(key.getKeyId(), header["kid"])
            assertTrue(token.isNotBlank())
        }
    }

    @Test
    fun `single provider can sign with different keys via request-scoped resolver`() = runBlocking {
        val issuerAKey = JWKKey.generate(KeyType.Ed25519)
        val issuerBKey = JWKKey.generate(KeyType.secp256r1)

        val currentKey = ThreadLocal<Key?>()
        val resolver = JwtSigningKeyResolver { resolveCurrentKey(currentKey) }
        val service = JwtAccessTokenService(resolver)

        suspend fun signFor(key: Key): String = withContext(currentKey.asContextElement(key)) {
            service.createAccessToken(mapOf("sub" to "demo"))
        }

        val tokenA = signFor(issuerAKey)
        val tokenB = signFor(issuerBKey)

        val headerA = decodeHeader(tokenA)
        assertEquals(issuerAKey.keyType.jwsAlg, headerA["alg"])
        assertEquals(issuerAKey.getKeyId(), headerA["kid"])

        val headerB = decodeHeader(tokenB)
        assertEquals(issuerBKey.keyType.jwsAlg, headerB["alg"])
        assertEquals(issuerBKey.getKeyId(), headerB["kid"])
    }

    @Test
    fun `single provider handles multiple issuers end-to-end with context-based key selection`() = runBlocking {
        val keysByIssuer = mapOf(
            "issuer-1" to JWKKey.generate(KeyType.Ed25519),
            "issuer-2" to JWKKey.generate(KeyType.secp256r1),
            "issuer-3" to JWKKey.generate(KeyType.RSA),
        )

        val currentKey = ThreadLocal<Key?>()
        val tokenService = JwtAccessTokenService( { resolveCurrentKey(currentKey) })
        val provider = buildOAuth2Provider(createTestConfig(tokenService = tokenService))

        suspend fun runFlow(issuerId: String): String = withContext(currentKey.asContextElement(keysByIssuer.getValue(issuerId))) {
            val authorizeRequestResult = provider.createAuthorizeRequest(
                mapOf(
                    "response_type" to "code",
                    "client_id" to "client-$issuerId",
                    "redirect_uri" to "https://client.example/callback",
                    "scope" to "openid",
                ),
            )
            require(authorizeRequestResult is AuthorizeRequestResult.Success)
            val authorizeRequest = authorizeRequestResult.request.also { it.setIssuerId(issuerId) }

            val authorizeResponse = provider.createAuthorizeResponse(
                authorizeRequest,
                DefaultSession(subject = "sub-$issuerId"),
            )
            require(authorizeResponse is AuthorizeResponseResult.Success)
            val code = authorizeResponse.response.parameters.getValue("code")

            val accessRequestResult = provider.createAccessRequest(
                mapOf(
                    "grant_type" to GrantType.AuthorizationCode.value,
                    "client_id" to "client-$issuerId",
                    "code" to code,
                    "redirect_uri" to "https://client.example/callback",
                ),
            )
            require(accessRequestResult is AccessRequestResult.Success)
            val accessRequest = accessRequestResult.request.also { it.setIssuerId(issuerId) }

            val accessResponse = provider.createAccessResponse(accessRequest)
            require(accessResponse is AccessResponseResult.Success)

            accessResponse.response.accessToken
        }

        val tokensByIssuer = keysByIssuer.keys.associateWith { runFlow(it) }
        tokensByIssuer.forEach { (issuerId, token) ->
            val header = decodeHeader(token)
            val key = keysByIssuer.getValue(issuerId)
            assertEquals(key.keyType.jwsAlg, header["alg"])
            assertEquals(key.getKeyId(), header["kid"])
        }
    }

    private fun resolveCurrentKey(currentKey: ThreadLocal<Key?>): Key =
        currentKey.get() ?: error("No key in request context")

    private fun decodeHeader(jwt: String): Map<String, String> {
        val headerSegment = jwt.substringBefore(".")
        val json = headerSegment.decodeFromBase64Url().decodeToString()
        return Json.parseToJsonElement(json).jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    }

    private fun decodePayload(jwt: String): Map<String, String> {
        val payloadSegment = jwt.substringAfter(".").substringBefore(".")
        val json = payloadSegment.decodeFromBase64Url().decodeToString()
        return Json.parseToJsonElement(json).jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    }
}
