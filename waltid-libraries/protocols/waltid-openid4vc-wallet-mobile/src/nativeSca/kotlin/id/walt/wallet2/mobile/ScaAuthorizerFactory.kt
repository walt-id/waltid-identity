package id.walt.wallet2.mobile

import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider

internal fun createScaPresentationAuthorizer(provider: PlatformManagedKeyProvider): WalletScaPresentationAuthorizer =
    NativeScaPresentationAuthorizer(provider)
