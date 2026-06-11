package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class AssemblerTestP256Key : Key() {
    override val keyType: KeyType = KeyType.secp256r1
    override val hasPrivateKey: Boolean = true
    override suspend fun getKeyId(): String = "mock-kid"
    override suspend fun getThumbprint(): String = "mock-thumbprint"
    override suspend fun exportJWK(): String = """{"kty":"EC","crv":"P-256","x":"x","y":"y"}"""
    override suspend fun exportJWKObject(): JsonObject = JsonObject(emptyMap())
    override suspend fun getPublicKey(): Key = this
    override suspend fun getMeta(): KeyMeta = throw NotImplementedError()
    override suspend fun deleteKey(): Boolean = true
    override suspend fun exportPEM(): String = ""
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = byteArrayOf()
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> = Result.success(byteArrayOf())
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = Result.success(JsonObject(emptyMap()))
    override suspend fun getPublicKeyRepresentation(): ByteArray = byteArrayOf()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val headerJson = JsonObject(headers).toString()
        val headerB64 = Base64.UrlSafe.encode(headerJson.encodeToByteArray()).trimEnd('=')
        val payloadB64 = Base64.UrlSafe.encode(plaintext).trimEnd('=')
        return "$headerB64.$payloadB64.mock-sig"
    }
}

private class StaticAttestationProvider(private val jwt: String) : WalletAttestationProvider {
    var callCount = 0
        private set

    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
        callCount++
        return jwt
    }
}

class ClientAttestationAssemblerTest {

    @Test
    fun testAttestationHeaders() = runTest {
        val attestationJwt = "eyJ.attestation.sig"
        val provider = StaticAttestationProvider(attestationJwt)
        val assembler = ClientAttestationAssembler(provider)
        val key = AssemblerTestP256Key()

        val headers = assembler.buildAttestationHeaders(key, "wallet-client", "https://issuer.example.com/token")

        assertEquals(attestationJwt, headers.attestationJwt)

        val popParts = headers.popJwt.split(".")
        assertEquals(3, popParts.size, "PoP should be a valid 3-part JWT")
    }

    @Test
    fun testProviderIsCalled() = runTest {
        val provider = StaticAttestationProvider("attestation.jwt.here")
        val assembler = ClientAttestationAssembler(provider)

        assembler.buildAttestationHeaders(AssemblerTestP256Key(), "client", "https://token.endpoint")
        assertEquals(1, provider.callCount)
    }
}
