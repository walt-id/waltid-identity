package id.waltid.openid4vp.wallet.presentation

import kotlinx.serialization.json.JsonObject

object LDPPresenter {

    fun presentLdpTodo(): JsonObject {
        TODO("Data Integrity Proof signed credentials are not supported yet")
        // TODO: LinkedDataProof
        // Construct a W3C VP LDP (JSON-LD with Data Integrity proof)
        // This is more complex as it involves JSON-LD processing and DI proofs.
        // The 'originalCredential.credentialData' would be the LDP VC.
        // The proof needs 'challenge' (nonce) and 'domain' (client_id).
        /*val ldpPresentationObject = buildLdpPresentation(
            originalCredential.credentialData,
            holderDid,
            authorizationRequest.nonce!!,
            authorizationRequest.clientId,
            holderKey
        )
        ldpPresentationObject*/ // This would be a JsonObject
    }

}
