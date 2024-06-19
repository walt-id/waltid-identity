//import kotlinx.coroutines.test.runTest
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.JsonPrimitive
//import kotlin.test.Test
//
//class OCIKeyTest {
//
//    private object Config {/*        const val BASE_SERVER = "http://127.0.0.1:8200"
//        const val BASE_URL = "$BASE_SERVER/v1/transit"
//        const val TOKEN = "dev-only-token"
//        val NAMESPACE = null
//
//        //val TESTABLE_KEY_TYPES = KeyType.entries.filterNot { it in listOf(KeyType.secp256k1) } // TODO get working: this line
//        val TESTABLE_KEY_TYPES = listOf(KeyType.Ed25519)*/
//
//        val payload = JsonObject(
//            mapOf(
//                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
//                "iss" to JsonPrimitive("http://localhost:3000"),
//                "aud" to JsonPrimitive("TOKEN"),
//            )
//        )
//
//    }
//
//    private suspend fun isVaultAvailable() = runCatching {
//        // http.get(Config.BASE_SERVER).status == HttpStatusCode.OK
//    }.fold(onSuccess = { true }, onFailure = { false })
//
//    @Test
//    fun testOci() = runTest {
//        isVaultAvailable().takeIf { it }?.let {
//            println("Testing key creation ...")
//            testKeyCreation()
//
//            println("Testing resolving to public key ...")
//            testPublicKeys()
//
//            println("Testing sign & verify raw (payload: ${Config.payload})...")
//            testSignRaw()
//
//            println("Testing sign & verify JWS (payload: ${Config.payload})...")
//            testSignJws()
//
//            println("Full flow ...")
//            exampleCompleteOciFlow()
//
//        }
//    }
//
//    private suspend fun testKeyCreation() {
////        val tseMetadata = TSEKeyMetadata(Config.BASE_URL, Config.TOKEN, Config.NAMESPACE)
////        keys = TESTABLE_KEY_TYPES.map { TSEKey.generate(it, tseMetadata) }
//    }
//
//    private suspend fun testPublicKeys() {
////        keys.forEach { key ->
////            val publicKey = key.getPublicKey()
////            assertTrue { !publicKey.hasPrivateKey }
////        }
////        TODO: assert keyId, thumbprint, export
//    }
//
//    private suspend fun testSignRaw() {
////        keys.forEach { key ->
////            val signed = key.signRaw(Config.payload.toString().encodeToByteArray()) as String
////            val verificationResult = key.verifyRaw(signed.decodeBase64Bytes(), Config.payload.toString().encodeToByteArray())
////            assertTrue(verificationResult.isSuccess)
////            assertEquals(Config.payload.toString(), verificationResult.getOrThrow().decodeToString())
////        }
//    }
//
//    private suspend fun testSignJws() {
////        keys.forEach { key ->
////            val signed = key.signJws(Config.payload.toString().encodeToByteArray())
////            val verificationResult = key.verifyJws(signed)
////            assertTrue(verificationResult.isSuccess)
////            assertEquals(Config.payload, verificationResult.getOrThrow())
////        }
//    }
//
//    private suspend fun exampleCompleteOciFlow() {
////        val plaintext = JsonObject(
////            mapOf("id" to JsonPrimitive("abc123-${keyType.name}-JVM"))
////        )
////        println("Plaintext: $plaintext")
////
////        println("Generating key: $keyType...")
////        val key = JWKKey.generate(keyType)
////
////        println("  Checking for private key...")
////        assertTrue { key.hasPrivateKey }
////
////        println("  Checking for private key on a public key...")
////        assertFalse { key.getPublicKey().hasPrivateKey }
////
////        println("  Key id: " + key.getKeyId())
////
////        println("  Exporting JWK...")
////        val exportedJwk = key.exportJWK()
////        println("  JWK: $exportedJwk")
////        assertTrue { exportedJwk.startsWith("{") }
////
////        println("  Signing...")
////        val signed = key.signJws(Json.encodeToString(plaintext).encodeToByteArray())
////        println("  Signed: $signed")
////
////        println("  Verifying...")
////        val check1 = key.verifyJws(signed)
////        assertTrue(check1.isSuccess)
////        assertEquals(plaintext, check1.getOrThrow())
////
////        assertEquals(plaintext, check1.getOrThrow())
////        println("  Private key: ${check1.getOrNull()}")
////
////        val check2 = key.getPublicKey().verifyJws(signed)
////        assertEquals(plaintext, check2.getOrThrow())
////        println("  Public key: ${check2.getOrNull()}")
//    }
//
//
//}