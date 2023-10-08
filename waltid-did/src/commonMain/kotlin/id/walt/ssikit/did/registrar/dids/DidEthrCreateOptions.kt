package id.walt.ssikit.did.registrar.dids

class DidEthrCreateOptions(network: String = "goerli") : DidCreateOptions(
    method = "ethr",
    options = options("network" to network)
)

