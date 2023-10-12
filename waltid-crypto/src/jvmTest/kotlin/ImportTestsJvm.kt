import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportTestsJvm {

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
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqV+fGqTo8r6L52sIJpt4
        4bxLkODaF0/wvIL/eYYDL55H+Ap+b1q4pd4YyZb7pARor/1mob6sMRnnAr5htmO1
        XucmKBEiNY+12zza0q9smjLm3+eNqq+8PgsEqBz4lU1YIBeQzsCR0NTa3J3OHfr+
        bADVystQeonSPoRLqSoO78oAtonQWLX1MUfS9778+ECcxlM21+JaUjqMD0nQR6wl
        8L6oWGcR7PjcjPQAyuS/ASTy7MO0SqunpkGzj/H7uFbK9Np/dLIOr9ZqrkCSdioA
        /PgDyk36E8ayuMnN1HDy4ak/Q7yEX4R/C75T0JxuuYio06hugwyREgOQNID+DVUo
        LwIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()

    private val privateKeyPem = """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEpAIBAAKCAQEAqV+fGqTo8r6L52sIJpt44bxLkODaF0/wvIL/eYYDL55H+Ap+
        b1q4pd4YyZb7pARor/1mob6sMRnnAr5htmO1XucmKBEiNY+12zza0q9smjLm3+eN
        qq+8PgsEqBz4lU1YIBeQzsCR0NTa3J3OHfr+bADVystQeonSPoRLqSoO78oAtonQ
        WLX1MUfS9778+ECcxlM21+JaUjqMD0nQR6wl8L6oWGcR7PjcjPQAyuS/ASTy7MO0
        SqunpkGzj/H7uFbK9Np/dLIOr9ZqrkCSdioA/PgDyk36E8ayuMnN1HDy4ak/Q7yE
        X4R/C75T0JxuuYio06hugwyREgOQNID+DVUoLwIDAQABAoIBAQCGd1HbV11RipGL
        0l+QNxJLNLBRfxHmPCMFpoKougpBfcnpVHt4cG/zz1WihemWF6H9RpJ6iuQtv0C1
        3uu4X4SYqa6TVLbyCvv36GJZrcfsy8ibrju8bPRn1VuHFCkOb28tW0gtvJiHUNXJ
        HMeM6b2fhTI2ZB+qiUyPMXzX+noNR+mQxwMElKWxOZEoxY8bS7yYWbxoH1l+1uvI
        87fpA6fLuDj8zxJkbXLEZNvKmPINM/1aoyYeloL7rqAuzOPLHARtWHr2b4ewMSzt
        CD2gxUbYWLC0NJcgCnB8uTa/ZiTj5N4APj2tnPD5BwnTKo95V2rxyDIU6HBf5w+1
        AibxfsCBAoGBANGzaSc8zjoM6amlpG/uT7lIGPQHnVLJ83tuALbpF2jfVWO3uQS2
        HGxNdURnso/JJIaaLb2UOlq2BH70pH8Qb6FXNavOhV4qpacJlC/v4zuAW4BEjivX
        mlj9Ylplja2aasHZyHhd1WR3wiKBcUVf02/0qfFDUrnhYAh+iFSZX8EhAoGBAM7E
        2q0WKPZSxPF786Rs6Y7azh7Jy7pNp+Rlz+jNl/PQS/MnPP+RaffZvxJvbxgDoWQS
        vQHLWbmRBklVYX1dSJhsNe7S8kGUggL0s0KWUmKU/RA3mYJ/6EqK31IDCqaZtV+K
        5eWM+5TEdZIZY3Srf1+oBQOgAy9ofwEStWwUY69PAoGBAJTn6k5jfil4i9/ccHTO
        66usx5NZaNyl7RCDn1xDDk148UCa8HWo/2vkYNYPMJurgBVYnAxXmkxZnb2s6LYV
        rL8Ll2AFiWzBqdmAEssrc9cHoXHmvHHjaoWwf8ui+0UANriqdhEKyIHMDH3GHvHd
        Rt3kBVz9qlu17yR4/UPdmUIhAoGAE7TDOpfQE5nT10f+8n7Gy6yi1GBbIEhiZewm
        IoPlpYEGnAfzUlAjj1GbWkBwkBNYgFcg2FjvFjZyKO8QOYh4cL5vbXGBUSq8MVfs
        9b2p4Gdervr9kGhsVR5jJkfP7gzcMlzkiDoliAopQmFVDzuBCjbTM4M+inglEo8b
        508SKRUCgYBomwMGHFAUNulCvbROODEibAGyVU/3Ehq7LedwKaMeLHoVzWXnWAvv
        VXU5YaddpK0Z+hbLSAqRScm5Tf9f/ED9DDMBc8lhr6O5GUPwHFN/uuZH8cL5eJV3
        I2eRTdp8e0ridKFLYS43YRc6tgOttCgDXf9roi2T/nm8OkneNyLBLw==
        -----END RSA PRIVATE KEY-----
    """.trimIndent()

    @Test
    fun importJwk() = runTest {
        // Private
        println("Importing private JWK...")
        val privateKeyResult = LocalKey.importJWK(privateKeyJsonString)
        println("  Checking import success...")
        assertTrue { privateKeyResult.isSuccess }
        println("  Getting private key...")
        val privateKey = privateKeyResult.getOrThrow()

        println("  Checking for private key...")
        assertTrue { privateKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.RSA, privateKey.keyType)

        println("  Checking JWK export matches imported JWK...")
        assertEquals(
            Json.parseToJsonElement(privateKeyJsonString),
            Json.parseToJsonElement(privateKey.exportJWK())
        )
        println("  Checking keyId from thumbprint...")
        assertEquals(privateKey.getThumbprint(), privateKey.getKeyId())

        // Public
        println("Importing public JWK...")
        val publicKeyResult = LocalKey.importJWK(publicKeyJwkString)
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
            Json.parseToJsonElement(publicKeyJwkString),
            Json.parseToJsonElement(publicKey.exportJWK())
        )

        println("  Checking keyId from thumbprint...")
        assertEquals(publicKey.getThumbprint(), publicKey.getKeyId())
    }

    @Test
    fun importPem() = runTest {
        // Private
        println("Importing private PEM...")
        val privateKeyResult = LocalKey.importPEM(privateKeyPem)
        println("  Checking import success...")
        assertTrue { privateKeyResult.isSuccess }

        println("  Getting private key...")
        val privateKey = privateKeyResult.getOrThrow()
        println("  Checking for private key...")
        assertTrue { privateKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.RSA, privateKey.keyType)

        println("  Checking JWK export matches JWK...")
        assertEquals(
            Json.parseToJsonElement(privateKeyJsonString).jsonObject.filterNot { it.key == "kid" },
            Json.parseToJsonElement(privateKey.exportJWK()).jsonObject
        )

        // Public
        println("Importing public PEM...")
        val publicKeyResult = LocalKey.importPEM(publicKeyPem)
        println("  Checking import success...")
        assertTrue { publicKeyResult.isSuccess }

        println("  Getting public key...")
        val publicKey = publicKeyResult.getOrThrow()
        println("  Checking for private key...")
        assertFalse { publicKey.hasPrivateKey }
        println("  Checking for correct key type...")
        assertEquals(KeyType.RSA, publicKey.keyType)

        println("  Checking JWK export matches JWK...")
        assertEquals(
            Json.parseToJsonElement(publicKeyJwkString).jsonObject.filterNot { it.key == "kid" },
            Json.parseToJsonElement(publicKey.exportJWK()).jsonObject
        )
    }
}
