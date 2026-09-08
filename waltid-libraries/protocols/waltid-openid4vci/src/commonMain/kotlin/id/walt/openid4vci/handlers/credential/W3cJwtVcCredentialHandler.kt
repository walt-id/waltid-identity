package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.SDMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig

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
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        authorizedTransactionDataTypes: List<String>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
        expectedUpdate: Instant?,
        verifiedProofs: List<VerifiedCredentialProof>,
    ): CredentialResponseResult = sign(configuration, verifiedProofs) { verifiedProof ->
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
            verifiedProof = verifiedProof,
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
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        authorizedTransactionDataTypes: List<String>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
        expectedUpdate: Instant?,
        verifiedProofs: List<VerifiedCredentialProof>,
    ): CredentialResponseResult = sign(configuration, verifiedProofs) { verifiedProof ->
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
            verifiedProof = verifiedProof,
        )
    }

    /**
     * Issues one credential per verified proof, or a single credential bound to the
     * request proof when no proof was verified upfront.
     */
    private suspend fun sign(
        configuration: CredentialConfiguration,
        verifiedProofs: List<VerifiedCredentialProof>,
        issue: suspend (VerifiedCredentialProof?) -> String,
    ): CredentialResponseResult {
        return try {
            if (configuration.format !in supportedFormats) {
                return CredentialResponseResult.Failure(
                    CredentialError(
                        CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                        "Unsupported format ${configuration.format.value}"
                    )
                )
            }

            val proofsToIssue = verifiedProofs.ifEmpty { listOf(null) }
            val jwtVcs = proofsToIssue.map { verifiedProof -> issue(verifiedProof) }

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = jwtVcs.map { IssuedCredential(credential = JsonPrimitive(it)) },
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CredentialResponseResult.Failure(e.toCredentialHandlerError())
        }
    }
}
