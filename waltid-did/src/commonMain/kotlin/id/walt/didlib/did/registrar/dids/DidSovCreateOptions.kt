package id.walt.didlib.did.registrar.dids

class DidSovCreateOptions(network: String) : DidCreateOptions(
    method = "sov",
    options = options("network" to network)
)
