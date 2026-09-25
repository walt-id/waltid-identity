package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.cose.CoseCertificate
import id.walt.crypto.keys.Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInstance
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.signEach
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.sdjwt.SDMap
import id.walt.w3c.issuance.IssuanceClock
import id.walt.w3c.issuance.InstantClock
import id.walt.w3c.issuance.dataFunctionsFor
import id.walt.w3c.utils.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
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
class MdocCredentialHandler(
    private val roundValidityToTwelveHours: Boolean = false,
    private val requireExpectedUpdateWithinWindow: Boolean = false,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialEndpointHandler, Crypto2CredentialEndpointHandler {
    override suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        issuanceBatch: CredentialIssuanceBatch,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        authorizedTransactionDataTypes: List<String>?,
        validFrom: Instant?,
        validUntil: Instant?,
        expectedUpdate: Instant?,
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
                issue = { certificateChain, docType, signedAt, effectiveValidFrom, effectiveValidUntil, instance ->
                    MdocCredentialSigner.generateMdocCredential(
                        credentialRequest = request,
                        credentialData = mapMdocData(
                            instance.input.credentialData,
                            dataMapping,
                            issuerId,
                            display,
                            instance.verifiedProof,
                            signedAt,
                        ),
                        issuerKey = issuerKey,
                        issuerCertificate = certificateChain,
                        docType = docType,
                        signedAt = signedAt,
                        validFrom = effectiveValidFrom,
                        validUntil = effectiveValidUntil,
                        expectedUpdate = expectedUpdate,
                        requireExpectedUpdateWithinWindow = requireExpectedUpdateWithinWindow,
                        status = instance.input.credentialStatus,
                        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                        verifiedProof = instance.verifiedProof,
                        authorizedTransactionDataTypes = authorizedTransactionDataTypes,
                    )
                },
                issuanceBatch = issuanceBatch,
                x5Chain = x5Chain,
                mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                validFrom = validFrom,
                validUntil = validUntil,
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
        issuanceBatch: CredentialIssuanceBatch,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        authorizedTransactionDataTypes: List<String>?,
        validFrom: Instant?,
        validUntil: Instant?,
        expectedUpdate: Instant?,
    ): CredentialResponseResult = try {
        computeCredentialResult(
            request = request,
            configuration = configuration,
            issuanceBatch = issuanceBatch,
            x5Chain = x5Chain,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            validFrom = validFrom,
            validUntil = validUntil,
            issue = { certificateChain, docType, signedAt, effectiveValidFrom, effectiveValidUntil, instance ->
                MdocCredentialSigner.generateMdocCredential(
                    credentialRequest = request,
                    credentialData = mapMdocData(
                        instance.input.credentialData,
                        dataMapping,
                        issuerId,
                        display,
                        instance.verifiedProof,
                        signedAt,
                    ),
                    issuerKey = issuerKey.key,
                    signatureAlgorithm = issuerKey.requireCoseAlgorithm(),
                    issuerCertificate = certificateChain,
                    docType = docType,
                    signedAt = signedAt,
                    validFrom = effectiveValidFrom,
                    validUntil = effectiveValidUntil,
                    expectedUpdate = expectedUpdate,
                    requireExpectedUpdateWithinWindow = requireExpectedUpdateWithinWindow,
                    status = instance.input.credentialStatus,
                    mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                    verifiedProof = instance.verifiedProof,
                    authorizedTransactionDataTypes = authorizedTransactionDataTypes,
                )
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CredentialResponseResult.Failure(e.toCredentialHandlerError())
    }

    /** Resolve namespace element mappings before mdoc-specific JSON-to-CBOR conversions. */
    private suspend fun mapMdocData(
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        issuerId: String,
        display: List<CredentialDisplay>?,
        verifiedProof: VerifiedCredentialProof?,
        issuedAt: Instant,
    ): JsonObject {
        if (dataMapping == null || dataMapping.isEmpty()) return credentialData
        val namespaceMapping = dataMapping.mdocNamespaceMapping(credentialData) ?: return credentialData
        return credentialData.mergeMdocPayloadWithMapping(
            mapping = namespaceMapping,
            context = mdocMappingContext(issuerId, display, verifiedProof?.holderDid),
            data = dataFunctionsFor(InstantClock(issuedAt)),
        )
    }

    private fun mdocMappingContext(
        issuerId: String,
        display: List<CredentialDisplay>?,
        subjectDid: String?,
    ): Map<String, JsonElement> = buildMap {
        put("issuerId", JsonPrimitive(issuerId))
        put("issuerDid", JsonPrimitive(issuerId))
        subjectDid?.takeIf { it.isNotBlank() }?.let { put("subjectDid", JsonPrimitive(it)) }
        display?.takeIf { it.isNotEmpty() }?.let { put("display", Json.encodeToJsonElement(it)) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun computeCredentialResult(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuanceBatch: CredentialIssuanceBatch,
        x5Chain: List<X509Certificate>?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        validFrom: Instant?,
        validUntil: Instant?,
        issue: suspend (
            certificateChain: List<CoseCertificate>,
            docType: String,
            signedAt: Instant,
            validFrom: Instant?,
            validUntil: Instant,
            instance: CredentialIssuanceInstance,
        ) -> String,
    ): CredentialResponseResult.Success {
        val docType = configuration.doctype
            ?: throw IllegalArgumentException("Missing doctype for mDoc credential configuration")

        val certificates = requireNotNull(x5Chain?.takeIf { it.isNotEmpty() }) {
            "mDoc issuance requests require that the x5Chain parameter contains at least one entry"
        }
        val issuerCertificateChain = certificates.map { CoseCertificate(it.encodedDer.toByteArray()) }

        val issuedAt = issuanceInstant()
        val roundedValidity = if (roundValidityToTwelveHours) {
            roundedMdocValidity(issuedAt, certificates.first().data.validity, validFrom, validUntil)
        } else null
        val signedAt = roundedValidity?.signed ?: issuedAt
        val effectiveValidFrom = roundedValidity?.validFrom ?: validFrom
        val effectiveValidUntil = roundedValidity?.validUntil ?: (validUntil ?: issuedAt.plus(365.days))
        return issuanceBatch.signEach { instance ->
            val credentialData = instance.input.credentialData
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
            issue(
                issuerCertificateChain, docType, signedAt,
                effectiveValidFrom, effectiveValidUntil, instance,
            )
        }
    }

    private suspend fun issuanceInstant(): Instant =
        currentCoroutineContext()[IssuanceClock]?.clock?.now() ?: now()
}
