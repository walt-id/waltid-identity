package id.walt.webwallet.service.exchange

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.oid4vc.data.CredentialOffer
import kotlin.time.Duration.Companion.minutes

object CredentialOfferCache {
    private val persistence = ConfiguredPersistence<CredentialOffer>(
        discriminator = "credential-offer",
        defaultExpiration = 15.minutes,
        encoding = { it.toJSONString() },
        decoding = { CredentialOffer.fromJSONString(it) }
    )

    fun put(key: String, offer: CredentialOffer) {
        persistence[key] = offer
    }

    fun get(key: String): CredentialOffer? = persistence[key]

    fun remove(key: String) {
        persistence.remove(key)
    }
}
