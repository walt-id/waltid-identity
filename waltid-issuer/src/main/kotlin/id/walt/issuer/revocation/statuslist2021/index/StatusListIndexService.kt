package id.walt.issuer.revocation.statuslist2021.index

import id.walt.servicematrix.ServiceProvider
import id.walt.servicematrix.ServiceRegistry
import id.walt.services.WaltIdService

abstract class StatusListIndexService : WaltIdService() {
    override val implementation: StatusListIndexService get() = serviceImplementation()
    abstract fun index(url: String): String

    companion object : ServiceProvider {
        override fun getService() = ServiceRegistry.getService(StatusListIndexService::class)
        override fun defaultImplementation() = WaltIdStatusListIndexService()

    }
}
