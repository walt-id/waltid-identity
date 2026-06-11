package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.crypto.keys.Key
import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.SDMap
import id.walt.x509.CertificateDer
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
class MdocCredentialHandler : CredentialEndpointHandler {
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
    ): CredentialResponseResult {
        return try {
            computeCredentialResult(
                request = request,
                configuration = configuration,
                issuerKey = issuerKey,
                credentialData = credentialData,
                x5Chain = x5Chain,
                mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                credentialStatus = credentialStatus,
                validFrom = validFrom,
                validUntil = validUntil,
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun computeCredentialResult(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        credentialData: JsonObject,
        x5Chain: List<CertificateDer>?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
    ): CredentialResponseResult.Success {
        if (configuration.format != CredentialFormat.MSO_MDOC) {
            throw IllegalArgumentException("Unsupported format ${configuration.format.value}")
        }

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
        }.map { CoseCertificate(it.bytes.toByteArray()) }

        val issuedCredential = MdocCredentialSigner.generateMdocCredential(
            credentialRequest = request,
            credentialData = credentialData,
            issuerKey = issuerKey,
            issuerCertificate = issuerCertificateChain,
            docType = docType,
            validFrom = validFrom,
            validUntil = resolveValidUntil(request, validUntil),
            status = credentialStatus,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
        )

        return CredentialResponseResult.Success(
            CredentialResponse(
                credentials = listOf(
                    IssuedCredential(credential = JsonPrimitive(issuedCredential)),
                ),
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
