package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile

internal val DemoTransactionDataProfiles = listOf(
    MobileWalletTransactionDataProfile(
        type = "org.waltid.transaction-data.payment-authorization",
        displayName = "Payment Authorization",
        fields = listOf("amount", "currency", "payee", "reference"),
    ),
    MobileWalletTransactionDataProfile(
        type = "org.waltid.transaction-data.account-access",
        displayName = "Account Access",
        fields = listOf("account_identifier", "access_scope"),
    ),
)
