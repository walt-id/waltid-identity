package id.walt

import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifierManager
import id.walt.ktorauthnz.accounts.identifiers.methods.*
import kotlin.test.Test

class AccountIdentifierManagerTest {

    @Test
    fun testIdentifierRegistry() {
        listOf(
            EmailIdentifier("test@example.org"),
            JWTIdentifier("subject1"),
            RADIUSIdentifier("example.host", "username1"),
            UsernameIdentifier("alice1"),
            OIDCIdentifier("issuer", "subject")
        ).associateWith { it.accountIdentifierName to it.toDataString() }
            .map { (k, v) ->
                k to AccountIdentifierManager.getAccountIdentifier(v.first, v.second)
            }
            .forEach {
                println("${it.first} == ${it.second}")
                check(it.first == it.second) { "${it.first} does not match ${it.second}" }
            }
    }
}
