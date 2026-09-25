package id.walt.walletdemo.compose.logic

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy

/** Additional choices are preflighted by the existing signing-identity setup on each platform. */
internal fun WalletDemoSigningProtectionMode.alternativeAuthorizations(): List<KeyUseAuthorizationPolicy> =
    WalletDemoSigningProtection.entries
        .filter { it != defaultSelection && allows(it) }
        .map { it.toKeyUseAuthorizationPolicy() }
