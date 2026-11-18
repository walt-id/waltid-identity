package id.walt.wallet2.handlers

import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders

/** Validates the manual token header pair. The issuer validates the JWTs themselves. */
internal fun parseManualClientAttestationHeaders(headers: Map<String, String>): ClientAttestationHeaders? {
    val attestations = headers.entries.filter {
        it.key.equals(ClientAttestationHeaders.HEADER_ATTESTATION, ignoreCase = true)
    }
    val proofs = headers.entries.filter {
        it.key.equals(ClientAttestationHeaders.HEADER_ATTESTATION_POP, ignoreCase = true)
    }
    if (attestations.isEmpty() && proofs.isEmpty()) return null

    require(attestations.size <= 1 && proofs.size <= 1) {
        "Client attestation header names must not be duplicated, ignoring case"
    }
    require(attestations.size == 1 && proofs.size == 1) {
        "OAuth-Client-Attestation and OAuth-Client-Attestation-PoP must be supplied together"
    }
    require(attestations.single().value.isNotBlank() && proofs.single().value.isNotBlank()) {
        "OAuth-Client-Attestation and OAuth-Client-Attestation-PoP must not be blank"
    }
    return ClientAttestationHeaders(attestations.single().value, proofs.single().value)
}
