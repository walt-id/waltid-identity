package id.walt

import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifierManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.methods.initialauth.AuthMethodRegistrationWrapper
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import kotlinx.serialization.json.*
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InitialAuthRegistrationTest {

    @Language("JSON")
    val myAuthMethodRegistration = """
    {
      "type": "email",
      "identifier": {
        "email": "user@email.com"
      },
      "data": {
        "password": "pass1234"
      }
    }
    """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun test() {
        val accountId = "account110011"
        val expectedEmail = myAuthMethodRegistration["identifier"]!!.jsonObject["email"]!!.jsonPrimitive.content
        val wrapper = Json.decodeFromJsonElement<AuthMethodRegistrationWrapper>(myAuthMethodRegistration)
        val registration = wrapper.transformWrapperToRegistration()
        val identifier = assertIs<EmailIdentifier>(registration.identifier)
        assertEquals(expectedEmail, identifier.email)

        val dbAccountIdentifier = AccountIdentifierManager.getAccountIdentifier(identifier.accountIdentifierName, identifier.toDataString())
        check(identifier == dbAccountIdentifier)
        val accountIdentifierAccountMapping = hashMapOf(dbAccountIdentifier to accountId)
        val authMethod = registration.getAuthMethod()
        check(authMethod.id == myAuthMethodRegistration["type"]?.jsonPrimitive?.content)

        val accountIdentifierAuthMethodDataMapping = HashMap<AccountIdentifier, String>()
        if (registration.data != null) {
            accountIdentifierAuthMethodDataMapping[dbAccountIdentifier] = Json.encodeToString(registration.data)
        }

        val loginMethod = "email"
        val loginId = "user@email.com"
        val loginPassword = "pass1234"
        val accountIdentifierForLogin = AccountIdentifierManager.getAccountIdentifier(loginMethod, loginId)
        check(accountIdentifierForLogin == identifier)
        check(accountIdentifierForLogin == dbAccountIdentifier)
        check(accountIdentifierAccountMapping[accountIdentifierForLogin] == accountId)

        val dataForLogin = Json.decodeFromString<AuthMethodStoredData>(accountIdentifierAuthMethodDataMapping[accountIdentifierForLogin]!!)
        check(dataForLogin == registration.data)
        assertEquals(loginPassword, assertIs<EmailPassStoredData>(dataForLogin).password)
    }
}
