package registrars

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.document.models.service.RegisteredServiceType
import id.walt.did.dids.document.models.service.ServiceEndpointURL
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.dids.registrar.dids.DidDocConfig
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.dids.ServiceConfiguration
import id.walt.did.dids.registrar.dids.VerificationMethodConfiguration
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class DidWebDocConfigTest {

    private val webRegistrar = DidWebRegistrar()
    private val domain = "localhost"
    private val path = "/some-path"

    @Test
    fun testEmptyDidDocConfig() = runTest {
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig(),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
            )
            assertEquals(
                expected = expectedKeys.size,
                actual = document.size,
            )
            expectedKeys.forEach {
                assertContains(
                    map = document,
                    key = it,
                )
            }
        }
    }

    @Test
    fun testRootDocCustomProperties() = runTest {
        val rootDocCustomProperties = mapOf(
            "this" to "that".toJsonElement(),
            "tit" to "tat".toJsonElement(),
        )
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig(
                    rootCustomProperties = rootDocCustomProperties,
                ),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
            ) + rootDocCustomProperties.keys
            assertEquals(
                expected = expectedKeys.size,
                actual = document.size,
            )
            expectedKeys.forEach {
                assertContains(
                    map = document,
                    key = it,
                )
            }
        }
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
                                    publicKeyId = randomUUIDString(),
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
                            }.toSet() + VerificationMethodConfiguration(randomUUIDString()),
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

    @Test
    fun testBuildFromPublicKeySet() = runTest {
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig.buildFromPublicKeySet(),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
            )
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
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig.buildFromPublicKeySet(
                    publicKeySet = setOf(publicKey),
                ),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "verificationMethod",
            ) + VerificationRelationshipType.entries.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == 1
            )
        }
        val publicKeySet = setOf(
            publicKey,
            JWKKey.generate(KeyType.secp256k1).getPublicKey(),
            JWKKey.generate(KeyType.RSA).getPublicKey(),
        )
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig.buildFromPublicKeySet(
                    publicKeySet = publicKeySet,
                ),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "verificationMethod"
            ) + VerificationRelationshipType.entries.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == publicKeySet.size
            )
        }
    }

    @Test
    fun testBuildFromPublicKeySetVerificationConfiguration() = runTest {
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig.buildFromPublicKeySetVerificationConfiguration(),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf("context", "id")
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
        val verRelTypeList = listOf(
            VerificationRelationshipType.Authentication,
            VerificationRelationshipType.AssertionMethod,
            VerificationRelationshipType.KeyAgreement,
        )
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        //single key, one verification relationship at a time
        VerificationRelationshipType.entries.forEach { verRelType ->
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig
                        .buildFromPublicKeySetVerificationConfiguration(
                            verificationKeySetConfiguration = mapOf(
                                verRelType to setOf(publicKey)
                            )
                        )
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
                assertTrue(
                    document["verificationMethod"] is JsonArray &&
                            (document["verificationMethod"] as JsonArray).size == 1
                )
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == 1
                )
            }
        }
        //single key, three verification relationships at a time
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySetVerificationConfiguration(
                        verificationKeySetConfiguration = verRelTypeList
                            .associateWith {
                                setOf(publicKey)
                            },
                    )
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf("context", "id", "verificationMethod") + verRelTypeList.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == 1
            )
            verRelTypeList.forEach { verRelType ->
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == 1
                )
            }

        }
        //single key, all verification relationships
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySetVerificationConfiguration(
                        verificationKeySetConfiguration = VerificationRelationshipType
                            .entries
                            .associateWith {
                                setOf(publicKey)
                            },
                    )
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "verificationMethod"
            ) + VerificationRelationshipType.entries.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == 1
            )
            verRelTypeList.forEach { verRelType ->
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == 1
                )
            }

        }
        val publicKeySet = setOf(
            publicKey,
            JWKKey.generate(KeyType.secp256k1).getPublicKey(),
            JWKKey.generate(KeyType.RSA).getPublicKey(),
        )
        //multiple keys, one verification relationship at a time
        VerificationRelationshipType.entries.forEach { verRelType ->
            webRegistrar.register(
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    didDocConfig = DidDocConfig
                        .buildFromPublicKeySetVerificationConfiguration(
                            verificationKeySetConfiguration = mapOf(
                                verRelType to publicKeySet
                            )
                        )
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
                assertTrue(
                    document["verificationMethod"] is JsonArray &&
                            (document["verificationMethod"] as JsonArray).size == publicKeySet.size
                )
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == publicKeySet.size
                )
            }
        }
        //multiple keys, three verification relationships at a time
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySetVerificationConfiguration(
                        verificationKeySetConfiguration = verRelTypeList
                            .associateWith {
                                publicKeySet
                            },
                    )
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf("context", "id", "verificationMethod") + verRelTypeList.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == publicKeySet.size
            )
            verRelTypeList.forEach { verRelType ->
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == publicKeySet.size
                )
            }

        }
        //multiple keys, all verification relationships
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySetVerificationConfiguration(
                        verificationKeySetConfiguration = VerificationRelationshipType
                            .entries
                            .associateWith {
                                publicKeySet
                            },
                    )
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "verificationMethod"
            ) + VerificationRelationshipType.entries.map { it.toString() }
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
            assertTrue(
                document["verificationMethod"] is JsonArray &&
                        (document["verificationMethod"] as JsonArray).size == publicKeySet.size
            )
            verRelTypeList.forEach { verRelType ->
                assertTrue(
                    document[verRelType.toString()] is JsonArray &&
                            (document[verRelType.toString()] as JsonArray).size == publicKeySet.size
                )
            }
        }
    }

    @Test
    fun testServiceConfiguration() = runTest {
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySet(
                        serviceConfigurationSet = setOf(
                            ServiceConfiguration(
                                type = RegisteredServiceType.DIDCommMessaging.toString(),
                                serviceEndpoint = setOf(
                                    ServiceEndpointURL(
                                        url = "http://some-url",
                                    )
                                )
                            ),
                        ),
                    ),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "service",
            )
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
            println(document.toString())
            assertTrue(
                document["service"] is JsonArray &&
                        (document["service"] as JsonArray).size == 1
            )
        }
        val customProperties = mapOf(
            "this" to "that".toJsonElement()
        )
        //with custom properties
        webRegistrar.register(
            DidWebCreateOptions(
                domain = domain,
                path = path,
                didDocConfig = DidDocConfig
                    .buildFromPublicKeySet(
                        serviceConfigurationSet = setOf(
                            ServiceConfiguration(
                                type = RegisteredServiceType.DIDCommMessaging.toString(),
                                serviceEndpoint = setOf(
                                    ServiceEndpointURL(
                                        url = "http://some-other-url",
                                    )
                                ),
                                customProperties = customProperties,
                            ),
                        ),
                    ),
            )
        ).didDocument.let { document ->
            val expectedKeys = listOf(
                "context",
                "id",
                "service",
            )
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
            println(document.toString())
            assertTrue(
                document["service"] is JsonArray &&
                        (document["service"] as JsonArray).size == 1
            )
            customProperties.forEach { (key, value) ->
                assertTrue(
                    ((document["service"] as JsonArray)[0] as JsonObject).containsKey(key) &&
                            ((document["service"] as JsonArray)[0] as JsonObject)[key] == value
                )
            }
        }
    }
}