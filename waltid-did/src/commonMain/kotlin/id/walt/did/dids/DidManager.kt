package id.walt.did.dids

interface DidManager {

    fun resolve(did: String)

    fun create(type: String)
    fun update()
    fun delete(did: String)
}
