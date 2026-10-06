package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletApiProfileTest {
    @Test
    fun openSourcePathsKeepWalletIdSegment() {
        assertEquals(
            "/wallet/wallet-1/credentials/receive",
            walletApiOperationPath(WalletApiKind.OpenSource, "wallet-1", "credentials/receive"),
        )
        assertEquals("/wallet/wallet-1", walletApiOperationPath(WalletApiKind.OpenSource, "wallet-1"))
    }

    @Test
    fun enterprisePathsUseWalletServiceTarget() {
        assertEquals(
            "/v2/waltid.tenant1.wallet1/wallet-service-api/credentials/present/preview",
            walletApiOperationPath(
                WalletApiKind.Enterprise,
                "waltid.tenant1.wallet1",
                "/credentials/present/preview",
            ),
        )
    }

    @Test
    fun enterpriseRequestsRenameKeyIdToKeyReference() {
        val body = buildJsonObject {
            put("requestUrl", "openid4vp://request")
            put("keyId", "waltid.tenant1.kms.key1")
            put("did", "did:jwk:example")
        }
        val adapted = adaptWalletRequestBody(WalletApiKind.Enterprise, body)
        assertEquals(JsonPrimitive("waltid.tenant1.kms.key1"), adapted["keyReference"])
        assertFalse(adapted.containsKey("keyId"))
        assertEquals(body, adaptWalletRequestBody(WalletApiKind.OpenSource, body))
    }

    @Test
    fun enterpriseRequestsRenameNestedHolderKeyIds() {
        val body = buildJsonObject {
            put("credentials", buildJsonArray {
                add(buildJsonObject {
                    put("holderBindings", buildJsonArray {
                        add(buildJsonObject {
                            put("keyId", "waltid.tenant1.kms.key1")
                            put("did", "did:jwk:example")
                        })
                    })
                })
            })
        }
        val binding = adaptWalletRequestBody(WalletApiKind.Enterprise, body)
            .getValue("credentials").jsonArray.single().jsonObject
            .getValue("holderBindings").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive("waltid.tenant1.kms.key1"), binding["keyReference"])
        assertFalse(binding.containsKey("keyId"))
    }

    @Test
    fun resourceTreeWalkFindsWallet2NodesInOrder() {
        val tree = buildJsonObject {
            put("waltid", buildJsonObject {
                put("type", "tree")
                put("entries", buildJsonObject {
                    put("waltid.tenant1", buildJsonObject {
                        put("type", "tenant")
                        put("entries", buildJsonObject {
                            put("waltid.tenant1.issuer", buildJsonObject { put("type", "issuer") })
                            put("waltid.tenant1.wallet1", buildJsonObject { put("type", "wallet2") })
                            put("waltid.tenant1.wallet2", buildJsonObject { put("type", "wallet2") })
                        })
                    })
                })
            })
        }
        assertEquals(
            listOf("waltid.tenant1.wallet1", "waltid.tenant1.wallet2"),
            wallet2TargetsFromResourceTree(tree),
        )
    }

    @Test
    fun rememberedWalletIsUsedWhenStillAvailable() {
        val available = listOf("waltid.tenant1.wallet1", "waltid.tenant1.wallet2")
        assertEquals("waltid.tenant1.wallet2", selectWalletId(available, "waltid.tenant1.wallet2"))
        assertEquals("waltid.tenant1.wallet1", selectWalletId(available, "missing"))
        assertEquals("waltid.tenant1.wallet1", selectWalletId(available, null))
        assertNull(selectWalletId(emptyList(), null))
    }

    @Test
    fun enterpriseProfileIsLoginOnly() {
        assertTrue(WalletApiKind.OpenSource.canRegister)
        assertTrue(WalletApiKind.OpenSource.canGenerateIdentity)
        assertFalse(WalletApiKind.Enterprise.canRegister)
        assertFalse(WalletApiKind.Enterprise.canManageWallet)
        assertFalse(WalletApiKind.Enterprise.canGenerateIdentity)
        assertFalse(WalletApiKind.Enterprise.canDeleteCredential)
        assertEquals(WalletApiKind.Enterprise, parseWalletApiKind("enterprise"))
        assertEquals(WalletApiKind.OpenSource, parseWalletApiKind("oss"))
        assertEquals(WalletApiKind.OpenSource, parseWalletApiKind(null))
    }

    @Test
    fun authorizationCallbackStaysWithTheWalletThatStartedIt() {
        val available = listOf("wallet-a", "wallet-b")
        assertEquals("wallet-a", walletForAuthorizationCallback("wallet-b", available, "wallet-a"))
        assertEquals("wallet-b", walletForAuthorizationCallback("wallet-b", available, "wallet-b"))
        assertEquals("wallet-b", walletForAuthorizationCallback("wallet-b", available, null))
        assertNull(walletForAuthorizationCallback("wallet-b", available, "wallet-c"))
    }

    @Test
    fun holderPairsMatchTheDidThatPublishesTheKey() {
        val first = publicJwk("x1")
        val second = publicJwk("x2")
        val pairs = verifiedHolderPairs(
            keys = listOf(
                WalletKeyInfo(keyId = "key-1", publicJwk = first),
                WalletKeyInfo(keyId = "key-2", publicJwk = second),
            ),
            dids = listOf(WalletDidEntry(did = "did:jwk:second", document = didDocument(second))),
        )
        assertEquals("key-2", pairs.single().keyId)
        assertEquals("did:jwk:second", pairs.single().did)
        assertTrue(pairs.single().publicJwk.contains("x2"))
    }

    @Test
    fun holderPairsIgnoreKeysWithoutPublicMaterial() {
        val pairs = verifiedHolderPairs(
            keys = listOf(WalletKeyInfo(keyId = "key-1"), WalletKeyInfo(keyId = "key-2")),
            dids = listOf(WalletDidEntry(did = "did:jwk:one", document = didDocument(publicJwk("x1")))),
        )
        assertTrue(pairs.isEmpty())
    }
}

private fun publicJwk(x: String): JsonObject = buildJsonObject {
    put("kty", "EC")
    put("crv", "P-256")
    put("x", x)
    put("y", "yy")
}

private fun didDocument(jwk: JsonObject): JsonObject = buildJsonObject {
    put("verificationMethod", buildJsonArray {
        add(buildJsonObject { put("publicKeyJwk", jwk) })
    })
}
