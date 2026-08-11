package id.walt.issuer2.application.openid4vci

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.config.Issuer2ServiceConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class Issuer2TokenStoredKeyTest {
    @Test
    fun `explicit token StoredKey preserves EdDSA and survives restart`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        val config = Issuer2ServiceConfig(
            baseUrl = "https://issuer.example",
            ciTokenKey = KeySerialization.serializeKey(legacyKey),
            ciTokenStoredKey = StoredKeyCodec.encodeToString(stored),
        )
        val signingKey = assertNotNull(OpenId4VciModule.resolveCrypto2TokenKey(config))
        val token = CompactJws.sign("{}".encodeToByteArray(), signingKey.key, signingKey.algorithm)

        assertEquals(JwsAlgorithm.EDDSA, signingKey.algorithm)
        assertEquals(JwsAlgorithm.EDDSA, CompactJws.decodeUnverified(token).algorithm)
        assertEquals(signingKey.keyId, OpenId4VciModule.resolveCrypto2TokenKey(config)?.keyId)
    }

    @Test
    fun `explicit token StoredKey mismatch fails without legacy downgrade`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val mismatched = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(replacement).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )

        assertFailsWith<IllegalArgumentException> {
            OpenId4VciModule.resolveCrypto2TokenKey(
                Issuer2ServiceConfig(
                    baseUrl = "https://issuer.example",
                    ciTokenKey = KeySerialization.serializeKey(legacyKey),
                    ciTokenStoredKey = StoredKeyCodec.encodeToString(mismatched),
                )
            )
        }
    }

    @Test
    fun `explicit signing-only token StoredKey fails startup validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val signingOnly = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN),
        )

        assertFailsWith<IllegalArgumentException> {
            OpenId4VciModule.resolveCrypto2TokenKey(
                Issuer2ServiceConfig(
                    baseUrl = "https://issuer.example",
                    ciTokenKey = KeySerialization.serializeKey(legacyKey),
                    ciTokenStoredKey = StoredKeyCodec.encodeToString(signingOnly),
                )
            )
        }
    }

    @Test
    fun `explicit overgranted token StoredKey fails startup validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val overgranted = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT),
        )

        assertFailsWith<IllegalArgumentException> {
            OpenId4VciModule.resolveCrypto2TokenKey(
                Issuer2ServiceConfig(
                    baseUrl = "https://issuer.example",
                    ciTokenKey = KeySerialization.serializeKey(legacyKey),
                    ciTokenStoredKey = StoredKeyCodec.encodeToString(overgranted),
                )
            )
        }
    }
}
