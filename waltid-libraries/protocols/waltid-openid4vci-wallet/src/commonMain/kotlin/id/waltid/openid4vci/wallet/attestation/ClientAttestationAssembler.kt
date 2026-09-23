package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.publicOnly
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto.keys.Key as LegacyKey

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
    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildAttestationHeaders(
        instanceKey: LegacyKey,
        clientId: String,
        audience: String,
    ): ClientAttestationHeaders = buildAttestationHeaders(instanceKey, clientId, audience, null)

    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildAttestationHeaders(
        instanceKey: LegacyKey,
        clientId: String,
        audience: String,
        challenge: String?,
    ): ClientAttestationHeaders {
        val attestationJwt = attestationProvider.getAttestationJwt(instanceKey.getPublicKey(), clientId)
        val popJwt = popBuilder.buildPopJwt(instanceKey, clientId, audience, challenge)
        return ClientAttestationHeaders(attestationJwt, popJwt)
    }

    suspend fun buildAttestationHeaders(
        instanceKey: Key,
        clientId: String,
        audience: String,
    ): ClientAttestationHeaders = buildAttestationHeaders(instanceKey, clientId, audience, null)

    suspend fun buildAttestationHeaders(
        instanceKey: Key,
        clientId: String,
        audience: String,
        challenge: String?,
    ): ClientAttestationHeaders {
        val attestationJwt = obtainAttestationJwt(instanceKey, clientId)
        val popJwt = buildPopJwt(instanceKey, clientId, audience, challenge)
        return ClientAttestationHeaders(attestationJwt, popJwt)
    }

    /** Obtains the reusable Wallet Attestation JWT for one protocol exchange. */
    suspend fun obtainAttestationJwt(
        instanceKey: Key,
        clientId: String,
    ): String {
        val exported = requireNotNull(instanceKey.capabilities.publicKeyExporter) {
            "Wallet attestation instance key does not export its public key"
        }.exportPublicKey()
        val publicJwk = exported.toPublicJwk(instanceKey.spec).publicOnly()
        return attestationProvider.getAttestationJwt(publicJwk, clientId)
    }

    /** Builds a fresh proof for the specific HTTP request being sent. */
    suspend fun buildPopJwt(
        instanceKey: Key,
        clientId: String,
        audience: String,
        challenge: String?,
    ): String = popBuilder.buildPopJwt(instanceKey, clientId, audience, challenge)
}
