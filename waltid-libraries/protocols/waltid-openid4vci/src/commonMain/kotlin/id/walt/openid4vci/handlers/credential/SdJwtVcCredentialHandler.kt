package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.metadata.type.TypeMetadataException
import id.walt.sdjwt.metadata.type.TypeMetadataFailure
import id.walt.sdjwt.metadata.type.TypeMetadataValidationException
import id.walt.sdjwt.metadata.type.validateForIssuance
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.sdjwt.SDJwtVerificationException
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * SD-JWT VC credential response handler.
 */
class SdJwtVcCredentialHandler(
    private val metadataResolver: SdJwtVcMetadataResolver?,
    private val roundGeneratedTimeClaims: Boolean = false,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialEndpointHandler, Crypto2CredentialEndpointHandler {
    constructor(roundGeneratedTimeClaims: Boolean = false, now: () -> Instant = { Clock.System.now() }) :
        this(null, roundGeneratedTimeClaims, now)

    constructor() : this(null, false, { Clock.System.now() })

    private companion object {
        val supportedFormats = setOf(CredentialFormat.SD_JWT_VC)
        val logger = KotlinLogging.logger {}
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
    ): CredentialResponseResult = sign(
        configuration, issuanceBatch, SdJwtVcCredentialSigner.IssuerSigningKey.Legacy(issuerKey),
        issuerId, dataMapping, selectiveDisclosure, x5Chain, display,
    )

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
    ): CredentialResponseResult = sign(
        configuration, issuanceBatch,
        SdJwtVcCredentialSigner.IssuerSigningKey.Crypto2(issuerKey.key, issuerKey.requireJwsAlgorithm()),
        issuerId, dataMapping, selectiveDisclosure, x5Chain, display,
    )

    private suspend fun sign(
        configuration: CredentialConfiguration,
        issuanceBatch: CredentialIssuanceBatch,
        issuerKey: SdJwtVcCredentialSigner.IssuerSigningKey,
        issuerId: String,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
    ): CredentialResponseResult = try {
        if (configuration.format !in supportedFormats) {
            CredentialResponseResult.Failure(CredentialError(
                CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION, "Unsupported format ${configuration.format.value}",
            ))
        } else if (configuration.vct == null) {
            CredentialResponseResult.Failure(CredentialError(
                CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, "Missing vct for SD-JWT VC credential configuration",
            ))
        } else {
            val context = metadataResolver?.resolve(configuration)
            try {
                if (context != null && (context.publication.vct != configuration.vct ||
                    context.resolved.documents.first().vct != context.publication.vct)) {
                    throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Issuance metadata context has a different VCT")
                }
                val claims = context?.let { JsonObject(mapOf("vct#integrity" to JsonPrimitive(it.publication.integrity))) }
                val effectiveMapping = if (roundGeneratedTimeClaims && dataMapping != null) {
                    roundedTimeClaimMapping(dataMapping, now())
                } else dataMapping
                val prepared = issuanceBatch.instances.map { instance ->
                    SdJwtVcCredentialSigner.prepare(
                        verifiedBinding = instance.verifiedBinding, credentialData = instance.input.credentialData,
                        issuerSigningKey = issuerKey, issuerId = issuerId, vct = configuration.vct,
                        selectiveDisclosure = selectiveDisclosure, dataMapping = effectiveMapping,
                        x5Chain = x5Chain, display = display, sdJwtTypeHeader = configuration.format.value,
                        sdJwtCredentialClaims = claims,
                    )
                }
                context?.let { it.resolved.validateForIssuance(prepared.map { item -> item.payload }, it::readDocument) }
                CredentialResponseResult.Success(CredentialResponse(credentials = prepared.map {
                    IssuedCredential(JsonPrimitive(SdJwtVcCredentialSigner.signPrepared(it, issuerKey)))
                }))
            } finally { context?.close() }
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: CredentialProofValidationException) {
        CredentialResponseResult.Failure(CredentialError(cause.errorCode, cause.message))
    } catch (cause: TypeMetadataValidationException) {
        CredentialResponseResult.Failure(CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, cause.message))
    } catch (cause: SDJwtVerificationException) {
        CredentialResponseResult.Failure(CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, "Credential data breaks SD-JWT processing"))
    } catch (cause: TypeMetadataException) {
        logger.error(cause) { "SD-JWT type metadata preparation failed" }
        CredentialResponseResult.Failure(CredentialError(OAuthErrorCodes.SERVER_ERROR, "Credential type metadata preparation failed"))
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
