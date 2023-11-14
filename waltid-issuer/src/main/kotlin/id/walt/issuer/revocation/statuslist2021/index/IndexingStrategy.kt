package id.walt.issuer.revocation.statuslist2021.index

import id.walt.servicematrix.ServiceProvider
import id.walt.servicematrix.ServiceRegistry
import id.walt.services.WaltIdService

abstract class IndexingStrategy : WaltIdService() {
    override val implementation: IndexingStrategy get() = serviceImplementation()
    open fun next(bitset: Array<String>): String = implementation.next(bitset)

    companion object : ServiceProvider {
        override fun getService() = ServiceRegistry.getService(IndexingStrategy::class)
        override fun defaultImplementation() = IncrementalIndexingStrategy()

    }
}
