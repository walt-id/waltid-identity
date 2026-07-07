package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key

interface WalletAttestationProvider {
    suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String
}
