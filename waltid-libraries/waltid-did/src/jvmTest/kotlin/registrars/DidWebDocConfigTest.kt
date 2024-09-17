package registrars

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.dids.registrar.dids.DidDocConfig
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.dids.VerificationMethodConfiguration
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.utils.randomUUID
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DidWebDocConfigTest {

    private val webRegistrar = DidWebRegistrar()
    private val domain = "localhost"
    private val path = "/some-path"

    @Test
    fun testEmptyDidDocConfig() = runTest {
        val document = webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig(),
            )
        ).didDocument
        assertTrue(document.size == 2)
        assertTrue(document.containsKey("context"))
        assertTrue(document.containsKey("id"))
    }

    @Test
    fun testKeyMapWithPrivateKeyFails() = runTest {
        val privKey = JWKKey.generate(KeyType.secp256r1)
        assertFails {
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = mapOf(privKey.getKeyId() to privKey),
                        verificationConfigurationMap = mapOf(
                            VerificationRelationshipType.Authentication to setOf(
                                VerificationMethodConfiguration(
                                    publicKeyId = privKey.getKeyId(),
                                )
                            ),
                        ),
                    ),
                )
            )
        }
        val keyList = listOf(
            JWKKey.generate(KeyType.RSA).getPublicKey(),
            privKey,
            JWKKey.generate(KeyType.secp256k1).getPublicKey(),
        )
        assertFails {
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = keyList.associateBy { it.getKeyId() },
                        verificationConfigurationMap = mapOf(
                            VerificationRelationshipType.Authentication to keyList.map {
                                VerificationMethodConfiguration(
                                    publicKeyId = it.getKeyId(),
                                )
                            }.toSet(),
                        ),
                    ),
                )
            )
        }
    }

    @Test
    fun testInvalidKeyIdReferenceFails() = runTest {
        val privKey = JWKKey.generate(KeyType.secp256r1)
        assertFails {
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = mapOf(privKey.getKeyId() to privKey),
                        verificationConfigurationMap = mapOf(
                            VerificationRelationshipType.Authentication to setOf(
                                VerificationMethodConfiguration(
                                    publicKeyId = randomUUID(),
                                )
                            ),
                        ),
                    ),
                )
            )
        }
        val keyList = listOf(
            JWKKey.generate(KeyType.RSA).getPublicKey(),
            privKey,
            JWKKey.generate(KeyType.secp256k1).getPublicKey(),
        )
        assertFails {
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = keyList.associateBy { it.getKeyId() },
                        verificationConfigurationMap = mapOf(
                            VerificationRelationshipType.Authentication to keyList.map {
                                VerificationMethodConfiguration(
                                    publicKeyId = it.getKeyId(),
                                )
                            }.toSet() + VerificationMethodConfiguration(randomUUID()),
                        ),
                    ),
                )
            )
        }
    }

    @Test
    fun testEd25519KeyAllVerificationRelationships() = runTest {
        val pubKey = JWKKey.generate(KeyType.Ed25519).getPublicKey()
        //test without keyAgreement first, implicit success
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig(
                    publicKeyMap = mapOf(pubKey.getKeyId() to pubKey),
                    verificationConfigurationMap = VerificationRelationshipType
                        .entries
                        .filterNot { it == VerificationRelationshipType.KeyAgreement }
                        .associateWith {
                            setOf(
                                VerificationMethodConfiguration(
                                    publicKeyId = pubKey.getKeyId(),
                                )
                            )
                        },
                ),
            )
        ).let { result ->
            val expectedKeys = listOf(
                "context",
                "id",
                "verificationMethod",
                "authentication",
                "assertionMethod",
                "capabilityDelegation",
                "capabilityInvocation"
            )
            assertEquals(
                expected = expectedKeys.size,
                actual = result.didDocument.size,
            )
            expectedKeys.forEach {
                assertContains(
                    map = result.didDocument,
                    key = it,
                )
            }
        }
        //test with keyAgreement, must fail
        assertFails {
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = mapOf(pubKey.getKeyId() to pubKey),
                        verificationConfigurationMap = VerificationRelationshipType
                            .entries
                            .associateWith {
                                setOf(
                                    VerificationMethodConfiguration(
                                        publicKeyId = pubKey.getKeyId(),
                                    )
                                )
                            },
                    ),
                )
            )
        }
    }

    @Test
    fun testNonEd25519KeyAllVerificationRelationships() = runTest {
        KeyType
            .entries
            .filterNot { it == KeyType.Ed25519 }
            .forEach { keyType ->
                val pubKey = JWKKey.generate(keyType).getPublicKey()
                webRegistrar.register(
                    DidWebCreateOptions(
                        domain = domain,
                        path = path,
                        didDocConfig = DidDocConfig(
                            publicKeyMap = mapOf(pubKey.getKeyId() to pubKey),
                            verificationConfigurationMap = VerificationRelationshipType
                                .entries
                                .associateWith {
                                    setOf(
                                        VerificationMethodConfiguration(
                                            publicKeyId = pubKey.getKeyId(),
                                        )
                                    )
                                },
                        ),
                    )
                )
            }
    }

    @Test
    fun testOneVerificationRelationshipType() = runTest {
        val pubKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        VerificationRelationshipType.entries.forEach { verRelType ->
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig(
                        publicKeyMap = mapOf(pubKey.getKeyId() to pubKey),
                        verificationConfigurationMap = mapOf(
                            verRelType to setOf(
                                VerificationMethodConfiguration(
                                    publicKeyId = pubKey.getKeyId(),
                                )
                            ),
                        ),
                    ),
                )
            ).didDocument.let { document ->
                val expectedKeys = listOf("context", "id", "verificationMethod", verRelType.toString())
                assertEquals(
                    expected = expectedKeys.size,
                    actual = document.keys.size,
                )
                expectedKeys.forEach {
                    assertContains(
                        map = document,
                        key = it,
                    )
                }
            }
        }
    }

    @Test
    fun testRootDocCustomPropertyInvalidOverrideAttempt() = runTest {
        val rootDocReservedKeys = listOf(
            "context",
            "id",
            "verificationMethod",
            "service",
        ) + VerificationRelationshipType.entries.map { it.toString() }
        rootDocReservedKeys.forEach { reservedKey ->
            assertFails {
                webRegistrar.register(
                    DidWebCreateOptions(
                        domain = domain,
                        path = path,
                        didDocConfig = DidDocConfig(
                            rootCustomProperties = mapOf(reservedKey to listOf("something").toJsonElement())
                        )
                    )
                )
            }
        }
    }
}