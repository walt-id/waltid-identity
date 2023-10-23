package id.walt.did.dids.registrar.dids

class DidCheqdCreateOptions(network: String) : DidCreateOptions(
    method = "cheqd",
    options = mapOf("network" to network)
)
