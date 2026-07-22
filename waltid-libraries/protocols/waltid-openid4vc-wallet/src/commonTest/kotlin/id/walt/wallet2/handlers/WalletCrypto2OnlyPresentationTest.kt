@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.DidService
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletCrypto2OnlyPresentationTest {
    @Test
    fun `wallet builds presentation with crypto2-only key`() = runTest {
        DidService.minimalInit()
        val did = DidService.registerByKey("key", JWKKey.generate(KeyType.Ed25519)).did
        val key = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("crypto2-only-presentation"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val keyStore = InMemoryKeyStore().also { it.addCrypto2Key(key) }
        val credentialStore = InMemoryCredentialStore().also {
            it.addCredential(
                StoredCredential(
                    id = "credential",
                    credential = W3C11(
                        credentialData = buildJsonObject {
                            put("credentialSubject", buildJsonObject { put("given_name", "Ada") })
                        },
                        issuer = "https://issuer.example",
                        subject = did,
                        signature = JwtCredentialSignature("signature", buildJsonObject {}),
                        signed = "issuer.jwt.signature",
                    ),
                )
            )
        }
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "pid",
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = NoMeta,
                )
            )
        )
        val result = WalletPresentationHandler.buildVpToken(
            wallet = Wallet(
                id = "wallet",
                keyStores = listOf(keyStore),
                credentialStores = listOf(credentialStore),
            ),
            request = BuildVpTokenRequest(
                authorizationRequest = AuthorizationRequest(
                    clientId = "verifier",
                    nonce = "nonce",
                    responseType = OpenID4VPResponseType.VP_TOKEN,
                    dcqlQuery = query,
                ),
                selectedCredentialIds = mapOf("pid" to listOf("credential")),
                did = did,
            ),
        )
        val presentationJwt = Json.parseToJsonElement(result.vpToken).jsonObject
            .getValue("pid").jsonArray.single().jsonPrimitive.content

        val verified = CompactJws.verify(presentationJwt, key, JwsAlgorithm.ED25519)
        assertEquals("verifier", Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject["aud"]
            ?.jsonPrimitive?.content)
    }
}
