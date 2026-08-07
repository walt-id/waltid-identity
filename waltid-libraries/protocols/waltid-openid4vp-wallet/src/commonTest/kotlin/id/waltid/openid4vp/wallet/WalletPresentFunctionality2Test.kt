package id.waltid.openid4vp.wallet

import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.meta.NoMeta
import id.walt.did.dids.DidService
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.*

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
                    clientId = "redirect_uri:https://wallet.example/callback",
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

        assertEquals("https://wallet.example/callback#error=invalid_request", result.getUrl)
    }

    @Test
    fun postSelectionRejectionRequiresBoundPlainResponseDestination() = runTest {
        var credentialsSelected = false
        val failure = assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.walletPresentHandling(
                holderKey = JWKKey.generate(KeyType.Ed25519),
                holderDid = "did:example:holder",
                presentationRequestUrl = Url("openid4vp://authorize"),
                resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(
                    AuthorizationRequest(
                        clientId = "unbound-client",
                        responseMode = OpenID4VPResponseMode.FRAGMENT,
                        redirectUri = "https://attacker.example/collect",
                        nonce = "nonce-from-preview",
                        dcqlQuery = DcqlQuery(
                            credentials = listOf(
                                CredentialQuery(
                                    id = "pid",
                                    format = CredentialFormat.DC_SD_JWT,
                                    meta = NoMeta,
                                )
                            )
                        ),
                    )
                ),
                selectCredentialsForQuery = {
                    credentialsSelected = true
                    emptyMap()
                },
                holderPoliciesToRun = null,
                runPolicies = null,
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            ).getOrThrow()
        }

        assertTrue(credentialsSelected)
        assertTrue(failure.message.orEmpty().contains("must bind client_id"))
    }

    @Test
    fun vpOnlyRequestWithCrypto2OnlyKeyNeverRequiresALegacyKey() = runTest {
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
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
                    clientId = "redirect_uri:https://wallet.example/callback",
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

        // An empty DCQL query cannot be fulfilled, so the wallet answers with a protocol error
        // instead of a token. Reaching this point already proves that a crypto2-only holder key
        // never falls back to a legacy key, and that no id_token is produced for a vp_token request.
        assertEquals("https://wallet.example/callback#error=invalid_request", result.getUrl)
        assertFalse(result.getUrl.orEmpty().contains("id_token"))
    }

    @Test
    fun crypto2OnlyKeyCompletesVpPresentationWithoutLegacyKeyMaterial() = runTest {
        DidService.minimalInit()
        val did = DidService.registerByKey("key", JWKKey.generate(KeyType.Ed25519)).did
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("crypto2-only-full-presentation"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val credential = W3C11(
            credentialData = buildJsonObject {
                put("credentialSubject", buildJsonObject { put("given_name", "Ada") })
            },
            issuer = "https://issuer.example",
            subject = did,
            signature = JwtCredentialSignature("signature", buildJsonObject {}),
            signed = "issuer.jwt.signature",
        )
        val query = CredentialQuery(
            id = "pid",
            format = CredentialFormat.JWT_VC_JSON,
            meta = NoMeta,
        )
        val request = AuthorizationRequest(
            clientId = "redirect_uri:https://wallet.example/callback",
            responseType = OpenID4VPResponseType.VP_TOKEN,
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            redirectUri = "https://wallet.example/callback",
            nonce = "nonce",
            dcqlQuery = DcqlQuery(credentials = listOf(query)),
        )
        val raw = RawDcqlCredential(
            id = "credential",
            format = credential.format,
            data = credential.credentialData,
            originalCredential = credential,
        )
        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = holderKey,
            holderDid = did,
            presentationRequestUrl = Url("openid4vp://authorize"),
            resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(request),
            selectCredentialsForQuery = {
                mapOf(
                    "pid" to listOf(
                        DcqlMatcher.DcqlMatchResult(raw, selectedDisclosures = null, originalQuery = query)
                    )
                )
            },
            holderPoliciesToRun = null,
            runPolicies = null,
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        ).getOrThrow()

        val vpToken = parseQueryString(requireNotNull(result.getUrl).substringAfter('#'))
            .get("vp_token")
            ?: error("presentation response did not contain vp_token")
        val vpJwt = Json.parseToJsonElement(vpToken).jsonObject.getValue("pid").jsonArray.single().jsonPrimitive.content
        val verified = CompactJws.verify(vpJwt, holderKey, JwsAlgorithm.ED25519)
        assertEquals(did, Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject["iss"]?.jsonPrimitive?.content)
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
                put("aud", "https://self-issued.me/v2")
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
    fun dcApiHolderBindingAudienceIsEncodedInSdJwtKeyBindingJwt() = runTest {
        val issuerJwt = JWKKey.generate(KeyType.Ed25519).signJws(
            buildJsonObject { put("_sd_alg", "sha-256") }.toString().encodeToByteArray()
        )
        val keyBindingJwt = WalletPresentFunctionality2.createKeyBindingJwt(
            disclosed = "$issuerJwt~",
            nonce = "nonce-123",
            audience = "origin:https://verifier.example",
            selectedDisclosures = emptyList(),
            holderKey = JWKKey.generate(KeyType.Ed25519),
        )
        val payload = Json.parseToJsonElement(
            keyBindingJwt.split('.')[1].decodeFromBase64Url().decodeToString()
        ).jsonObject

        assertEquals("origin:https://verifier.example", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("nonce-123", payload["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun signedRequestCarryingLegacyParameterIsNeverDivertedToFallback() = runTest {
        // The strict resolver must run first: appending a legacy parameter to a signed request must
        // not let an attacker downgrade it to the legacy Presentation Exchange path.
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val requestObject = attackerKey.signJws(
            buildJsonObject {
                put("client_id", "verifier")
                put("nonce", "nonce")
                put("aud", "https://self-issued.me/v2")
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        var fallbackInvocations = 0
        val downgradeUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier")
            parameters.append("request", requestObject)
            parameters.append("presentation_definition", "{}")
        }.build()

        assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            WalletPresentFunctionality2.resolveAuthorizationRequest(
                presentationRequestUrl = downgradeUrl,
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
        val payload = """{"client_id":"$clientId","nonce":"nonce","aud":"https://self-issued.me/v2"}"""
        return listOf(header, payload).joinToString(".") {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).encode(it.encodeToByteArray())
        } + "."
    }
}
