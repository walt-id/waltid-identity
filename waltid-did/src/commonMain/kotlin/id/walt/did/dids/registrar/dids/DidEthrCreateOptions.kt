package id.walt.did.dids.registrar.dids

class DidEthrCreateOptions(network: String = "goerli") : DidCreateOptions(
    method = "ethr",
    options = options("network" to network)
)

