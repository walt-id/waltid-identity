package id.walt.oid4vc.requests

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.HTTPDataObjectFactory

data class CredentialOfferRequest(
    val credentialOffer: CredentialOffer? = null,
    val credentialOfferUri: String? = null,
    override val customParameters: Map<String, List<String>> = mapOf()
) : HTTPDataObject() {
    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            credentialOffer?.let { put("credential_offer", listOf(it.toJSONString())) }
            credentialOfferUri?.let { put("credential_offer_uri", listOf(it)) }
        }
    }

    companion object : HTTPDataObjectFactory<CredentialOfferRequest>() {
        private val knownKeys = setOf("credential_offer", "credential_offer_uri")
        override fun fromHttpParameters(parameters: Map<String, List<String>>): CredentialOfferRequest {
            return CredentialOfferRequest(
                parameters["credential_offer"]?.firstOrNull()?.let { CredentialOffer.fromJSONString(it) },
                parameters["credential_offer_uri"]?.firstOrNull(),
                parameters.filterKeys { !knownKeys.contains(it) }
            )
        }
    }
}
