package id.walt.authkit

import id.walt.authkit.accounts.AccountStore
import id.walt.authkit.accounts.ExampleAccountStore
import id.walt.authkit.sessions.InMemorySessionStore
import id.walt.authkit.sessions.InMemoryTokenStore

object AuthKitManager {

    var accountStore: AccountStore = ExampleAccountStore
    var sessionStore = InMemorySessionStore()
    var tokenStore = InMemoryTokenStore()

}
