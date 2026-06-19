package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.sdjwt.SDMap
import id.walt.mdoc.objects.mso.Status
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

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
        x5Chain: List<CertificateDer>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
    ): CredentialResponseResult
}
