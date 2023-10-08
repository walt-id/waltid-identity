package id.walt.ssikit.did.registrar.dids

class DidSovCreateOptions(network: String) : DidCreateOptions(
    method = "sov",
    options = options("network" to network)
)
