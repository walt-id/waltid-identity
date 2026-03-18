package id.walt.openid4vci.metadata.issuer

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialResponseEncryptionTest {

    @Test
    fun `valid response encryption metadata passes validation`() {
        CredentialResponseEncryption(
            algValuesSupported = setOf("ECDH-ES"),
            encValuesSupported = setOf("A128GCM"),
            zipValuesSupported = setOf("DEF"),
            encryptionRequired = true,
        )
    }

    @Test
    fun `alg values supported must not be empty`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryption(
                algValuesSupported = emptySet(),
                encValuesSupported = setOf("A128GCM"),
                encryptionRequired = true,
            )
        }
    }

    @Test
    fun `enc values supported must not be empty`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryption(
                algValuesSupported = setOf("ECDH-ES"),
                encValuesSupported = emptySet(),
                encryptionRequired = true,
            )
        }
    }
}
