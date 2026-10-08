package id.walt.ktorauthnz.accounts

import kotlinx.serialization.json.JsonObject
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AccountExistsException
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.Identify
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.methods.storeddata.IdentifyStoredData
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A new account and the ways it logs in, for [registerAccount]:
 *
 * ```kotlin
 * val accountId = registerAccount {
 *     password(EmailIdentifier("alice@example.com"), "s3cret-password")
 *     totp(secret)
 *     identifier(OIDCIdentifier(issuer, subject))
 * }
 * ```
 */
class NewAccount internal constructor() {
    internal val identifiers = LinkedHashSet<AccountIdentifier>()
    internal val identifierData = mutableListOf<Triple<AccountIdentifier, String, AuthMethodStoredData>>()
    internal val accountData = mutableListOf<Pair<String, AuthMethodStoredData>>()

    /** An identity that logs into this account (an OIDC subject, an LDAP name, a wallet DID, an address, ...). */
    fun identifier(identifier: AccountIdentifier) {
        identifiers += identifier
    }

    /** A password for [identifier] - an email address (method `email`) or a username (method `userpass`); stored hashed. */
    fun password(identifier: AccountIdentifier, password: String) {
        val (method, data) = when (identifier) {
            is EmailIdentifier -> EmailPass.id to EmailPassStoredData(password = password)
            is UsernameIdentifier -> UserPass.id to UserPassStoredData(password = password)
            else -> throw IllegalArgumentException("Passwords belong to an email address or a username, not to ${identifier.accountIdentifierName}")
        }
        data(identifier, method, data)
    }

    /** A TOTP secret (base32), set up with an authenticator app beforehand. */
    fun totp(secret: String) = data(TOTP.id, TOTPStoredData(secret))

    /** The flows the account offers after an `identify` step. */
    fun loginFlows(flows: Set<AuthFlow>) = data(Identify.id, IdentifyStoredData(flows))

    /** Stored data of [method] for [identifier] (e.g. of a custom method). */
    fun data(identifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {
        identifiers += identifier
        identifierData += Triple(identifier, method, data)
    }

    /** Stored data of [method] for the account. */
    fun data(method: String, data: AuthMethodStoredData) {
        accountData += method to data
    }
}

/** An account just registered, for the application's `onAccountRegistered` hook. */
data class RegisteredAccount(
    val accountId: String,
    val identifiers: List<AccountIdentifier>,
    /** What else is known: the fields of a sign-up form, or the claims of an OIDC identity (name, email, ...). */
    val details: JsonObject?,
)

/**
 * Creates an account with [build]'s identifiers and stored data in the configured account store, and returns its id.
 * Refuses (409, [AccountExistsException]) if one of the identifiers belongs to an account already - before writing
 * anything. Then runs the application's `onAccountRegistered` hook (e.g. to create a profile from [details]) and emits
 * [AuthnzEvent.AccountRegistered].
 *
 * All registrations go through here: sign-up routes, new OIDC identities, and `registerUnknownAccounts`.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun registerAccount(
    accountId: String = Uuid.random().toString(),
    details: JsonObject? = null,
    build: NewAccount.() -> Unit,
): String {
    val account = NewAccount().apply(build)
    require(account.identifiers.isNotEmpty()) { "A new account needs at least one identifier to log in with" }
    val store = KtorAuthnzManager.accountStore
    account.identifiers.firstOrNull { store.lookupAccountUuid(it) != null }?.let { throw AccountExistsException(it.accountIdentifierName) }

    account.identifiers.forEach { store.addAccountIdentifierToAccount(accountId, it) }
    // Hashed here (passwords), so that no store keeps them in the clear.
    account.identifierData.forEach { (identifier, method, data) -> store.addAccountIdentifierStoredData(identifier, method, data.transformSavable()) }
    account.accountData.forEach { (method, data) -> store.addAccountStoredData(accountId, method, data.transformSavable()) }
    KtorAuthnzManager.onAccountRegistered?.invoke(RegisteredAccount(accountId, account.identifiers.toList(), details))
    AuthnzEvents.emit(AuthnzEvent.AccountRegistered(accountId, account.identifiers.map { it.accountIdentifierName }))
    return accountId
}
