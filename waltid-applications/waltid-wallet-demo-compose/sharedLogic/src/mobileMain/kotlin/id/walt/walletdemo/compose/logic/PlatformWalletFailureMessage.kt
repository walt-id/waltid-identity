package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.WalletFailure

internal actual fun platformWalletFailureMessage(error: Throwable): String? =
    WalletFailure.classify(error).takeUnless { it == WalletFailure.internalFailure }?.userMessage
