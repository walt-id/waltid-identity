package id.walt.issuer.issuance

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.data.TxInputMode
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class NewSingleCredentialIssuanceRequest(
    val credentialData: W3CVC,
    val credentialConfigurationId: String = credentialData.getType().last(), //+ "_jwt_vc_json", // TODO <-- test
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
)

@Serializable
data class IssuerConfiguration(
    val issuerKey: JsonObject,
    val issuerDid: String,
)

@Serializable
data class PinConfiguration(
    val description: String,
    val pin: String? = null,

    val type: TxInputMode = TxInputMode.forPin(pin)
        ?: error("If no pin is provided, pin input `type` has to be provided, allowed values: ${TxInputMode.entries.joinToString()}."),
    val pinLength: Int = pin?.length ?: error("Neither pin nor pin length was supplied"),

    /**
     * if no pin is supplied
     */
    val callbackAuthenticationUrl: String? = null,
) {
    fun toTxCode() = TxCode(type, pinLength, description)

    init {
        require(
            (pin == null && callbackAuthenticationUrl != null)
                    || (pin != null && callbackAuthenticationUrl == null)
        ) { "Either pin directly or authentication URL (and no pin) has to be provided." }
    }
}

@Serializable
data class IssuanceConfiguration(
    /**
     * select issuance flow to use (optional): full auth or pre-auth
     * TODO
     */
    val flow: GrantType = GrantType.authorization_code,
    /**
     * optional for pre-auth flow: tx_code ("pin")
     */
    val pin: PinConfiguration? = null,

    /**
     * optionally: add a URL to send updates for this issuance session to
     */
    val callbackUrl: String? = null, // TODO
) {
    init {
        if (pin != null) require(flow == GrantType.pre_authorized_code) { "pin argument can only be used for pre-auth issuance flow" }
    }
}

@Serializable
data class NewIssuanceRequest(
    val issuer: IssuerConfiguration,
    val issuance: IssuanceConfiguration,
    val credential: List<NewSingleCredentialIssuanceRequest>,
)

@Serializable
data class IssuanceRequest(
    val issuerKey: JsonObject,
    val issuerDid: String,
    val credentialConfigurationId: String,
    val credentialData: W3CVC?,
    val mdocData: Map<String, JsonObject>? = null,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    val authenticationMethod: AuthenticationMethod? = AuthenticationMethod.PRE_AUTHORIZED, // "PWD" OR "ID_TOKEN" OR "VP_TOKEN" OR "PRE_AUTHORIZED" OR "NONE"
    val vpRequestValue: String? = null,
    val vpProfile: OpenId4VPProfile? = null,
    val useJar: Boolean? = null,
    val x5Chain: List<String>? = null,
    val trustedRootCAs: List<String>? = null,
)

@Serializable
data class IssuerOnboardingResponse(
    val issuerKey: JsonElement, val issuerDid: String,
)
