package id.walt.wallet2.server

import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletInfoResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalSerializationApi::class)
class WalletHttpContractTest {
    @Test
    fun `wallet HTTP and OpenAPI models exclude persistence sidecars`() {
        val responseFields = setOf(
            "walletId",
            "keyStoreCount",
            "credentialStoreCount",
            "hasDidStore",
            "hasStaticKey",
            "hasStaticDid",
            "keyStoreIds",
            "credentialStoreIds",
            "didStoreId",
            "defaultKeyId",
            "defaultDidId",
        )
        assertEquals(responseFields, WalletInfoResponse.serializer().descriptor.elementNames())
        assertEquals(
            responseFields,
            Json.encodeToJsonElement(
                WalletInfoResponse(
                    walletId = "wallet",
                    keyStoreCount = 1,
                    credentialStoreCount = 1,
                    hasDidStore = true,
                    hasStaticKey = true,
                    hasStaticDid = true,
                    keyStoreIds = listOf("keys"),
                    credentialStoreIds = listOf("credentials"),
                    didStoreId = "dids",
                    defaultKeyId = "key",
                    defaultDidId = "did:key:test",
                )
            ).jsonObject.keys,
        )
        val requestFields = CreateWalletRequest.serializer().descriptor.elementNames()
        assertFalse("crypto2StaticKey" in requestFields)
        assertFalse("staticCrypto2Key" in requestFields)
        assertFalse("serializedStaticKey" in requestFields)
    }

    private fun SerialDescriptor.elementNames(): Set<String> =
        (0 until elementsCount).mapTo(mutableSetOf(), ::getElementName)
}
