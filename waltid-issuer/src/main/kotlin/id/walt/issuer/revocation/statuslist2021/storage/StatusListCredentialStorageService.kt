package id.walt.issuer.revocation.statuslist2021.storage

import id.walt.credentials.w3c.VerifiableCredential
import id.walt.servicematrix.ServiceProvider
import id.walt.services.WaltIdService

open class StatusListCredentialStorageService : WaltIdService() {
    override val implementation get() = serviceImplementation<StatusListCredentialStorageService>()

    open fun fetch(url: String): VerifiableCredential? = implementation.fetch(url)
    open fun store(credential: VerifiableCredential, url: String): Unit = implementation.store(credential, url)

    companion object : ServiceProvider {
        override fun getService() = object : StatusListCredentialStorageService() {}
        override fun defaultImplementation() = WaltIdStatusListCredentialStorageService()
    }
}
