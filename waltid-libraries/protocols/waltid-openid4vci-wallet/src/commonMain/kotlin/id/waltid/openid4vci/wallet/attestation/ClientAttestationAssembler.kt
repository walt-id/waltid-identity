package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key

data class ClientAttestationHeaders(
    val attestationJwt: String,
    val popJwt: String,
) {
    companion object {
        const val HEADER_ATTESTATION = "OAuth-Client-Attestation"
        const val HEADER_ATTESTATION_POP = "OAuth-Client-Attestation-PoP"
    }
}

class ClientAttestationAssembler(
    private val attestationProvider: WalletAttestationProvider,
    private val popBuilder: WalletAttestationPopBuilder = WalletAttestationPopBuilder(),
) {
    suspend fun buildAttestationHeaders(
        instanceKey: Key,
        clientId: String,
        audience: String,
    ): ClientAttestationHeaders {
        val attestationJwt = attestationProvider.getAttestationJwt(instanceKey, clientId)
        val popJwt = popBuilder.buildPopJwt(instanceKey, clientId, audience)
        return ClientAttestationHeaders(attestationJwt, popJwt)
    }
}
