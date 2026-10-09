package id.walt.ktorauthnz.examples.services

import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** A browser with a passkey authenticator (webauthn4j's emulator): answers WebAuthn options as the browser would. */
class ExampleAuthenticator(origin: String) {
    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val browser = ClientPlatform(Origin(origin), WebAuthnAuthenticatorAdaptor(NoneAttestationAuthenticator()))

    private fun String.bytes() = b64.decode(this)
    private fun ByteArray.b64() = b64.encode(this)

    /** `navigator.credentials.create()` for registration [options]; the PublicKeyCredential as WebAuthn JSON. */
    fun create(options: JsonObject): String {
        val user = options["user"]!!.jsonObject
        val credential = browser.create(
            PublicKeyCredentialCreationOptions(
                PublicKeyCredentialRpEntity(options["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content, "Example"),
                PublicKeyCredentialUserEntity(user["id"]!!.jsonPrimitive.content.bytes(), user["name"]!!.jsonPrimitive.content, user["name"]!!.jsonPrimitive.content),
                DefaultChallenge(options["challenge"]!!.jsonPrimitive.content.bytes()),
                listOf(PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
                60_000L, emptyList(),
                AuthenticatorSelectionCriteria(null, true, ResidentKeyRequirement.REQUIRED, UserVerificationRequirement.PREFERRED),
                emptyList(), AttestationConveyancePreference.NONE, null,
            )
        )
        val response = credential.response!!
        return buildJsonObject {
            put("id", credential.id); put("rawId", credential.rawId.b64()); put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", response.clientDataJSON.b64())
                put("attestationObject", response.attestationObject.b64())
                putJsonArray("transports") {}
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }

    /** `navigator.credentials.get()` for login [options]; the PublicKeyCredential as WebAuthn JSON. */
    fun get(options: JsonObject): String {
        val credential = browser.get(
            PublicKeyCredentialRequestOptions(
                DefaultChallenge(options["challenge"]!!.jsonPrimitive.content.bytes()), 60_000L,
                options["rpId"]!!.jsonPrimitive.content, emptyList(), UserVerificationRequirement.PREFERRED, null,
            )
        )
        val response = credential.response!!
        return buildJsonObject {
            put("id", credential.id); put("rawId", credential.rawId.b64()); put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", response.clientDataJSON.b64())
                put("authenticatorData", response.authenticatorData.b64())
                put("signature", response.signature.b64())
                response.userHandle?.let { put("userHandle", it.b64()) }
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }
}
