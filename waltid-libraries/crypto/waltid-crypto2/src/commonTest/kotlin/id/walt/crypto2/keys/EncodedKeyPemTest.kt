package id.walt.crypto2.keys

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncodedKeyPemTest {
    @Test
    fun `known SPKI and PKCS8 PEM vectors round trip canonically`() {
        val publicKey = P256_PUBLIC_PEM.decodePublicKeyPem()
        val privateKey = P256_PRIVATE_PEM.decodePrivateKeyPem()

        assertEquals(P256_PUBLIC_PEM, publicKey.encodePem())
        assertEquals(P256_PRIVATE_PEM, privateKey.encodePem())
        assertContentEquals(publicKey.data.toByteArray(), publicKey.encodePem().decodePublicKeyPem().data.toByteArray())
        assertContentEquals(privateKey.data.toByteArray(), privateKey.encodePem().decodePrivateKeyPem().data.toByteArray())
    }

    @Test
    fun `PEM decoder rejects key kind mismatch encrypted and unsupported labels`() {
        assertFailsWith<IllegalArgumentException> { P256_PRIVATE_PEM.decodePublicKeyPem() }
        assertFailsWith<IllegalArgumentException> { P256_PUBLIC_PEM.decodePrivateKeyPem() }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMA==\n-----END ENCRYPTED PRIVATE KEY-----\n"
                .decodePrivateKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN RSA PRIVATE KEY-----\nMA==\n-----END RSA PRIVATE KEY-----\n".decodePrivateKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN CERTIFICATE-----\nMA==\n-----END CERTIFICATE-----\n".decodePublicKeyPem()
        }
    }

    @Test
    fun `PEM decoder rejects malformed noncanonical and non-ASCII input`() {
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN PUBLIC KEY-----\nM*==\n-----END PUBLIC KEY-----\n".decodePublicKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN PUBLIC KEY-----\nMB==\n-----END PUBLIC KEY-----\n".decodePublicKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN PUBLIC KEY-----\nMA==\nMA==\n-----END PUBLIC KEY-----\n".decodePublicKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "prefix\n-----BEGIN PUBLIC KEY-----\nMA==\n-----END PUBLIC KEY-----\n".decodePublicKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN PUBLIC KEY-----\nMA==\n-----END PUBLIC KEY-----\nsuffix".decodePublicKeyPem()
        }
        assertFailsWith<IllegalArgumentException> {
            "-----BEGIN PUBLIC KEY-----\nMA==\n-----END PUBLIC KEY-----\u00a0".decodePublicKeyPem()
        }
    }

    private companion object {
        val P256_PUBLIC_PEM = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuo896Ho570UP24xyyNt7dE3U6qHl
            DNJth0Hc/u/uJ2H0+7gRyILHJOH15UTFrQWcmIlnnzNAplM+d8pelYwK2g==
            -----END PUBLIC KEY-----
        """.trimIndent() + "\n"

        val P256_PRIVATE_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg1K0LxD5eCTxD0RQA
            DLbSmdBtr4Mu6pA9uAM9iotuKZKgCgYIKoZIzj0DAQehRANCAAS6jz3oejnvRQ/b
            jHLI23t0TdTqoeUM0m2HQdz+7+4nYfT7uBHIgsck4fXlRMWtBZyYiWefM0CmUz53
            yl6VjAra
            -----END PRIVATE KEY-----
        """.trimIndent() + "\n"
    }
}
