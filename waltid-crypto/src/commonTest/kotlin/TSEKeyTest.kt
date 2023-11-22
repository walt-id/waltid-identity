import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TSEKey
import id.walt.crypto.keys.TSEKeyMetadata
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.streams.asStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TSEKeyTest {
    private val payload = JsonObject(
        mapOf(
            "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
            "iss" to JsonPrimitive("http://localhost:3000"),
            "aud" to JsonPrimitive("TOKEN"),
        )
    )

    fun getPublicKeyRepresentation() = runTest {

    }

    @ParameterizedTest
    @MethodSource
    fun getPublicKey(key: TSEKey?) = runTest {
        assumeTrue(hostCondition())
        assumeTrue(key != null)
        val publicKey = key!!.getPublicKey()
        assertTrue(!publicKey.hasPrivateKey)
        //TODO: assert keyId, thumbprint, export
    }

    fun getKeyType() = runTest {

    }

    fun getHasPrivateKey() = runTest {

    }

    @ParameterizedTest
    @MethodSource
    fun signRaw(key: TSEKey?) = runTest {
        assumeTrue(hostCondition())
        assumeTrue(key != null)
        val signed = key!!.signRaw(payload.toString().encodeToByteArray()) as String
        val verificationResult = key.verifyRaw(signed.decodeBase64Bytes(), payload.toString().encodeToByteArray())
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload.toString(), String(verificationResult.getOrThrow()))
    }

    @ParameterizedTest
    @MethodSource
    fun signJws(key: TSEKey?) = runTest {
        assumeTrue(hostCondition())
        val signed = key!!.signJws(payload.toString().encodeToByteArray())
        val verificationResult = key.verifyJws(signed)
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }

    fun getKeyId() = runTest {

    }

    fun verifyJws() = runTest {

    }

    fun exportJWK() = runTest {

    }

    fun exportJWKObject() = runTest {

    }

    fun exportPEM() = runTest {

    }

    companion object {
        private var keys: List<TSEKey?> = listOf(null)//ugly way to have test parameterization and condition at once

        @JvmStatic
        @BeforeAll
        fun initKeys() = runTest {
            hostCondition().takeIf { it }?.let {
                val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")
                keys = enumValues<KeyType>().map { TSEKey.generate(KeyType.Ed25519, tseMetadata) }
            }
        }
        @JvmStatic
        @AfterAll
        fun cleanup() = runTest {
            keys.forEach { it?.delete() }
        }
        @JvmStatic
        fun getPublicKey(): Stream<Arguments> = keys.map { arguments(it) }.asSequence().asStream()
        @JvmStatic
        fun signRaw(): Stream<Arguments> = keys.map { arguments(it) }.asSequence().asStream()
        @JvmStatic
        fun signJws(): Stream<Arguments> = keys.map { arguments(it) }.asSequence().asStream()

        private fun hostCondition() = runCatching {
            runBlocking { HttpClient().get("http://127.0.0.1:8200") }.status == HttpStatusCode.OK
        }.fold(onSuccess = { it }, onFailure = { false })
    }
}