package id.walt.webwallet.service.trust

interface TrustValidationService {
    //TODO: maybe return a custom type
    fun validate(did: String): Boolean?
}