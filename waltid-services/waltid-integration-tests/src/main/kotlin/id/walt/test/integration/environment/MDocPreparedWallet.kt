@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.test.integration.environment.api.wallet.WalletApi
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

object MDocPreparedWallet {


    suspend fun ensureWalletIsPreparedForMdoc(walletApi: WalletApi) {
        val key = walletApi.listKeys().firstOrNull { key ->
            key.algorithm == "secp256r1"
        }
        if (key == null) {
            // could not find key of type TODO ... recreate wallet
            resetWallet(walletApi)
            val keys = walletApi.listKeys()
            assertNotNull(keys)
        }
    }

    private suspend fun resetWallet(walletApi: WalletApi) {
        clearAllKeys(walletApi)
        clearAllDids(walletApi)
        val keyId = generateSecp256r1Key(walletApi)
        generateDidBackedBySecp256r1Key(walletApi, keyId)
    }

    private suspend fun clearAllKeys(walletApi: WalletApi) {
        walletApi.listKeys().forEach { key ->
            walletApi.deleteKey(key.keyId.id)
        }
    }

    private suspend fun clearAllDids(walletApi: WalletApi) {
        walletApi.listDids().forEach { did ->
            walletApi.deleteDid(did.did)
        }
    }

    private suspend fun generateSecp256r1Key(walletApi: WalletApi): String {
        return walletApi.generateKey(
            KeyGenerationRequest(keyType = KeyType.secp256r1)
        )
    }

    private suspend fun generateDidBackedBySecp256r1Key(walletApi: WalletApi, keyId: String) {
        val did = walletApi.createDid(
            keyId = keyId,
            method = "jwk"
        )
        walletApi.setDefaultDid(did)
    }
}
