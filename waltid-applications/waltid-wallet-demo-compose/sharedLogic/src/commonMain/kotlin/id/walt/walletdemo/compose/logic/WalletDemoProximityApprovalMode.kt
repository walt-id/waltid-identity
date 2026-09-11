package id.walt.walletdemo.compose.logic

/** Remembered UX preference only. Selecting a mode never grants permission to disclose data. */
enum class WalletDemoProximityApprovalMode(val persistedValue: String) {
    AskEachTime("ask_each_time"),
    PrepareSharing("prepare_sharing");

    companion object {
        fun fromPersistedValue(value: String?): WalletDemoProximityApprovalMode =
            entries.firstOrNull { it.persistedValue == value } ?: AskEachTime
    }
}
