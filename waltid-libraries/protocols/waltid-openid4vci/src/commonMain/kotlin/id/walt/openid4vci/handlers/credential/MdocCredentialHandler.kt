package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.cose.CoseCertificate
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
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.SDMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Native mDoc credential response handler.
 *
 * This mirrors the old issuer2 mDoc flow, but uses the native vci request model and the
 * new mdoc issuer directly.
 */
@OptIn(ExperimentalSerializationApi::class)
class MdocCredentialHandler : CredentialEndpointHandler, Crypto2CredentialEndpointHandler {
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
    ): CredentialResponseResult {
        return try {
            if (configuration.format != CredentialFormat.MSO_MDOC) {
                return CredentialResponseResult.Failure(
                    CredentialError(
                        CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                        "Unsupported format ${configuration.format.value}"
                    )
                )
            }

            computeCredentialResult(
                request = request,
                configuration = configuration,
                issue = { certificateChain, docType, effectiveValidUntil, verifiedProof ->
                    MdocCredentialSigner.generateMdocCredential(
                        credentialRequest = request,
                        credentialData = credentialData,
                        issuerKey = issuerKey,
                        issuerCertificate = certificateChain,
                        docType = docType,
                        validFrom = validFrom,
                        validUntil = effectiveValidUntil,
                        expectedUpdate = expectedUpdate,
                        status = credentialStatus,
                        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                        verifiedProof = verifiedProof,
                        authorizedTransactionDataTypes = authorizedTransactionDataTypes,
                    )
                },
                credentialData = credentialData,
                x5Chain = x5Chain,
                mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                validUntil = validUntil,
                verifiedProofs = verifiedProofs,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CredentialResponseResult.Failure(e.toCredentialHandlerError())
        }
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
    ): CredentialResponseResult = try {
        computeCredentialResult(
            request = request,
            configuration = configuration,
            credentialData = credentialData,
            x5Chain = x5Chain,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            validUntil = validUntil,
            verifiedProofs = verifiedProofs,
            issue = { certificateChain, docType, effectiveValidUntil, verifiedProof ->
                MdocCredentialSigner.generateMdocCredential(
                    credentialRequest = request,
                    credentialData = credentialData,
                    issuerKey = issuerKey.key,
                    signatureAlgorithm = issuerKey.requireCoseAlgorithm(),
                    issuerCertificate = certificateChain,
                    docType = docType,
                    validFrom = validFrom,
                    validUntil = effectiveValidUntil,
                    expectedUpdate = expectedUpdate,
                    status = credentialStatus,
                    mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                    verifiedProof = verifiedProof,
                    authorizedTransactionDataTypes = authorizedTransactionDataTypes,
                )
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CredentialResponseResult.Failure(e.toCredentialHandlerError())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun computeCredentialResult(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        credentialData: JsonObject,
        x5Chain: List<X509Certificate>?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        validUntil: Instant?,
        verifiedProofs: List<VerifiedCredentialProof>,
        issue: suspend (
            certificateChain: List<CoseCertificate>,
            docType: String,
            validUntil: Instant,
            verifiedProof: VerifiedCredentialProof?,
        ) -> String,
    ): CredentialResponseResult.Success {
        val docType = configuration.doctype
            ?: throw IllegalArgumentException("Missing doctype for mDoc credential configuration")

        val namespaceIdentifiers = credentialData.keys
        require(namespaceIdentifiers.isNotEmpty()) {
            "At least one namespace identifier needs to be specified for mDoc issuance, found none in credentialData: $credentialData"
        }
        mDocNameSpacesDataMappingConfig?.let { mappingConfig ->
            require(namespaceIdentifiers.containsAll(mappingConfig.keys)) {
                "Invalid mDoc nameSpace data mapping configuration: found data mapping configuration for nameSpace that is not defined in credentialData namespaces"
            }
        }

        namespaceIdentifiers.forEach { namespaceIdentifier ->
            requireNotNull(credentialData[namespaceIdentifier]?.jsonObject) {
                "Credential data for namespace $namespaceIdentifier must be a JSON object"
            }
        }

        val issuerCertificateChain = requireNotNull(x5Chain?.takeIf { it.isNotEmpty() }) {
            "mDoc issuance requests require that the x5Chain parameter contains at least one entry"
        }.map { CoseCertificate(it.encodedDer.toByteArray()) }

        // Issues one credential per verified proof, or a single credential bound to the request proof.
        val effectiveValidUntil = resolveValidUntil(request, validUntil)
        val issuedCredentials = verifiedProofs.ifEmpty { listOf(null) }.map { verifiedProof ->
            issue(issuerCertificateChain, docType, effectiveValidUntil, verifiedProof)
        }

        return CredentialResponseResult.Success(
            CredentialResponse(
                credentials = issuedCredentials.map { IssuedCredential(credential = JsonPrimitive(it)) },
            ),
        )
    }

    private fun resolveValidUntil(
        request: CredentialRequest,
        configuredValidUntil: Instant?,
    ): Instant =
        request.requestForm["validUntil"]
            ?.firstOrNull()
            ?.toLongOrNull()
            ?.let(Instant::fromEpochMilliseconds)
            ?: configuredValidUntil
            ?: Clock.System.now().plus(365.days)

}
