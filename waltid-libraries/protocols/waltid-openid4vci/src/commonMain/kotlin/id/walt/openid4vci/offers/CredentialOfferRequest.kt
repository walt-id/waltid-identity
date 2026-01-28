package id.walt.openid4vci.offers

import io.ktor.http.URLBuilder
import io.ktor.http.formUrlEncode
import io.ktor.http.parametersOf
import kotlinx.serialization.json.Json

const val CROSS_DEVICE_CREDENTIAL_OFFER_URL = "openid-credential-offer://"

private val defaultJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

data class CredentialOfferRequest(
    val credentialOffer: CredentialOffer? = null,
    val credentialOfferUri: String? = null,
    val customParameters: Map<String, List<String>> = emptyMap(),
) {
    init {
        require((credentialOffer != null) xor (credentialOfferUri != null)) {
            "Credential offer request must include exactly one of credential_offer or credential_offer_uri"
        }
        require(customParameters.keys.none { it == "credential_offer" || it == "credential_offer_uri" }) {
            "Custom parameters must not override credential_offer or credential_offer_uri"
        }
    }

    fun toHttpParameters(): Map<String, List<String>> = buildMap {
        credentialOffer?.let {
            put("credential_offer", listOf(defaultJson.encodeToString(CredentialOffer.serializer(), it)))
        }
        credentialOfferUri?.let { put("credential_offer_uri", listOf(it)) }
        customParameters.forEach { (key, value) -> put(key, value) }
    }

    /**
     * Builds the shareable offer URL by encoding this request as query parameters.
     *
     * Use the default `openid-credential-offer://` scheme for cross-device offers or provide
     * a HTTPS endpoint when hosting the offer request on a web URL.
     */
    fun toUrl(credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL): String {
        val params = parametersOf(toHttpParameters())

        if (credentialOfferEndpoint == CROSS_DEVICE_CREDENTIAL_OFFER_URL) {
            val query = params.formUrlEncode()
            return if (query.isBlank()) credentialOfferEndpoint else "$credentialOfferEndpoint?$query"
        }

        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(params)
        }.buildString()
    }

}
