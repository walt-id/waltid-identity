import E2ETestWebService.loadResource
import id.walt.webwallet.web.model.EmailAccountRequest
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

object E2EResources {
    val defaultTestTimeout = 5.minutes
    val defaultEmailAccount = EmailAccountRequest(
        email = "user@email.com",
        password = "password"
    )
    val issuerKey = loadResource("issuance/key.json")
    val issuerDid = loadResource("issuance/did.txt")
    val openBadgeCredentialData = loadResource("issuance/openbadgecredential.json")
    val credentialMapping = loadResource("issuance/mapping.json")
    val credentialDisclosure = loadResource("issuance/disclosure.json")
    val sdjwtCredential = buildJsonObject {
        put("issuerKey", Json.decodeFromString<JsonElement>(issuerKey))
        put("issuerDid", issuerDid)
        put("credentialConfigurationId", "OpenBadgeCredential_jwt_vc_json")
        put("credentialData", Json.decodeFromString<JsonElement>(openBadgeCredentialData))
        put("mapping", Json.decodeFromString<JsonElement>(credentialMapping))
        put("selectiveDisclosure", Json.decodeFromString<JsonElement>(credentialDisclosure))
    }
    val jwtCredential = JsonObject(sdjwtCredential.minus("selectiveDisclosure"))
    val simplePresentationRequestPayload =
        loadResource("presentation/openbadgecredential-presentation-request.json")
    val nameFieldSchemaPresentationRequestPayload =
        loadResource("presentation/openbadgecredential-name-field-presentation-request.json")

}
