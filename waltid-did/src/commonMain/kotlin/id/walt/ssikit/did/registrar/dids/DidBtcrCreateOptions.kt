package id.walt.ssikit.did.registrar.dids

class DidBtcrCreateOptions(chain: String) : DidCreateOptions(
    method = "btcr",
    options = options("chain" to chain)
)
