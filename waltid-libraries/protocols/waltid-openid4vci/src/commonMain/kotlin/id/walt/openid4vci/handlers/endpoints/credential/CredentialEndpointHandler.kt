package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.oid4vc.data.DisplayProperties
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject

/**
 * Handler for credential responses per credential format.
 */
fun interface CredentialEndpointHandler {
    suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<String>?,
        display: List<DisplayProperties>?,
    ): CredentialResponseResult
}
