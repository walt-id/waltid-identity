package id.walt.did.dids.registrar.dids

class DidBtcrCreateOptions(chain: String) : DidCreateOptions(
    method = "btcr",
    options = options("chain" to chain)
)
