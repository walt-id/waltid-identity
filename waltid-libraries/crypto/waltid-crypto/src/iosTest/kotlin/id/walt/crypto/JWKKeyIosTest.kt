package id.walt.crypto

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JWKKeyIosTest {

    private val softwareKeyTypes = listOf(KeyType.Ed25519, KeyType.secp256k1)

    @Test
    fun generate() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            assertNotNull(key, "generate failed for $type")
            assertEquals(type, key.keyType, "keyType mismatch for $type")
            assertTrue(key.hasPrivateKey, "hasPrivateKey false for $type")
        }
    }

    @Test
    fun exportJwk() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val jwk = key.exportJWK()
            assertTrue(jwk.contains("\"d\""), "private key 'd' missing for $type")
            when (type) {
                KeyType.Ed25519 -> {
                    assertTrue(jwk.contains("\"OKP\""), "kty OKP missing for Ed25519")
                    assertTrue(jwk.contains("\"Ed25519\""), "crv Ed25519 missing")
                }
                KeyType.secp256k1 -> {
                    assertTrue(jwk.contains("\"EC\""), "kty EC missing for secp256k1")
                    assertTrue(jwk.contains("\"secp256k1\""), "crv secp256k1 missing")
                }
                else -> {}
            }
        }
    }

    @Test
    fun signAndVerifyRaw() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val plaintext = "Hello $type".encodeToByteArray()

            val signature = key.signRaw(plaintext)
            assertNotNull(signature, "signRaw returned null for $type")
            assertTrue(signature.isNotEmpty(), "signature empty for $type")

            val result = key.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun signAndVerifyJws() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val payload = """{"sub":"test","type":"$type"}""".encodeToByteArray()

            val jws = key.signJws(payload)
            assertNotNull(jws, "signJws returned null for $type")
            assertEquals(2, jws.count { it == '.' }, "JWS dot count wrong for $type")

            val result = key.verifyJws(jws)
            assertTrue(result.isSuccess, "verifyJws failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun publicKeyExtraction() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val pubKey = key.getPublicKey()
            assertEquals(type, pubKey.keyType, "public key type mismatch for $type")
            assertEquals(key.getKeyId(), pubKey.getKeyId(), "public key id mismatch for $type")
            assertTrue(!pubKey.hasPrivateKey, "public key has private for $type")
            assertTrue(!pubKey.exportJWK().contains("\"d\""), "public JWK contains 'd' for $type")
        }
    }

    @Test
    fun importJwkRoundtrip() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val exported = key.exportJWK()

            val imported = JWKKey.importJWK(exported).getOrThrow()
            assertEquals(type, imported.keyType, "imported keyType mismatch for $type")
            assertTrue(imported.hasPrivateKey, "imported key missing private for $type")

            val plaintext = "roundtrip $type".encodeToByteArray()
            val sig = imported.signRaw(plaintext)
            val result = imported.verifyRaw(sig, plaintext)
            assertTrue(result.isSuccess, "imported key sign/verify failed for $type")
        }
    }

    @Test
    fun importRawPublicSoftwareKeys() = runTest {
        for (type in softwareKeyTypes) {
            val privateKey = JWKKey.generate(type)
            val rawPublicKey = privateKey.getPublicKeyRepresentation()
            val imported = JWKKey.importRawPublicKey(type, rawPublicKey, JwkKeyMeta("raw-$type")) as JWKKey

            assertEquals(type, imported.keyType, "imported keyType mismatch for $type")
            assertTrue(!imported.hasPrivateKey, "raw public import should not create private material for $type")

            val plaintext = "raw import $type".encodeToByteArray()
            val signature = privateKey.signRaw(plaintext)
            val result = imported.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "raw imported public key verify failed for $type: ${result.exceptionOrNull()}")

            val importedWithoutKid = JWKKey.importRawPublicKey(type, rawPublicKey, null) as JWKKey
            assertTrue("kid" !in importedWithoutKid.exportJWKObject(), "missing metadata should not add kid")
        }
    }

    @Test
    fun importJwkAcceptsSecp256k1P256KAlias() = runTest {
        val key = JWKKey.generate(KeyType.secp256k1)
        val aliasedJwk = key.exportJWK().replace("\"secp256k1\"", "\"P-256K\"")

        val imported = JWKKey.importJWK(aliasedJwk).getOrThrow()

        assertEquals(KeyType.secp256k1, imported.keyType)
    }

    @Test
    fun keySerializationInitializes() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val serialized = KeySerialization.serializeKey(key)

        assertTrue(serialized.contains("\"jwk\""), "serialized key should use JWK polymorphic type")
    }

    @Test
    fun rejectsPkcs8PrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported(pkcs8RsaPrivateKeyPem)
    }

    @Test
    fun rejectsPkcs1RsaPrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported(pkcs1RsaPrivateKeyPem)
    }

    @Test
    fun rejectsSec1EcPrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported(sec1P256PrivateKeyPem)
    }

    @Test
    fun importsPublicKeyPem() = runTest {
        val key = JWKKey.importPEM(publicKeyPem).getOrThrow()

        assertEquals(KeyType.secp256r1, key.keyType)
        assertFalse(key.hasPrivateKey)
    }

    @Test
    fun importsCertificatePemAsPublicKey() = runTest {
        val key = JWKKey.importPEM(certificatePem).getOrThrow()

        assertEquals(KeyType.secp256r1, key.keyType)
        assertFalse(key.hasPrivateKey)
    }

    private suspend fun assertPrivatePemImportUnsupported(pem: String) {
        assertFailsWith<UnsupportedOperationException> {
            JWKKey.importPEM(pem).getOrThrow()
        }
    }

    companion object {
        private val pkcs8RsaPrivateKeyPem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCpX58apOjyvovn
            awgmm3jhvEuQ4NoXT/C8gv95hgMvnkf4Cn5vWril3hjJlvukBGiv/WahvqwxGecC
            vmG2Y7Ve5yYoESI1j7XbPNrSr2yaMubf542qr7w+CwSoHPiVTVggF5DOwJHQ1Nrc
            nc4d+v5sANXKy1B6idI+hEupKg7vygC2idBYtfUxR9L3vvz4QJzGUzbX4lpSOowP
            SdBHrCXwvqhYZxHs+NyM9ADK5L8BJPLsw7RKq6emQbOP8fu4Vsr02n90sg6v1mqu
            QJJ2KgD8+APKTfoTxrK4yc3UcPLhqT9DvIRfhH8LvlPQnG65iKjTqG6DDJESA5A0
            gP4NVSgvAgMBAAECggEBAIZ3UdtXXVGKkYvSX5A3Eks0sFF/EeY8IwWmgqi6CkF9
            yelUe3hwb/PPVaKF6ZYXof1GknqK5C2/QLXe67hfhJiprpNUtvIK+/foYlmtx+zL
            yJuuO7xs9GfVW4cUKQ5vby1bSC28mIdQ1ckcx4zpvZ+FMjZkH6qJTI8xfNf6eg1H
            6ZDHAwSUpbE5kSjFjxtLvJhZvGgfWX7W68jzt+kDp8u4OPzPEmRtcsRk28qY8g0z
            /VqjJh6WgvuuoC7M48scBG1YevZvh7AxLO0IPaDFRthYsLQ0lyAKcHy5Nr9mJOPk
            3gA+Pa2c8PkHCdMqj3lXavHIMhTocF/nD7UCJvF+wIECgYEA0bNpJzzOOgzpqaWk
            b+5PuUgY9AedUsnze24AtukXaN9VY7e5BLYcbE11RGeyj8kkhpotvZQ6WrYEfvSk
            fxBvoVc1q86FXiqlpwmUL+/jO4BbgESOK9eaWP1iWmWNrZpqwdnIeF3VZHfCIoFx
            RV/Tb/Sp8UNSueFgCH6IVJlfwSECgYEAzsTarRYo9lLE8XvzpGzpjtrOHsnLuk2n
            5GXP6M2X89BL8yc8/5Fp99m/Em9vGAOhZBK9ActZuZEGSVVhfV1ImGw17tLyQZSC
            AvSzQpZSYpT9EDeZgn/oSorfUgMKppm1X4rl5Yz7lMR1khljdKt/X6gFA6ADL2h/
            ARK1bBRjr08CgYEAlOfqTmN+KXiL39xwdM7rq6zHk1lo3KXtEIOfXEMOTXjxQJrw
            daj/a+Rg1g8wm6uAFVicDFeaTFmdvazothWsvwuXYAWJbMGp2YASyytz1wehcea8
            ceNqhbB/y6L7RQA2uKp2EQrIgcwMfcYe8d1G3eQFXP2qW7XvJHj9Q92ZQiECgYAT
            tMM6l9ATmdPXR/7yfsbLrKLUYFsgSGJl7CYig+WlgQacB/NSUCOPUZtaQHCQE1iA
            VyDYWO8WNnIo7xA5iHhwvm9tcYFRKrwxV+z1vangZ16u+v2QaGxVHmMmR8/uDNwy
            XOSIOiWICilCYVUPO4EKNtMzgz6KeCUSjxvnTxIpFQKBgGibAwYcUBQ26UK9tE44
            MSJsAbJVT/cSGrst53Apox4sehXNZedYC+9VdTlhp12krRn6FstICpFJyblN/1/8
            QP0MMwFzyWGvo7kZQ/AcU3+65kfxwvl4lXcjZ5FN2nx7SuJ0oUthLjdhFzq2A620
            KANd/2uiLZP+ebw6Sd43IsEv
            -----END PRIVATE KEY-----
        """.trimIndent()

        private val pkcs1RsaPrivateKeyPem = """
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

        private val sec1P256PrivateKeyPem = """
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEINStC8Q+Xgk8Q9EUAAy20pnQba+DLuqQPbgDPYqLbimSoAoGCCqGSM49
            AwEHoUQDQgAEuo896Ho570UP24xyyNt7dE3U6qHlDNJth0Hc/u/uJ2H0+7gRyILH
            JOH15UTFrQWcmIlnnzNAplM+d8pelYwK2g==
            -----END EC PRIVATE KEY-----
        """.trimIndent()

        private val publicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuo896Ho570UP24xyyNt7dE3U6qHl
            DNJth0Hc/u/uJ2H0+7gRyILHJOH15UTFrQWcmIlnnzNAplM+d8pelYwK2g==
            -----END PUBLIC KEY-----
        """.trimIndent()

        private val certificatePem = """
            -----BEGIN CERTIFICATE-----
            MIIC8TCCApigAwIBAgIKXkW8UsGZRHyHyDAKBggqhkjOPQQDAjB1MQswCQYDVQQG
            EwJBVTEtMCsGA1UEAwwkQXVzdHJvYWRzIFByZS1wcm9kdWN0aW9uIERUUyBSb290
            IENBMTcwNQYDVQQKDC5BdXN0cm9hZHMgUHJlLXByb2R1Y3Rpb24gRGlnaXRhbCBU
            cnVzdCBTZXJ2aWNlMB4XDTI0MDkwMjAzMjQ0MVoXDTQ0MDkwMjAzMjQ0MVowdTEL
            MAkGA1UEBhMCQVUxLTArBgNVBAMMJEF1c3Ryb2FkcyBQcmUtcHJvZHVjdGlvbiBE
            VFMgUm9vdCBDQTE3MDUGA1UECgwuQXVzdHJvYWRzIFByZS1wcm9kdWN0aW9uIERp
            Z2l0YWwgVHJ1c3QgU2VydmljZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABMJt
            zT2r7UHttv6jSGEso6dVKF9QwsEyXcb4EUzciXonsLIorpUiG5tuuIUoD0fRbGNV
            KD4yA3KJs6R8pe/94c6jggEOMIIBCjASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1Ud
            DwEB/wQEAwIBBjAdBgNVHQ4EFgQUjQCqUhB6NiazWEI4a0qWZ7mXlRYwgcQGA1Ud
            HwSBvDCBuTCBtqCBs6CBsIaBrWh0dHBzOi8vYXVzdHJvYWRzLWR0cy1wcmUtcHJk
            LnZpaS5hdTAxLm1hdHRyLmdsb2JhbC92MS9lY29zeXN0ZW1zLzljOTVmNjY2LWNk
            Y2UtNGU4YS1iY2Q3LWRkNzQ0ZjQ3ODhmNC92aWNhbHMvcHVibGljL2NlcnRpZmlj
            YXRlcy9jYS9kOTRkZTEyNi1lOTgyLTRmOTUtYTUzNS1iZDA3NjcwOWU2NmYvY3Js
            MAoGCCqGSM49BAMCA0cAMEQCIGxwNRWAq0B4DU/OlHjal0gULknk3JD4w1+Mtrpb
            yPxFAiAaQMxnrcRJopU6SRrNTq1x29UlFJdaE7XHvdXu1sXnDA==
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
