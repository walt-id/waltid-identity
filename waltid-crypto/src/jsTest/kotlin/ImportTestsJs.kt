import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportTestsJs {

    private val privateKeyJsonString = """
            {
              "kty": "RSA",
              "kid": "288WlRQvku-zrHFmvcAW86jnTF3qsMoEUKEbteI2K4A",
              "n": "qV-fGqTo8r6L52sIJpt44bxLkODaF0_wvIL_eYYDL55H-Ap-b1q4pd4YyZb7pARor_1mob6sMRnnAr5htmO1XucmKBEiNY-12zza0q9smjLm3-eNqq-8PgsEqBz4lU1YIBeQzsCR0NTa3J3OHfr-bADVystQeonSPoRLqSoO78oAtonQWLX1MUfS9778-ECcxlM21-JaUjqMD0nQR6wl8L6oWGcR7PjcjPQAyuS_ASTy7MO0SqunpkGzj_H7uFbK9Np_dLIOr9ZqrkCSdioA_PgDyk36E8ayuMnN1HDy4ak_Q7yEX4R_C75T0JxuuYio06hugwyREgOQNID-DVUoLw",
              "e": "AQAB",
              "d": "hndR21ddUYqRi9JfkDcSSzSwUX8R5jwjBaaCqLoKQX3J6VR7eHBv889VooXplheh_UaSeorkLb9Atd7ruF-EmKmuk1S28gr79-hiWa3H7MvIm647vGz0Z9VbhxQpDm9vLVtILbyYh1DVyRzHjOm9n4UyNmQfqolMjzF81_p6DUfpkMcDBJSlsTmRKMWPG0u8mFm8aB9ZftbryPO36QOny7g4_M8SZG1yxGTbypjyDTP9WqMmHpaC-66gLszjyxwEbVh69m-HsDEs7Qg9oMVG2FiwtDSXIApwfLk2v2Yk4-TeAD49rZzw-QcJ0yqPeVdq8cgyFOhwX-cPtQIm8X7AgQ",
              "p": "0bNpJzzOOgzpqaWkb-5PuUgY9AedUsnze24AtukXaN9VY7e5BLYcbE11RGeyj8kkhpotvZQ6WrYEfvSkfxBvoVc1q86FXiqlpwmUL-_jO4BbgESOK9eaWP1iWmWNrZpqwdnIeF3VZHfCIoFxRV_Tb_Sp8UNSueFgCH6IVJlfwSE",
              "q": "zsTarRYo9lLE8XvzpGzpjtrOHsnLuk2n5GXP6M2X89BL8yc8_5Fp99m_Em9vGAOhZBK9ActZuZEGSVVhfV1ImGw17tLyQZSCAvSzQpZSYpT9EDeZgn_oSorfUgMKppm1X4rl5Yz7lMR1khljdKt_X6gFA6ADL2h_ARK1bBRjr08",
              "dp": "lOfqTmN-KXiL39xwdM7rq6zHk1lo3KXtEIOfXEMOTXjxQJrwdaj_a-Rg1g8wm6uAFVicDFeaTFmdvazothWsvwuXYAWJbMGp2YASyytz1wehcea8ceNqhbB_y6L7RQA2uKp2EQrIgcwMfcYe8d1G3eQFXP2qW7XvJHj9Q92ZQiE",
              "dq": "E7TDOpfQE5nT10f-8n7Gy6yi1GBbIEhiZewmIoPlpYEGnAfzUlAjj1GbWkBwkBNYgFcg2FjvFjZyKO8QOYh4cL5vbXGBUSq8MVfs9b2p4Gdervr9kGhsVR5jJkfP7gzcMlzkiDoliAopQmFVDzuBCjbTM4M-inglEo8b508SKRU",
              "qi": "aJsDBhxQFDbpQr20TjgxImwBslVP9xIauy3ncCmjHix6Fc1l51gL71V1OWGnXaStGfoWy0gKkUnJuU3_X_xA_QwzAXPJYa-juRlD8BxTf7rmR_HC-XiVdyNnkU3afHtK4nShS2EuN2EXOrYDrbQoA13_a6Itk_55vDpJ3jciwS8"
            }
        """.trimIndent()


    private val publicKeyJwkString = """
            {
              "kty": "RSA",
              "kid": "288WlRQvku-zrHFmvcAW86jnTF3qsMoEUKEbteI2K4A",
              "n": "qV-fGqTo8r6L52sIJpt44bxLkODaF0_wvIL_eYYDL55H-Ap-b1q4pd4YyZb7pARor_1mob6sMRnnAr5htmO1XucmKBEiNY-12zza0q9smjLm3-eNqq-8PgsEqBz4lU1YIBeQzsCR0NTa3J3OHfr-bADVystQeonSPoRLqSoO78oAtonQWLX1MUfS9778-ECcxlM21-JaUjqMD0nQR6wl8L6oWGcR7PjcjPQAyuS_ASTy7MO0SqunpkGzj_H7uFbK9Np_dLIOr9ZqrkCSdioA_PgDyk36E8ayuMnN1HDy4ak_Q7yEX4R_C75T0JxuuYio06hugwyREgOQNID-DVUoLw",
              "e": "AQAB"
            }
        """.trimIndent()

    private val publicKeyPem = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFlHHWfLk0gLBbsLTcuCrbCqoHqmM
        YJepMC+Q+Dd6RBmBiA41evUsNMwLeN+PNFqib+xwi9JkJ8qhZkq8Y/IzGg==
        -----END PUBLIC KEY-----
    """.trimIndent()

    private val privateKeyPem = """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgiyvo0X+VQ0yIrOaN
        nlrnUclopnvuuMfoc8HHly3505OhRANCAAQWUcdZ8uTSAsFuwtNy4KtsKqgeqYxg
        l6kwL5D4N3pEGYGIDjV69Sw0zAt43480WqJv7HCL0mQnyqFmSrxj8jMa
        -----END PRIVATE KEY-----
    """.trimIndent()

    @Test
    fun importJwk() = runTest {
        // Private
        println("Importing private JWK...")
        val privateKeyResult = JWKKey.importJWK(privateKeyJsonString)
        assertTrue { privateKeyResult.isSuccess }
        println("  Getting private key...")
        val privateKey = privateKeyResult.getOrThrow()

        println("  Checking for private key...")
        assertTrue { privateKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.RSA, privateKey.keyType)

        println("  Checking JWK export matches imported JWK...")
        assertEquals(
            Json.parseToJsonElement(privateKeyJsonString)/*.jsonObject.entries.filterNot { it.key == "kid" }*/,
            Json.parseToJsonElement(privateKey.exportJWK())//.jsonObject.entries.filterNot { it.key == "kid" }
        )
        println("  Checking keyId from thumbprint...")
        assertEquals(privateKey.getThumbprint(), privateKey.getKeyId())

        // Public
        println("Importing public JWK...")
        val publicKeyResult = JWKKey.importJWK(publicKeyJwkString)
        println("  Checking import success...")
        assertTrue { publicKeyResult.isSuccess }
        println("  Getting public key...")
        val publicKey = publicKeyResult.getOrThrow()

        println("  Checking for private key...")
        assertFalse { publicKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.RSA, publicKey.keyType)

        println("  Checking JWK export matches imported JWK...")
        assertEquals(
            Json.parseToJsonElement(publicKeyJwkString)/*.jsonObject.entries.filterNot { it.key == "kid" }*/,
            Json.parseToJsonElement(publicKey.exportJWK())//.jsonObject.entries.filterNot { it.key == "kid" }
        )

        println("  Checking keyId from thumbprint...")
        assertEquals(publicKey.getThumbprint(), publicKey.getKeyId())
    }

    @Test
    fun importPem() = runTest {
        // Private
        println("Importing private PEM...")
        val privateKeyResult = JWKKey.importPEM(privateKeyPem)
        println("  Checking import success...${privateKeyResult.exceptionOrNull() ?: ""}")
        assertTrue { privateKeyResult.isSuccess }

        println("  Getting private key...")
        val privateKey = privateKeyResult.getOrThrow()
        println("  Checking for private key...")
        assertTrue { privateKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.secp256r1, privateKey.keyType)

        /*println("  Checking JWK export matches JWK...")
        assertEquals(
            Json.parseToJsonElement(privateKeyJsonString).jsonObject.filterNot { it.key == "kid" },
            Json.parseToJsonElement(privateKey.exportJWK()).jsonObject
        )*/

        // Public
        println("Importing public PEM...")
        val publicKeyResult = JWKKey.importPEM(publicKeyPem)
        println("  Checking import success...${privateKeyResult.exceptionOrNull() ?: ""}")
        assertTrue { publicKeyResult.isSuccess }

        println("  Getting public key...")
        val publicKey = publicKeyResult.getOrThrow()
        println("  Checking for private key...")
        assertFalse { publicKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.secp256r1, publicKey.keyType)

        /*println("  Checking JWK export matches JWK...")
        assertEquals(
            Json.parseToJsonElement(publicKeyJwkString).jsonObject.filterNot { it.key == "kid" },
            Json.parseToJsonElement(publicKey.exportJWK()).jsonObject
        )*/
    }
}
