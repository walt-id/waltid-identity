package id.walt.did.dids

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

class DidServiceTest {

    init {
        runBlocking {
            // TODO: What exactly is needed?
            // DidService.apply {
            //     registerResolver(LocalResolver())
            //     updateResolversForMethods()
            //     registerRegistrar(LocalRegistrar())
            //     updateRegistrarsForMethods()
            // }
            DidService.minimalInit()
        }
    }

    @Test
    @Ignore
    fun `should create a Secpk1 did-key`() {

        val keyContent = """{
            "kty": "EC",
            "d": "NO6W0utz_C7HFhaGqpHR-HJMV2JTqZTWK8UodYO-CoI",
            "crv": "P-256",
            "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
            "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
        }""".trimIndent()


        runBlocking {
            val key = JWKKey.importJWK(keyContent).getOrThrow()
            val did = DidService.registerByKey("key", key).did

            assertContains(did, "did:key:z[a-km-zA-HJ-NP-Z1-9]+".toRegex())

            assertDoesNotThrow {
                runBlocking { DidService.resolve(did) }
            }
        }
    }

    @ParameterizedTest(name = "DidService.resolve() should resolve {0}")
    // @ValueSource(strings = [uniSampleDidP521])
    @MethodSource("didsFactory")
    @Ignore
    fun `should resolve {0}`(did: String) {
        assertDoesNotThrow {
            runBlocking { DidService.resolve(did) }
        }
    }

    companion object {

        // Examples from https://dev.uniresolver.io
        val uniSampleDidP521 =
            "did:key:z2J9gcGbsEDUmANXS8iJTVefK5t4eCx9x5k8jr8EyXWekTiEet6Jt6gwup2aWawzhHyMadvVMFcQ3ruwqg1Y8rYzjto1ccQu"
        val uniSampleDidBls12381G1 = "did:key:z3tEFS9q2WkwvvVvr1BrYwNreqcudmcCQGGRSQ8r73recEqAUHGeLPWzwK6toBdKJgX3Fs"
        val uniSampleDidRSA =
            "did:key:z4MXj1wBzi9jUstyPMS4jQqB6KdJaiatPkAtVtGc6bQEQEEsKTic4G7Rou3iBf9vPmT5dbkm9qsZsuVNjq8HCuW1w24nhBFGkRE4cd2Uf2tfrB3N7h4mnyPp1BF3ZttHTYv3DLUPi1zMdkULiow3M1GfXkoC6DoxDUm1jmN6GBj22SjVsr6dxezRVQc7aj9TxE7JLbMH1wh5X3kA58H3DFW8rnYMakFGbca5CB2Jf6CnGQZmL7o5uJAdTwXfy2iiiyPxXEGerMhHwhjTA1mKYobyk2CpeEcmvynADfNZ5MBvcCS7m3XkFCMNUYBS9NQ3fze6vMSUPsNa6GVYmKx2x6JrdEjCk3qRMMmyjnjCMfR4pXbRMZa3i"
        val uniSampleDidBls12381G2 =
            "did:key:z5TcEoNqw2THWrFNZP62f2UmKMsuDnxmtYiNFHbVvqyPKUVyt7XfYmJ6HUsxmMYh2QWRctQ65HEw6BcPXxQevdAAWsd2aTNSjVUZ6VoyuPv8g8BySddJG9bDLGzey9EHSdYMcHYrYV8ycwKeNxcSrLqTCqxzDBHmyW6zEzDyYUoa8S8SAzAhVXF2uT19iyczDekWKZoPw"
        val uniSampleDidEd25519 = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH"
        val uniSampleDidP384 = "did:key:z82Lkytz3HqpWiBmt2853ZgNgNG8qVoUJnyoMvGw6ZEBktGcwUVdKpUNJHct1wvp9pXjr7Y"
        val uniSampleDidP256 = "did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169"
        val uniSampleDidSecp256k1 = "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"
        val did9 =
            "did:key:zUC7DWA2FazpvPXmiXeTWuLjdMGXXmmWXbwoKNo554L3E4PD5ZsoZPqzCvkFkkQGvWp6uLZ3PKQJMfXYzLGNoiMyqXYSQa19cvWTiH3QpzddfRVWW6FtFMWTcvUb7wg4o9khbDt"

        // WaltId Generated DIDs
        val waltidDidEd25519 = "did:key:z6Mkp47FTJHXLfHNwBpoYer88SPm3AbKqnW3825MM3tSYFiN"
        val waltidDidRSA =
            "did:key:z4MXj1wBzi9jUstyQbe4dJtZdrS5erChYU2cEdS6QN1BUFF5EQmbRt2fuCvjqywFc4VDS3dFJiBfetocrBYDUCVqRKpss68ZcXeG6kPBQ8aJzxGpYgd3ThL6CgsGJGpwyk3nBshzpU5DHPTB6iigzox6kB8Jo8Uv68t1cUgaMnXuMR7soN3CnVXo197zRbNZ5oynbAhpEjUpK8GAqw1z41SMb75htBKnpQfY8SnN1XUGTgveVKpJTRe524zFxhpHy4QmnMH8XJKZieyDB7CKjP88d5oW6B3TqDqcAY2RCe5tREYgKBGbJZcrhFpdZZkyQpJCPnTNzsvWMCmbcrkMnmbhsEWj3hVpWYXj5p8yaHZHZhNiwj1UL"
        val waltidDidSecp256k1 =
            "did:key:zdCru39GRVTj7Y6gKRbT9axbErpR9xAq9GmQoS7S97SiYS5bPwRQbaJNcMsH9MAGfg5qpmgCcXN7SzwGbe1w6ZxFvkr5uwSjAm1JFnC3jHyLQc4toMXvQno97EMq"
        val waltidDidSecp256r1 =
            "did:key:zWmu9RCrS3hBhGSyhKfNSywuTWFCMjMxJeKhMPt6m3jwpBpWY3VaLqLiNMiVM4FpUNsSm3psdggkevuYHK6Lq1pnoznV7N6Uo7RDLu34gyGxLRhkL16B5r2ZP2QcNDFu"

        // waltid did lib (from DidKeyResolverTest)
        val waltidDidLibEd25519 = "did:key:z6MkrP3u6dEz2gpWq89J7DMWtPCYW8dGrpPBzkfkDt9FXrQg"
        val waltidDidLibSecp256r1 = "did:key:zDnaeWUm6JXpipAtL1N1hQFA4BQUwGCdaAVbpEZjN3Pf9nMrb"
        val waltidDidLibSecp256k1 = "did:key:zQ3shTZmje6ppF4oboKTX85hUWGrR1nxrWnFA3tnJhsHEC8c4"


        @JvmStatic
        fun didsFactory(): Stream<String> {
            return Stream.of(
                uniSampleDidP521,
                uniSampleDidBls12381G1,
                uniSampleDidRSA,
                uniSampleDidBls12381G2,
                uniSampleDidEd25519,
                uniSampleDidP384,
                uniSampleDidP256,
                uniSampleDidSecp256k1,
                did9,
                waltidDidEd25519,
                waltidDidRSA,
                waltidDidSecp256k1,
                waltidDidSecp256r1,
                waltidDidLibEd25519,
                waltidDidLibSecp256r1,
                waltidDidLibSecp256k1
            )
        }
    }
}