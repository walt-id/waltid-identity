package id.walt.oid4vc

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object OpenID4VCI {
    fun getCredentialOfferRequestUrl(
        credOffer: CredentialOffer,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(CredentialOfferRequest(credOffer, customParameters = customReqParams).toHttpParameters()))
        }.buildString()
    }

    fun getCredentialOfferRequestUrl(
        credOfferUrl: String,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(
                parametersOf(
                    CredentialOfferRequest(
                        credentialOfferUri = credOfferUrl,
                        customParameters = customReqParams
                    ).toHttpParameters()
                )
            )
        }.buildString()
    }

    fun parseCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOfferRequest {
        return CredentialOfferRequest.fromHttpParameters(Url(credOfferReqUrl).parameters.toMap())
    }

    suspend fun parseAndResolveCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOffer {
        val offerReq = parseCredentialOfferRequestUrl(credOfferReqUrl)
        return if (offerReq.credentialOffer != null) {
            offerReq.credentialOffer
        } else if (!offerReq.credentialOfferUri.isNullOrEmpty()) {

            http.get(offerReq.credentialOfferUri).bodyAsText().let {
                CredentialOffer.fromJSONString(it)
            }
        } else throw Exception("Credential offer request has no credential offer object set by value or reference.")
    }

    fun getCIProviderMetadataUrl(credOffer: CredentialOffer): String {
        return URLBuilder(credOffer.credentialIssuer).apply {
            appendPathSegments(".well-known", "openid-credential-issuer")
        }.buildString()
    }

    suspend fun resolveCIProviderMetadata(credOffer: CredentialOffer): OpenIDProviderMetadata {
        return http.get(getCIProviderMetadataUrl(credOffer)).bodyAsText().let {
            OpenIDProviderMetadata.fromJSONString(it)
        }
    }

    fun resolveOfferedCredentials(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> {
        val supportedCredentials =
            providerMetadata.credentialsSupported?.filter { !it.id.isNullOrEmpty() }?.associateBy { it.id!! } ?: mapOf()
        return credentialOffer.credentials.mapNotNull { c ->
            if (c is JsonObject) {
                OfferedCredential.fromJSON(c)
            } else if (c is JsonPrimitive && c.isString) {
                supportedCredentials[c.content]?.let {
                    OfferedCredential.fromProviderMetadata(it)
                }
            } else null
        }
    }
}
