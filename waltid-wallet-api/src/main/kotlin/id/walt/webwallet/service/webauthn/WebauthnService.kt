package id.walt.webwallet.service.webauthn

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.Authenticator
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.*
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.exception.ValidationException


object WebauthnService {

    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

    fun register() {
        val randomChallenge = DefaultChallenge()
    }

    fun attestationVerification() {
        // Client properties
        val attestationObject: ByteArray? = null /* set attestationObject */
        val clientDataJSON: ByteArray? = null /* set clientDataJSON */
        val clientExtensionJSON: String? = null /* set clientExtensionJSON */
        val transports: Set<String>? = null /* set transports */


        // Server properties
        val origin: Origin? = null /* set origin */
        val rpId: String? = null /* set rpId */
        val challenge: Challenge? = null /* set challenge */


        val tokenBindingId: ByteArray? = null /* set tokenBindingId */
        val serverProperty = ServerProperty(origin!!, rpId!!, challenge, tokenBindingId)


        // expectations
        val userVerificationRequired = false
        val userPresenceRequired = true

        val registrationRequest = RegistrationRequest(attestationObject, clientDataJSON, clientExtensionJSON, transports)
        val registrationParameters = RegistrationParameters(serverProperty, null, userVerificationRequired, userPresenceRequired)
        val registrationData: RegistrationData

        try {
            registrationData = webAuthnManager.parse(registrationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }

        try {
            webAuthnManager.validate(registrationData, registrationParameters)
        } catch (e: ValidationException) {
            // If you would like to handle WebAuthn data validation error, please catch ValidationException
            throw e
        }


        // please persist Authenticator object, which will be used in the authentication process.
        val authenticator: Authenticator =
            AuthenticatorImpl( // You may create your own Authenticator implementation to save friendly authenticator name
                registrationData.attestationObject!!.authenticatorData.attestedCredentialData!!,
                registrationData.attestationObject!!.attestationStatement,
                registrationData.attestationObject!!.authenticatorData.signCount
            )

        save(authenticator) // please persist authenticator in your manner
    }

    private val TEMPORARY_STORE = HashMap<ByteArray, Authenticator>()

    fun save(authenticator: Authenticator) {
        TEMPORARY_STORE[authenticator.attestedCredentialData.credentialId] = authenticator
    }

    fun load(credentialId: ByteArray): Authenticator? {
        return TEMPORARY_STORE[credentialId]
    }

    fun updateCounter(credentialId: ByteArray, signCount: Long) {
        TEMPORARY_STORE[credentialId]!!.counter = signCount
    }

    fun assertionVerification() {
        // Client properties
        val credentialId: ByteArray? = null /* set credentialId */
        val userHandle: ByteArray? = null /* set userHandle */
        val authenticatorData: ByteArray? = null /* set authenticatorData */
        val clientDataJSON: ByteArray? = null /* set clientDataJSON */
        val clientExtensionJSON: String? = null /* set clientExtensionJSON */
        val signature: ByteArray? = null /* set signature */


        // Server properties
        val origin: Origin? = null /* set origin */
        val rpId: String? = null /* set rpId */
        val challenge: Challenge? = null /* set challenge */
        val tokenBindingId: ByteArray? = null /* set tokenBindingId */
        val serverProperty = ServerProperty(origin!!, rpId!!, challenge, tokenBindingId)


        // expectations
        val allowCredentials: List<ByteArray>? = null
        val userVerificationRequired = true
        val userPresenceRequired = true

        val authenticator: Authenticator =
            load(credentialId!!)!! // please load authenticator object persisted in the registration process in your manner

        val authenticationRequest =
            AuthenticationRequest(
                credentialId,
                userHandle,
                authenticatorData,
                clientDataJSON,
                clientExtensionJSON,
                signature
            )
        val authenticationParameters =
            AuthenticationParameters(
                serverProperty,
                authenticator,
                allowCredentials,
                userVerificationRequired,
                userPresenceRequired
            )

        val authenticationData: AuthenticationData
        try {
            authenticationData = webAuthnManager.parse(authenticationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }
        try {
            webAuthnManager.validate(authenticationData, authenticationParameters)
        } catch (e: ValidationException) {
            // If you would like to handle WebAuthn data validation error, please catch ValidationException
            throw e
        }

        // please update the counter of the authenticator record
        updateCounter(
            authenticationData.credentialId,
            authenticationData.authenticatorData!!.signCount
        )
    }
}
