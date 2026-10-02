package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Owned by the request's coordinator when its platform host can be recreated. */
class WalletDemoOfferDraft {
    var transactionCode by mutableStateOf("")
    var copies by mutableStateOf<Map<String, Int>>(emptyMap())
}
