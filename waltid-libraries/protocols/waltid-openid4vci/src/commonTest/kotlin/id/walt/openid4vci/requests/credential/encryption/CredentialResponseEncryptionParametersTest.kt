package id.walt.openid4vci.requests.credential.encryption

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialResponseEncryptionParametersTest {

    @Test
    fun `valid P-256 ECDH-ES A128GCM request passes validation`() {
        val request = CredentialResponseEncryptionParameters.fromJsonObject(
            buildJsonObject {
                put("jwk", validWalletJwk())
                put("enc", JsonPrimitive("A128GCM"))
            }
        )

        assertEquals("ECDH-ES", request.alg)
        assertEquals("A128GCM", request.enc)
    }

    @Test
    fun `request rejects unsupported encryption algorithms`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put("jwk", validWalletJwk())
                    put("enc", JsonPrimitive("A256GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects unsupported key algorithms`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put(
                        "jwk",
                        validWalletJwk(
                            "alg" to JsonPrimitive("RSA-OAEP"),
                        ),
                    )
                    put("enc", JsonPrimitive("A128GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects unsupported key curves`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put(
                        "jwk",
                        validWalletJwk(
                            "crv" to JsonPrimitive("P-384"),
                        ),
                    )
                    put("enc", JsonPrimitive("A128GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects missing key id`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put(
                        "jwk",
                        validWalletJwk(
                            "kid" to JsonPrimitive(""),
                        ),
                    )
                    put("enc", JsonPrimitive("A128GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects incomplete EC public keys`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put(
                        "jwk",
                        validWalletJwk(
                            "x" to JsonPrimitive(""),
                        ),
                    )
                    put("enc", JsonPrimitive("A128GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects private key material`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put(
                        "jwk",
                        validWalletJwk(
                            "d" to JsonPrimitive("private-key-material"),
                        ),
                    )
                    put("enc", JsonPrimitive("A128GCM"))
                }
            )
        }
    }

    @Test
    fun `request rejects compression`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialResponseEncryptionParameters.fromJsonObject(
                buildJsonObject {
                    put("jwk", validWalletJwk())
                    put("enc", JsonPrimitive("A128GCM"))
                    put("zip", JsonPrimitive("DEF"))
                }
            )
        }
    }

    private fun validWalletJwk(vararg replacements: Pair<String, JsonPrimitive>): JsonObject {
        val replacementMap = replacements.toMap()
        return buildJsonObject {
            put("kty", replacementMap["kty"] ?: JsonPrimitive("EC"))
            put("kid", replacementMap["kid"] ?: JsonPrimitive("wallet-key"))
            put("use", replacementMap["use"] ?: JsonPrimitive("enc"))
            put("crv", replacementMap["crv"] ?: JsonPrimitive("P-256"))
            put("alg", replacementMap["alg"] ?: JsonPrimitive("ECDH-ES"))
            put("x", replacementMap["x"] ?: JsonPrimitive("f83OJ3D2xF4Qz7ptV5WZs3CrbKjK9bZ2WXsuFwGv_MQ"))
            put("y", replacementMap["y"] ?: JsonPrimitive("x_FEzRu9sU2JV0tUvr1DkzQP8tTKUydDR5L1l8S9dN0"))
            replacementMap["d"]?.let { put("d", it) }
        }
    }
}
