package id.walt.webwallet.service.exchange

import id.walt.oid4vc.data.CredentialOffer
import java.util.concurrent.ConcurrentHashMap

object CredentialOfferCache {
    private val cache = ConcurrentHashMap<String, CredentialOffer>()

    fun put(key: String, offer: CredentialOffer) {
        cache[key] = offer
    }

    fun get(key: String): CredentialOffer? = cache[key]

    fun remove(key: String) {
        cache.remove(key)
    }
}
