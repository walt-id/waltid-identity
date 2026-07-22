package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.dcql.models.DcqlQuery
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSerializationApi::class)
class WalletPresentFunctionality2Test {

    @Test
    fun resolvedAuthorizationRequestBypassesRequestUriResolution() = runTest {
        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = JWKKey.generate(KeyType.Ed25519),
            holderDid = "did:example:holder",
            presentationRequestUrl = Url(
                "openid4vp://authorize?request_uri=https%3A%2F%2Fverifier.invalid%2Frequest.jwt&request_uri_method=post",
            ),
            resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(
                AuthorizationRequest(
                    clientId = "verifier",
                    responseMode = OpenID4VPResponseMode.FRAGMENT,
                    redirectUri = "https://wallet.example/callback",
                    nonce = "nonce-from-preview",
                    dcqlQuery = DcqlQuery(credentials = emptyList()),
                )
            ),
            selectCredentialsForQuery = { emptyMap() },
            holderPoliciesToRun = null,
            runPolicies = null,
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        ).getOrThrow()

        assertEquals("https://wallet.example/callback#vp_token=%7B%7D", result.getUrl)
    }

    @Test
    fun vpOnlyRequestWithCrypto2OnlyKeyDoesNotBuildIdTokenOrRequireLegacyKey() = runTest {
        val holderKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("crypto2-only-holder"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = holderKey,
            holderDid = null,
            presentationRequestUrl = Url("openid4vp://authorize"),
            resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(
                AuthorizationRequest(
                    clientId = "verifier",
                    responseType = OpenID4VPResponseType.VP_TOKEN,
                    responseMode = OpenID4VPResponseMode.FRAGMENT,
                    redirectUri = "https://wallet.example/callback",
                    nonce = "nonce",
                    dcqlQuery = DcqlQuery(credentials = emptyList()),
                )
            ),
            selectCredentialsForQuery = { emptyMap() },
            holderPoliciesToRun = null,
            runPolicies = null,
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        ).getOrThrow()

        assertEquals("https://wallet.example/callback#vp_token=%7B%7D", result.getUrl)
    }

    @Test
    fun securityValidationErrorsNeverInvokeLegacyFallback() = runTest {
        var fallbackInvocations = 0
        val fallback: suspend (Url) -> Result<JsonElement> = {
            fallbackInvocations++
            Result.success(JsonPrimitive("legacy"))
        }
        val algNone = requestUrl(
            outerClientId = "verifier",
            requestObject = unsignedRequestObject("verifier", "oauth-authz-req+jwt"),
        )
        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                algNone,
                AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                fallback,
            )
        }
        val clientMismatch = requestUrl("outer", unsignedRequestObject("inner", "oauth-authz-req+jwt"))
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                clientMismatch,
                AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                fallback,
            )
        }
        val wrongType = requestUrl("verifier", unsignedRequestObject("verifier", "JWT"))
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                wrongType,
                AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                fallback,
            )
        }

        assertEquals(0, fallbackInvocations)
    }

    @Test
    fun invalidPreRegisteredSignatureNeverInvokesLegacyFallback() = runTest {
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val requestObject = attackerKey.signJws(
            buildJsonObject {
                put("client_id", "verifier")
                put("nonce", "nonce")
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        var fallbackInvocations = 0

        assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                presentationRequestUrl = requestUrl("verifier", requestObject),
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                legacyFallbackCallback = {
                    fallbackInvocations++
                    Result.success(JsonPrimitive("legacy"))
                },
                clientIdTrustConfiguration = ClientIdTrustConfiguration(
                    preRegisteredClients = mapOf(
                        "verifier" to ClientMetadata(
                            jwks = ClientMetadata.Jwks(listOf(trustedKey.getPublicKey().exportJWKObject())),
                        )
                    ),
                ),
            )
        }

        assertEquals(0, fallbackInvocations)
    }

    @Test
    fun explicitPresentationDefinitionUsesLegacyFallback() = runTest {
        var fallbackInvocations = 0
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("presentation_definition", "{}")
        }.build()

        assertFailsWith<WalletPresentFunctionality2.LegacyFallbackException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                presentationRequestUrl = requestUrl,
                legacyFallbackCallback = {
                    fallbackInvocations++
                    Result.success(JsonPrimitive("legacy"))
                },
            )
        }
        assertEquals(1, fallbackInvocations)
    }

    private fun requestUrl(outerClientId: String, requestObject: String): Url =
        URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", outerClientId)
            parameters.append("request", requestObject)
        }.build()

    private fun unsignedRequestObject(clientId: String, type: String): String {
        val header = """{"alg":"none","typ":"$type"}"""
        val payload = """{"client_id":"$clientId","nonce":"nonce"}"""
        return listOf(header, payload).joinToString(".") {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).encode(it.encodeToByteArray())
        } + "."
    }
}
