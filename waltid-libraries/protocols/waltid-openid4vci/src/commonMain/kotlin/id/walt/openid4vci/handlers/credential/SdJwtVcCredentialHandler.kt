package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.mdoc.objects.mso.Status
import id.walt.sdjwt.SDMap
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * SD-JWT VC credential response handler.
 */
class SdJwtVcCredentialHandler : CredentialEndpointHandler {
    private companion object {
        val supportedFormats = setOf(CredentialFormat.SD_JWT_VC)
    }

    override suspend fun sign(
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
        verifiedProofs: List<VerifiedCredentialProof>,
    ): CredentialResponseResult {
        return try {
            if (configuration.format !in supportedFormats) {
                return CredentialResponseResult.Failure(
                    OAuthError(
                        CredentialErrorCodes.UNSUPPORTED_CREDENTIAL_CONFIGURATION,
                        "Unsupported format ${configuration.format.value}"
                    )
                )
            }

            val vct = configuration.vct
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "Missing vct for SD-JWT VC credential configuration"),
                )

            val proofsToIssue = if (verifiedProofs.isEmpty()) {
                listOf<VerifiedCredentialProof?>(null)
            } else {
                verifiedProofs
            }
            val sdJwts = proofsToIssue.map { verifiedProof ->
                SdJwtVcCredentialSigner.generateSdJwtVC(
                    credentialRequest = request,
                    credentialData = credentialData,
                    issuerId = issuerId,
                    issuerKey = issuerKey,
                    vct = vct,
                    selectiveDisclosure = selectiveDisclosure,
                    dataMapping = dataMapping,
                    x5Chain = x5Chain,
                    display = display,
                    sdJwtTypeHeader = configuration.format.value,
                    verifiedProof = verifiedProof,
                )
            }

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = sdJwts.map { IssuedCredential(credential = JsonPrimitive(it)) },
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
