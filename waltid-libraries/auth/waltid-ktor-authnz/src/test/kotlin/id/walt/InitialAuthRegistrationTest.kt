package id.walt

import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifierManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.initalauth.AuthMethodRegistrationWrapper
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.lang.annotations.Language
import kotlin.test.Test

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
        // Our HTTP request
        val accountId = "account110011"

        val _email = myAuthMethodRegistration["identifier"]!!.jsonObject["email"]!!.jsonPrimitive.content

        println("HTTP request: $myAuthMethodRegistration")
        val wrapper = Json.decodeFromJsonElement<AuthMethodRegistrationWrapper>(myAuthMethodRegistration)
        println("In wrapper: $wrapper")

        val registration = wrapper.transformWrapperToRegistration()
        println("Actual registration data: $registration")
        check(_email in registration.toString())

        // Data class is ready

        val identifier = registration.identifier
        check(_email in identifier.toString())

        //val dbAccountIdentifier = (identifier.accountIdentifierName to identifier.toDataString())
        val dbAccountIdentifier = AccountIdentifierManager.getAccountIdentifier(identifier.accountIdentifierName, identifier.toDataString())
        check(identifier == dbAccountIdentifier)

        val accountIdentifierAccountMapping = hashMapOf(dbAccountIdentifier to accountId)
        println("(Map account identifier type=${identifier.accountIdentifierName} data=${identifier.toDataString()} to account $accountId)")

        val authMethod = registration.getAuthMethod()
        check(authMethod.id == myAuthMethodRegistration["type"]?.jsonPrimitive?.content)

        // usually there could be multiple, this is single one now
        val accountIdentifierAuthMethodDataMapping = HashMap<AccountIdentifier, String>()

        if (registration.data != null) {
            val registrationDataJson = Json.encodeToString(registration.data)
            println("(Map auth method data $registrationDataJson to above mentioned account identifier, auth method is ${authMethod.id})")
            accountIdentifierAuthMethodDataMapping[dbAccountIdentifier] = registrationDataJson
        }

        // --- Login
        val loginMethod = "email"
        val loginId = "user@email.com"
        val loginPassword = "pass1234"

        println("""
        -- Login --
        Login method: $loginMethod
        Login id: $loginId
        Login password: $loginPassword
        """.trimIndent())

        // Resolve account identifier

        // this is a bit simplified now, with the data string
        val accountIdentifierForLogin = AccountIdentifierManager.getAccountIdentifier(loginMethod, loginId)
        println("Resolved to account identifier: $accountIdentifierForLogin")
        check(accountIdentifierForLogin == identifier)
        check(accountIdentifierForLogin == dbAccountIdentifier)

        // Get auth method data for identifier
        val accountForLogin = accountIdentifierAccountMapping[accountIdentifierForLogin]
        println("Resolved to account for login: $accountForLogin")
        check(accountForLogin == accountId)

        val dataForLogin = Json.decodeFromString<AuthMethodStoredData>(accountIdentifierAuthMethodDataMapping[accountIdentifierForLogin]!!)
        println("Data for login is: $dataForLogin")
        check(dataForLogin == registration.data)

        println("Method handling: Authenticate $loginPassword vs data $dataForLogin")
        check(loginPassword in dataForLogin.toString())
    }

}
