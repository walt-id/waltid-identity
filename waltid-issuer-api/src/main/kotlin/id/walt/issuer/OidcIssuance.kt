package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.JsonLDCredentialDefinition
import id.walt.oid4vc.data.OfferedCredential
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


    fun issuanceRequestsToCredentialOfferBuilder(issuanceRequests: List<IssuanceRequest>) =
        issuanceRequestsToCredentialOfferBuilder(*issuanceRequests.toTypedArray())

    fun issuanceRequestsToCredentialOfferBuilder(vararg issuanceRequests: IssuanceRequest): CredentialOffer.Builder {
        var builder = CredentialOffer.Builder(OidcApi.baseUrl)
        issuanceRequests.forEach { issuanceRequest ->
            builder.addOfferedCredential(issuanceRequest.credentialConfigurationId)
        }

        return builder
    }

}
