package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto.keys.Key
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

private class StaticAttestationProvider(private val jwt: String) : WalletAttestationProvider {
    var receivedPublicKey: EncodedKey.Jwk? = null
        private set

    override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String {
        receivedPublicKey = instancePublicKeyJwk
        return jwt
    }

    @Deprecated("Use the EncodedKey.Jwk overload")
    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String =
        getAttestationJwt(instanceKey.exportPublicCrypto2Jwk(), clientId)
}

@Suppress("DEPRECATION")
private class LegacyStaticAttestationProvider(private val jwt: String) : WalletAttestationProvider {
    var receivedKey: Key? = null
        private set

    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
        receivedKey = instanceKey
        return jwt
    }
}

class ClientAttestationAssemblerTest {
    @Test
    fun providerReceivesPublicKeyUsedByVerifiablePop() = runTest {
        val key = attestationTestKey("assembler-key")
        val provider = StaticAttestationProvider("eyJ.attestation.sig")
        val headers = ClientAttestationAssembler(provider).buildAttestationHeaders(
            key,
            "wallet-client",
            "https://issuer.example.com/token",
        )

        assertEquals("eyJ.attestation.sig", headers.attestationJwt)
        assertFalse("d" in Jwk.parse(assertNotNull(provider.receivedPublicKey)))
        CompactJws.verify(headers.popJwt, key, JwsAlgorithm.ES256)
    }

    @Test
    fun legacyProviderReceivesOnlyPublicMaterialFromCrypto2Caller() = runTest {
        val key = attestationTestKey("legacy-provider-key")
        val provider = LegacyStaticAttestationProvider("eyJ.attestation.sig")

        val headers = ClientAttestationAssembler(provider).buildAttestationHeaders(
            key,
            "wallet-client",
            "https://issuer.example.com/token",
        )

        assertEquals("eyJ.attestation.sig", headers.attestationJwt)
        assertFalse(requireNotNull(provider.receivedKey).hasPrivateKey)
        CompactJws.verify(headers.popJwt, key, JwsAlgorithm.ES256)
    }
}
