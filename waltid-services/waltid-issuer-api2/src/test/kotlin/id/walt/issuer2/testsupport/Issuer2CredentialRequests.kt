package id.walt.issuer2.testsupport

import id.walt.openid4vci.prooftypes.Proofs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun credentialRequest(
    credentialConfigurationId: String,
    proofs: Proofs,
): JsonObject = buildJsonObject {
    put("credential_configuration_id", credentialConfigurationId)
    put("proofs", issuer2TestJson.encodeToJsonElement(Proofs.serializer(), proofs))
}
