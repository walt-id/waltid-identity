package id.walt

import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifierRegistry
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.JWTIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.RADIUSIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import kotlin.test.Test

class AccountIdentifierRegistryTest {

    @Test
    fun testIdentifierRegistry() {
        listOf(
            EmailIdentifier("test@example.org"),
            JWTIdentifier("subject1"),
            RADIUSIdentifier("example.host", "username1"),
            UsernameIdentifier("alice1")
        ).associateWith { it.identifierName to it.toDataString() }
            .map { (k, v) -> k to AccountIdentifierRegistry.getAccountIdentifier(v.first, v.second) }
            .forEach {
                println("${it.first} == ${it.second}")
                check(it.first == it.second) { "${it.first} does not match ${it.second}" }
            }
    }
}
