package id.walt.issuer.issuance

import id.walt.w3c.vc.vcs.W3CVC
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.CredentialOffer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object OidcIssuance {

    private fun W3CVC.getJsonStringArray(key: String): List<String> {
        val keyElement = this[key] ?: throw IllegalArgumentException("Missing key in JSON: $key")
        return if (keyElement is JsonArray && keyElement.jsonArray.all { it is JsonPrimitive && it.isString }) {
            keyElement.jsonArray.map { it.jsonPrimitive.content }
        } else if (keyElement is JsonPrimitive && keyElement.isString) {
            listOf(keyElement.content)
        } else throw IllegalArgumentException("Key in JSON is not a string or an array of strings: $key")
    }


    fun issuanceRequestsToCredentialOfferBuilder(issuanceRequests: List<IssuanceRequest>, standardVersion: OpenID4VCIVersion) =
        issuanceRequestsToCredentialOfferBuilder(
            issuanceRequests = *issuanceRequests.toTypedArray(),
            standardVersion = standardVersion
        )

    fun issuanceRequestsToCredentialOfferBuilder(vararg issuanceRequests: IssuanceRequest, standardVersion: OpenID4VCIVersion): CredentialOffer.Builder<*> {
        val builder = when (standardVersion) {
            OpenID4VCIVersion.DRAFT13 -> CredentialOffer.Draft13.Builder(OidcApi.baseUrl)
            OpenID4VCIVersion.DRAFT11 -> CredentialOffer.Draft11.Builder(OidcApi.baseUrlDraft11)
        }

        issuanceRequests.forEach { issuanceRequest ->
            builder.addOfferedCredential(issuanceRequest.credentialConfigurationId)
        }

        return builder
    }

}
