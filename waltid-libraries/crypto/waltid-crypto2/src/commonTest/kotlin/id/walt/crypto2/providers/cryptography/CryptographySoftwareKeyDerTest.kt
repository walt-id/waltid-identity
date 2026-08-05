package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.keys.decodePublicKeyPem
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptographySoftwareKeyDerTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `known EC Ed25519 and RSA PKCS8 and SPKI vectors interoperate with JWK`() = runTest {
        if (!supportsPrivateDerImport(KeySpec.Ec(EcCurve.P256))) return@runTest
        vectors.forEachIndexed { index, vector ->
            val privateStored = stored(
                id = "${vector.name}-private",
                spec = vector.spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                material = vector.privatePem.decodePrivateKeyPem(),
            )
            val privateKey = restart(privateStored)
            val publicSpki = assertIs<EncodedKey.SpkiDer>(
                assertNotNull(privateKey.capabilities.publicKeyExporter)
                    .exportPublicKey(KeyEncodingFormat.SPKI_DER),
            )
            assertContentEquals(
                vector.publicPem.decodePublicKeyPem().data.toByteArray(),
                publicSpki.data.toByteArray(),
                vector.name,
            )
            val privatePkcs8 = assertIs<EncodedKey.Pkcs8Der>(
                assertNotNull(privateKey.capabilities.privateKeyExporter)
                    .exportPrivateKey(KeyEncodingFormat.PKCS8_DER),
            )
            val privateJwk = assertIs<EncodedKey.Jwk>(
                assertNotNull(privateKey.capabilities.privateKeyExporter).exportPrivateKey(),
            )
            assertTrue(privateJwk.privateMaterial)

            val publicKey = restart(
                stored(
                    id = "${vector.name}-public",
                    spec = vector.spec,
                    usages = setOf(KeyUsage.VERIFY),
                    material = vector.publicPem.decodePublicKeyPem(),
                ),
            )
            assertNull(publicKey.capabilities.signer)
            assertNull(publicKey.capabilities.privateKeyExporter)
            val publicJwk = assertIs<EncodedKey.Jwk>(
                assertNotNull(publicKey.capabilities.publicKeyExporter).exportPublicKey(),
            )
            assertFalse(publicJwk.privateMaterial)

            val message = "known-vector-$index".encodeToByteArray()
            val signature = assertNotNull(privateKey.capabilities.signer).sign(message, vector.algorithm)
            assertTrue(assertNotNull(publicKey.capabilities.verifier).verify(message, signature, vector.algorithm))

            val privateFromJwk = restart(privateStored.copy(material = privateJwk))
            val publicFromJwk = restart(
                privateStored.copy(
                    id = KeyId("${vector.name}-jwk-public"),
                    usages = setOf(KeyUsage.VERIFY),
                    material = publicJwk,
                ),
            )
            val jwkSignature = assertNotNull(privateFromJwk.capabilities.signer).sign(message, vector.algorithm)
            assertTrue(assertNotNull(publicFromJwk.capabilities.verifier).verify(message, jwkSignature, vector.algorithm))

            val privateFromPkcs8 = restart(privateStored.copy(material = privatePkcs8))
            val derSignature = assertNotNull(privateFromPkcs8.capabilities.signer).sign(message, vector.algorithm)
            assertTrue(assertNotNull(publicKey.capabilities.verifier).verify(message, derSignature, vector.algorithm))
        }
    }

    @Test
    fun `PKCS8 generation persists DER and exports both DER and JWK`() = runTest {
        if (!supportsPrivateDerGeneration()) return@runTest
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("generated-pkcs8"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                keyEncoding = KeyEncodingFormat.PKCS8_DER,
            ),
        )
        assertIs<EncodedKey.Pkcs8Der>(generated.storedKey.material)

        val restarted = restart(generated.storedKey)
        assertIs<EncodedKey.Jwk>(assertNotNull(restarted.capabilities.publicKeyExporter).exportPublicKey())
        assertIs<EncodedKey.SpkiDer>(
            assertNotNull(restarted.capabilities.publicKeyExporter).exportPublicKey(KeyEncodingFormat.SPKI_DER),
        )
        assertIs<EncodedKey.Jwk>(assertNotNull(restarted.capabilities.privateKeyExporter).exportPrivateKey())
        assertIs<EncodedKey.Pkcs8Der>(
            assertNotNull(restarted.capabilities.privateKeyExporter).exportPrivateKey(KeyEncodingFormat.PKCS8_DER),
        )
    }

    @Test
    fun `DER import rejects key kind spec and usage mismatches`() = runTest {
        if (!supportsPrivateDerImport(KeySpec.Ec(EcCurve.P256))) return@runTest
        val vector = vectors.first()
        val publicDer = vector.publicPem.decodePublicKeyPem().data
        val privateDer = vector.privatePem.decodePrivateKeyPem().data

        assertFails {
            provider.restore(
                stored(
                    id = "public-as-private",
                    spec = vector.spec,
                    usages = setOf(KeyUsage.SIGN),
                    material = EncodedKey.Pkcs8Der(publicDer),
                ),
            )
        }
        assertFails {
            provider.restore(
                stored(
                    id = "private-as-public",
                    spec = vector.spec,
                    usages = setOf(KeyUsage.VERIFY),
                    material = EncodedKey.SpkiDer(privateDer),
                ),
            )
        }
        assertFails {
            provider.restore(
                stored(
                    id = "wrong-curve",
                    spec = KeySpec.Ec(EcCurve.P384),
                    usages = setOf(KeyUsage.VERIFY),
                    material = vector.publicPem.decodePublicKeyPem(),
                ),
            )
        }
        assertFails {
            provider.restore(
                stored(
                    id = "public-private-usage",
                    spec = vector.spec,
                    usages = setOf(KeyUsage.SIGN),
                    material = vector.publicPem.decodePublicKeyPem(),
                ),
            )
        }
        assertFails {
            provider.restore(
                stored(
                    id = "private-public-usage",
                    spec = vector.spec,
                    usages = setOf(KeyUsage.VERIFY),
                    material = vector.privatePem.decodePrivateKeyPem(),
                ),
            )
        }
    }

    @Test
    fun `capability profile reports DER formats without overclaiming usages`() {
        val publicRequirement = CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.VERIFY),
            keyEncoding = KeyEncodingFormat.SPKI_DER,
        )
        val privateRequirement = publicRequirement.copy(
            usages = setOf(KeyUsage.SIGN),
            keyEncoding = KeyEncodingFormat.PKCS8_DER,
        )

        assertTrue(provider.supports(publicRequirement))
        assertEquals(
            KeyEncodingFormat.PKCS8_DER in CryptographyCapabilityProfile.Portable.keyImportFormats,
            provider.supports(privateRequirement),
        )
        assertFalse(provider.supports(publicRequirement.copy(usages = setOf(KeyUsage.SIGN))))
        assertFalse(provider.supports(privateRequirement.copy(usages = setOf(KeyUsage.VERIFY))))
        assertTrue(
            provider.supports(
                publicRequirement.copy(operation = CryptoOperation.EXPORT_PUBLIC),
            ),
        )
        assertFalse(
            provider.supports(
                publicRequirement.copy(
                    operation = CryptoOperation.EXPORT_PUBLIC,
                    keyEncoding = KeyEncodingFormat.PKCS8_DER,
                ),
            ),
        )

        val jwkOnly = CryptographySoftwareKeyProvider(
            profile = CryptographyCapabilityProfile.Portable.copy(
                keyGenerationFormats = setOf(KeyEncodingFormat.JWK),
                keyImportFormats = setOf(KeyEncodingFormat.JWK),
                publicKeyExportFormats = setOf(KeyEncodingFormat.JWK),
                privateKeyExportFormats = setOf(KeyEncodingFormat.JWK),
            ),
        )
        assertFalse(jwkOnly.supports(publicRequirement))
        assertFalse(jwkOnly.supports(privateRequirement))

        val derOnly = CryptographySoftwareKeyProvider(
            profile = CryptographyCapabilityProfile.Portable.copy(
                keyGenerationFormats = setOf(KeyEncodingFormat.PKCS8_DER),
                keyImportFormats = setOf(KeyEncodingFormat.SPKI_DER, KeyEncodingFormat.PKCS8_DER),
                publicKeyExportFormats = setOf(KeyEncodingFormat.SPKI_DER),
                privateKeyExportFormats = setOf(KeyEncodingFormat.PKCS8_DER),
            ),
        )
        assertFalse(
            derOnly.supports(
                publicRequirement.copy(operation = CryptoOperation.EXPORT_PUBLIC, keyEncoding = null),
            ),
        )
        assertTrue(derOnly.supports(publicRequirement.copy(operation = CryptoOperation.EXPORT_PUBLIC)))
    }

    private fun stored(
        id: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        material: EncodedKey,
    ) = StoredKey.Software(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId(id),
        spec = spec,
        usages = usages,
        material = material,
    )

    private suspend fun restart(stored: StoredKey.Software): SoftwareKey =
        runtime.restore(StoredKeyCodec.decodeFromString(StoredKeyCodec.encodeToString(stored))) as SoftwareKey

    private fun supportsPrivateDerGeneration(): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.GENERATE_KEY,
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            keyEncoding = KeyEncodingFormat.PKCS8_DER,
        ),
    )

    private fun supportsPrivateDerImport(spec: KeySpec): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            keyEncoding = KeyEncodingFormat.PKCS8_DER,
        ),
    )

    private data class KeyVector(
        val name: String,
        val spec: KeySpec,
        val algorithm: SignatureAlgorithm,
        val privatePem: String,
        val publicPem: String,
    )

    private companion object {
        val vectors = listOf(
            KeyVector(
                name = "P-256",
                spec = KeySpec.Ec(EcCurve.P256),
                algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
                privatePem = """
                    -----BEGIN PRIVATE KEY-----
                    MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg1K0LxD5eCTxD0RQA
                    DLbSmdBtr4Mu6pA9uAM9iotuKZKgCgYIKoZIzj0DAQehRANCAAS6jz3oejnvRQ/b
                    jHLI23t0TdTqoeUM0m2HQdz+7+4nYfT7uBHIgsck4fXlRMWtBZyYiWefM0CmUz53
                    yl6VjAra
                    -----END PRIVATE KEY-----
                """.trimIndent() + "\n",
                publicPem = """
                    -----BEGIN PUBLIC KEY-----
                    MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuo896Ho570UP24xyyNt7dE3U6qHl
                    DNJth0Hc/u/uJ2H0+7gRyILHJOH15UTFrQWcmIlnnzNAplM+d8pelYwK2g==
                    -----END PUBLIC KEY-----
                """.trimIndent() + "\n",
            ),
            KeyVector(
                name = "Ed25519",
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                algorithm = SignatureAlgorithm.EdDsa,
                privatePem = """
                    -----BEGIN PRIVATE KEY-----
                    MC4CAQAwBQYDK2VwBCIEIJpchqgaS5BrgnM/AfWg9DJp6iE/spWXuQhea3+FIlyH
                    -----END PRIVATE KEY-----
                """.trimIndent() + "\n",
                publicPem = """
                    -----BEGIN PUBLIC KEY-----
                    MCowBQYDK2VwAyEAKCy/sIsTN8yMMZXZVqiX4mdzQPjZYt+wwdGp0QQwq2I=
                    -----END PUBLIC KEY-----
                """.trimIndent() + "\n",
            ),
            KeyVector(
                name = "RSA",
                spec = KeySpec.Rsa(2048),
                algorithm = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256),
                privatePem = """
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
                """.trimIndent() + "\n",
                publicPem = """
                    -----BEGIN PUBLIC KEY-----
                    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqV+fGqTo8r6L52sIJpt4
                    4bxLkODaF0/wvIL/eYYDL55H+Ap+b1q4pd4YyZb7pARor/1mob6sMRnnAr5htmO1
                    XucmKBEiNY+12zza0q9smjLm3+eNqq+8PgsEqBz4lU1YIBeQzsCR0NTa3J3OHfr+
                    bADVystQeonSPoRLqSoO78oAtonQWLX1MUfS9778+ECcxlM21+JaUjqMD0nQR6wl
                    8L6oWGcR7PjcjPQAyuS/ASTy7MO0SqunpkGzj/H7uFbK9Np/dLIOr9ZqrkCSdioA
                    /PgDyk36E8ayuMnN1HDy4ak/Q7yEX4R/C75T0JxuuYio06hugwyREgOQNID+DVUo
                    LwIDAQAB
                    -----END PUBLIC KEY-----
                """.trimIndent() + "\n",
            ),
        )
    }
}
