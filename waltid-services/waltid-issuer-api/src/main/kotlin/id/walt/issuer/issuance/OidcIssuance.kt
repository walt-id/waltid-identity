package id.walt.issuer.issuance

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.OidcApi.metadataDraft11
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.CredentialOffer
import kotlinx.serialization.json.buildJsonObject

object OidcIssuance {

    fun issuanceRequestsToCredentialOfferBuilder(
        issuanceRequests: List<IssuanceRequest>,
        standardVersion: OpenID4VCIVersion,
    ) = issuanceRequestsToCredentialOfferBuilder(
        issuanceRequests = issuanceRequests.toTypedArray(),
        standardVersion = standardVersion,
    )

    private fun issuanceRequestsToCredentialOfferBuilder(
        vararg issuanceRequests: IssuanceRequest,
        standardVersion: OpenID4VCIVersion,
    ): CredentialOffer.Builder<*> {

        val builder = when (standardVersion) {
            OpenID4VCIVersion.DRAFT13 -> {
                CredentialOffer.Draft13.Builder(OidcApi.baseUrl)
            }

            OpenID4VCIVersion.DRAFT11 -> {
                CredentialOffer.Draft11.Builder(OidcApi.baseUrlDraft11)
            }

            OpenID4VCIVersion.V1 -> {
                throw IllegalArgumentException("Unsupported standard version: V1")
            }
        }

        if (standardVersion == OpenID4VCIVersion.DRAFT11 && issuanceRequests.first().draft11EncodeOfferedCredentialsByReference == false) {
            issuanceRequests.forEach { issuanceRequest ->
                builder.addOfferedCredentialByValue(buildJsonObject {
                    put(
                        "format",
                        metadataDraft11.credentialSupported!![issuanceRequest.credentialConfigurationId]!!.format.toJsonElement()
                    )
                    put(
                        "types",
                        metadataDraft11.credentialSupported!![issuanceRequest.credentialConfigurationId]!!.types!!.toJsonElement()
                    )
                })
            }
        } else {
            issuanceRequests.forEach { issuanceRequest ->
                builder.addOfferedCredentialByReference(issuanceRequest.credentialConfigurationId)
            }
        }

        return builder
    }

}
