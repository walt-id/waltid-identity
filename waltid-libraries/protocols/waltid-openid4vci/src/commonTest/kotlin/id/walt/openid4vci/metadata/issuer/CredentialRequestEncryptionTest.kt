package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialRequestEncryptionTest {

    @Test
    fun `valid request encryption metadata passes validation`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("key-1"))
                        }
                    )
                )
            )
        }

        CredentialRequestEncryption(
            jwks = jwks,
            encValuesSupported = setOf("A128GCM"),
            zipValuesSupported = setOf("DEF"),
            encryptionRequired = true,
        )
    }

    @Test
    fun `enc values supported must not be empty`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("key-1"))
                        }
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestEncryption(
                jwks = jwks,
                encValuesSupported = emptySet(),
                encryptionRequired = true,
            )
        }
    }

    @Test
    fun `jwks must contain keys with kid`() {
        val jwksWithoutKid = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                        }
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestEncryption(
                jwks = jwksWithoutKid,
                encValuesSupported = setOf("A128GCM"),
                encryptionRequired = true,
            )
        }
    }
}
