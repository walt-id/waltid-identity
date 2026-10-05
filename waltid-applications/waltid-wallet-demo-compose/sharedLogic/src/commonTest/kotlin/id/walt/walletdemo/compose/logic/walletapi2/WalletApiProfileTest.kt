package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
}
