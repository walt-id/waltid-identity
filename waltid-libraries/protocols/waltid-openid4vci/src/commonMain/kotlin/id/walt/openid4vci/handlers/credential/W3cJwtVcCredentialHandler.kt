package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.mdoc.objects.mso.Status
import id.walt.sdjwt.SDMap
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

/**
 * W3C JWT VC credential response handler.
 * Supports JWT VC formats (jwt_vc_json, jwt_vc).
 */
class W3cJwtVcCredentialHandler : CredentialEndpointHandler, Crypto2CredentialEndpointHandler {
    private companion object {
        val supportedFormats = setOf(
            CredentialFormat.JWT_VC_JSON,
            CredentialFormat.JWT_VC,
        )
    }

    @Deprecated("Use the Crypto2CredentialSigningKey overload")
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
    ): CredentialResponseResult = sign(configuration) {
        W3cJwtVcCredentialSigner.generateW3CJwtVC(
            credentialRequest = request,
            credentialData = credentialData,
            issuerId = issuerId,
            issuerKey = issuerKey,
            selectiveDisclosure = selectiveDisclosure,
            dataMapping = dataMapping,
            x5Chain = x5Chain,
            display = display,
            w3cVersion = w3cVersion,
        )
    }

    override suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Crypto2CredentialSigningKey,
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
    ): CredentialResponseResult = sign(configuration) {
        W3cJwtVcCredentialSigner.generateW3CJwtVC(
            credentialRequest = request,
            credentialData = credentialData,
            issuerId = issuerId,
            issuerKey = issuerKey.key,
            algorithm = issuerKey.requireJwsAlgorithm(),
            selectiveDisclosure = selectiveDisclosure,
            dataMapping = dataMapping,
            x5Chain = x5Chain,
            display = display,
            w3cVersion = w3cVersion,
        )
    }

    private suspend fun sign(
        configuration: CredentialConfiguration,
        issue: suspend () -> String,
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

            val jwtVc = issue()

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(
                        IssuedCredential(credential = JsonPrimitive(jwtVc)),
                    ),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
