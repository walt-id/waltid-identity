package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletBridgeKeyAttestationProviderTest {
    @Test
    fun importsConfiguredVerificationKeyAndForwardsEachRequest() = runTest {
        val publicKey = newPublicKey()
        val requests = mutableListOf<WalletBridgeKeyAttestationRequest>()
        val provider = object : WalletBridgeKeyAttestationProvider {
            override val verificationPublicJwk = publicKey.data.toByteArray().decodeToString()
            override suspend fun attest(request: WalletBridgeKeyAttestationRequest): String {
                requests += request
                return "provider-response"
            }
        }.toKeyAttestationProvider()

        val imported = provider.verificationKey.capabilities.publicKeyExporter!!
            .exportPublicKey().toPublicJwk(provider.verificationKey.spec)
        assertEquals(Jwk.sha256Thumbprint(publicKey), Jwk.sha256Thumbprint(imported))
        for (nonce in listOf("first", "refreshed")) {
            assertEquals("provider-response", provider.attest(KeyAttestationRequest(
                "https://issuer.example", publicKey, nonce,
                KeyAttestationsRequired(keyStorage = setOf("storage"), userAuthentication = setOf("authentication")),
            )))
        }
        assertEquals(listOf("first", "refreshed"), requests.map { it.nonce })
        requests.forEach {
            assertEquals("https://issuer.example", it.credentialIssuer)
            assertEquals(publicKey.data.toByteArray().decodeToString(), it.proofKeyJwk)
            assertEquals(listOf("storage"), it.requiredKeyStorage)
            assertEquals(listOf("authentication"), it.requiredUserAuthentication)
        }
    }

    @Test
    fun rejectsPrivateMaterialInConfiguredVerificationKey() = runTest {
        val publicKey = newPublicKey()
        val publicJwk = Json.parseToJsonElement(publicKey.data.toByteArray().decodeToString()).jsonObject
        val provider = object : WalletBridgeKeyAttestationProvider {
            override val verificationPublicJwk = JsonObject(publicJwk + ("d" to JsonPrimitive("private"))).toString()
            override suspend fun attest(request: WalletBridgeKeyAttestationRequest): String = error("Must not call provider")
        }
        assertFailsWith<IllegalArgumentException> { provider.toKeyAttestationProvider() }
    }

    private suspend fun newPublicKey() = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId("test-attester"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
    ).let { key -> key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec) }
}
