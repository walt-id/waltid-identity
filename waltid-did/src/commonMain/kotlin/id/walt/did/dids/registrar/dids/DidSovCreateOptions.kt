package id.walt.did.dids.registrar.dids

class DidSovCreateOptions(network: String) : DidCreateOptions(
    method = "sov",
    options = options("network" to network)
)
