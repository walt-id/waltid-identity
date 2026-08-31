package id.walt.walletdemo.compose.logic

/** Stable demo choices for one immutable proximity-session configuration. */
enum class WalletDemoProximityTransportProfile(
    internal val persistedValue: String,
) {
    Default("default"),
    ProvisionalNfcV2Hybrid("provisional_nfc_v2_hybrid"),
    ProvisionalNfcV2Direct("provisional_nfc_v2_direct"),
    ;

    internal companion object {
        fun fromPersistedValue(value: String?): WalletDemoProximityTransportProfile =
            entries.singleOrNull { it.persistedValue == value } ?: Default
    }
}
