package id.walt.wallet2.mobile.issuance

import id.walt.wallet2.handlers.WalletDeferredIssuanceReference

/** Wallet-local reference for polling a retained deferred credential after reopening the wallet. */
public data class DeferredIssuanceId(public val value: String) {
    init { require(value.isNotBlank()) { "Deferred issuance ID must contain a value" } }

    /** Omits the durable reference from diagnostics. */
    public override fun toString(): String = "DeferredIssuanceId(redacted)"
}

/** Typed resume reference shared by a deferred outcome and the retained wallet listing. */
public val WalletDeferredIssuanceReference.deferredId: DeferredIssuanceId get() = DeferredIssuanceId(id)
