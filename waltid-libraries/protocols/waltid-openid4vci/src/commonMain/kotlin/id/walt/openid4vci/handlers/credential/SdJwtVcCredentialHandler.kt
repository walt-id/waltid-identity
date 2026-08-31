package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInstance
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.signEach
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * SD-JWT VC credential response handler.
 */
class SdJwtVcCredentialHandler(
    private val roundGeneratedTimeClaims: Boolean = false,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialEndpointHandler, Crypto2CredentialEndpointHandler {
    private companion object {
        val supportedFormats = setOf(CredentialFormat.SD_JWT_VC)
    }

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
    ): CredentialResponseResult = sign(configuration, issuanceBatch, dataMapping) { vct, instance, effectiveMapping ->
        SdJwtVcCredentialSigner.generateSdJwtVC(
            credentialRequest = request,
            credentialData = instance.input.credentialData,
            issuerId = issuerId,
            issuerKey = issuerKey,
            vct = vct,
            selectiveDisclosure = selectiveDisclosure,
            dataMapping = effectiveMapping,
            x5Chain = x5Chain,
            display = display,
            sdJwtTypeHeader = configuration.format.value,
            verifiedProof = instance.verifiedProof,
        )
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
    ): CredentialResponseResult = sign(configuration, issuanceBatch, dataMapping) { vct, instance, effectiveMapping ->
        SdJwtVcCredentialSigner.generateSdJwtVC(
            credentialRequest = request,
            credentialData = instance.input.credentialData,
            issuerId = issuerId,
            issuerKey = issuerKey.key,
            algorithm = issuerKey.requireJwsAlgorithm(),
            vct = vct,
            selectiveDisclosure = selectiveDisclosure,
            dataMapping = effectiveMapping,
            x5Chain = x5Chain,
            display = display,
            sdJwtTypeHeader = configuration.format.value,
            verifiedProof = instance.verifiedProof,
        )
    }

    /**
     * Issues one credential per ordered batch instance.
     */
    private suspend fun sign(
        configuration: CredentialConfiguration,
        issuanceBatch: CredentialIssuanceBatch,
        dataMapping: JsonObject?,
        issue: suspend (
            vct: String,
            instance: CredentialIssuanceInstance,
            effectiveMapping: JsonObject?,
        ) -> String,
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

            val vct = configuration.vct
                ?: return CredentialResponseResult.Failure(
                    CredentialError(
                        CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                        "Missing vct for SD-JWT VC credential configuration",
                    ),
                )

            val effectiveMapping = if (roundGeneratedTimeClaims && dataMapping != null) {
                roundedTimeClaimMapping(dataMapping, now())
            } else dataMapping
            issuanceBatch.signEach { instance -> issue(vct, instance, effectiveMapping) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CredentialResponseResult.Failure(e.toCredentialHandlerError())
        }
    }

    private fun roundedTimeClaimMapping(mapping: JsonObject, now: Instant): JsonObject =
        JsonObject(mapping.mapValues { (claim, value) ->
            val template = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            when {
                // Round generated issuance times to UTC days and expiry to UTC hours to reduce linkability.
                (claim == "iat" || claim == "nbf") && template == "<timestamp-seconds>" ->
                    JsonPrimitive(now.epochSeconds.floorDiv(86_400L) * 86_400L)

                claim == "exp" && template != null &&
                    template.startsWith("<timestamp-in-seconds:") && template.endsWith(">") -> {
                    val lifetime = Duration.parse(template.removePrefix("<timestamp-in-seconds:").removeSuffix(">"))
                    require(lifetime.isFinite() && lifetime > Duration.ZERO) {
                        "Generated SD-JWT expiry requires a finite, positive lifetime"
                    }
                    val expiry = (now + lifetime).epochSeconds.floorDiv(3_600L) * 3_600L
                    require(expiry > now.epochSeconds) {
                        "Generated SD-JWT expiry is not in the future after hourly rounding; use a longer lifetime or an explicit expiry"
                    }
                    JsonPrimitive(expiry)
                }

                // Explicit dates, custom functions, and unrelated claims are not rounded.
                else -> value
            }
        })
}
