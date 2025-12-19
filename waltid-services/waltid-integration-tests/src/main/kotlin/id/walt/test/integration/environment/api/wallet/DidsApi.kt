@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletDid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val didRegexPattern = "^^did:%s:\\S+\$"

class DidsApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun listDidsRaw(walletId: Uuid) =
        client.get("/wallet-api/wallet/$walletId/dids")

    suspend fun listDids(
        walletId: Uuid
    ): List<WalletDid> = listDidsRaw(walletId).let {
        it.expectSuccess()
        it.body<List<WalletDid>>()
    }

    suspend fun setDefaultDidRaw(walletId: Uuid, did: String) =
        client.post("/wallet-api/wallet/$walletId/dids/default?did=$did")

    suspend fun setDefaultDid(walletId: Uuid, didString: String) {
        setDefaultDidRaw(walletId, didString).expectSuccess()
    }

    suspend fun getDidRaw(walletId: Uuid, didString: String) =
        client.get("/wallet-api/wallet/$walletId/dids/$didString")

    suspend fun getDid(walletId: Uuid, didString: String): JsonObject =
        getDidRaw(walletId, didString).let {
            val did = it.body<JsonObject>()
            assertEquals(didString, did["id"]?.jsonPrimitive?.content)
            did
        }

    suspend fun createDidRaw(
        walletId: Uuid,
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ): HttpResponse =
        client.post("/wallet-api/wallet/$walletId/dids/create/${method}") {
            url {
                keyId?.run { parameters.append("keyId", this) }
                alias?.run { parameters.append("alias", this) }
                options.onEach {
                    parameters.append(it.key, it.value.toString())
                }
            }
        }


    suspend fun createDid(
        walletId: Uuid,
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ): String = createDidRaw(walletId, method, keyId, alias, options).let {
        it.expectSuccess()
        val did = it.body<String>()
        assertTrue(String.format(didRegexPattern, method).toRegex().matches(did))
        did
    }

    suspend fun deleteDidRaw(walletId: Uuid, did: String) =
        client.delete("/wallet-api/wallet/$walletId/dids/$did")

    suspend fun deleteDid(walletId: Uuid, did: String) {
        deleteDidRaw(walletId, did).expectSuccess()
    }


    @Deprecated("Old API")
    suspend fun list(
        wallet: Uuid,
        expectedDefault: DefaultDidOption,
        size: Int? = null,
        output: ((List<WalletDid>) -> Unit)? = null,
    ) =
        e2e.test("/wallet-api/wallet/{wallet}/dids - list DIDs") {
            client.get("/wallet-api/wallet/$wallet/dids").expectSuccess().apply {
                val dids = body<List<WalletDid>>()
                assertFalse(dids.isEmpty(), "Wallet has no DIDs!")
                size?.let { assertEquals(dids.size, it, "Wallet has invalid number of DIDs!") }
                expectedDefault.whenNone { assertTrue(dids.none { it.default }) }
                expectedDefault.whenAny { assertNotNull(dids.single { it.default }) }
                expectedDefault.whenSome { did -> assertTrue(dids.single { it.did == did }.default) }
                output?.invoke(dids)
            }
        }

    suspend fun delete(wallet: Uuid, did: String) =
        e2e.test("/wallet-api/wallet/{wallet}/dids/{did} - delete did") {
            client.delete("/wallet-api/wallet/$wallet/dids/$did").expectSuccess()
        }

    suspend fun create(wallet: Uuid, payload: DidCreateRequest, output: ((String) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/dids/create/${payload.method} - create did:${payload.method}") {
            client.post("/wallet-api/wallet/$wallet/dids/create/${payload.method}") {
                url {
                    payload.toMap().onEach {
                        parameters.append(it.key, it.value)
                    }
                }
            }.expectSuccess().apply {
                val did = body<String>()
                assertTrue(String.format(didRegexPattern, payload.method).toRegex().matches(did))
                output?.invoke(did)
            }
        }

    data class DidCreateRequest(
        val method: String,
        val keyId: String? = null,
        val alias: String? = null,
        val options: Map<String, Any> = emptyMap(),
    ) {
        fun toMap() = mutableMapOf<String, String>().apply {
            keyId?.run { put("keyId", this) }
            alias?.run { put("alias", this) }
            putAll(options.mapValues { it.toString() })
        }
    }

    sealed class DefaultDidOption {
        @ConsistentCopyVisibility
        data class Some internal constructor(val value: String) : DefaultDidOption()
        data object None : DefaultDidOption()
        data object Any : DefaultDidOption()

        fun whenSome(action: (String) -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> apply { action(value) }
                is None -> this
                is Any -> this
            }
        }

        fun whenNone(action: () -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> this
                is None -> apply { action() }
                is Any -> this
            }
        }

        fun whenAny(action: () -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> this
                is None -> this
                is Any -> apply { action() }
            }
        }
    }
}
