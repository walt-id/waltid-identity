@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.validateRequestTransactionData
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest

/** Expected OpenID4VP request failure that can be returned through the resolved response channel. */
data class PresentationRequestError(
    val code: WalletPresentFunctionality2.OID4VPErrorCode,
    val message: String,
)

/** Result of validating a resolved OpenID4VP request for this wallet implementation. */
sealed interface PresentationRequestValidationResult {
    data class Valid(
        val transactionData: List<DecodedTransactionData>,
    ) : PresentationRequestValidationResult

    data class Invalid(
        val error: PresentationRequestError,
    ) : PresentationRequestValidationResult
}

/**
 * Validates wallet capabilities and request semantics after request resolution.
 *
 * Invalid results are safe to return through a signed request's response channel or an unsigned request's
 * client-bound redirect URI. Failures that prevent a safe response remain local exceptions.
 */
object PresentationRequestValidator {
    fun validate(
        resolvedRequest: ResolvedAuthorizationRequest,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        formatCapabilities: WalletPresentationFormatRegistry.RuntimeCapabilities =
            WalletPresentationFormatRegistry.defaultCapabilities(),
    ): PresentationRequestValidationResult {
        val request = resolvedRequest.authorizationRequest
        requireDispatchableResponse(resolvedRequest)

        if (request.nonce.isNullOrBlank()) {
            return invalid(
                WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
                "Authorization Request nonce is required",
            )
        }
        if (request.responseType !in supportedResponseTypes) {
            return invalid(
                WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE,
                "Unsupported response_type '${request.responseType?.responseType}'",
            )
        }

        val query = request.dcqlQuery ?: return invalid(
            code = if (request.scope != null) {
                WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE
            } else {
                WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST
            },
            message = if (request.scope != null) {
                "Scope-based DCQL is not configured for this wallet"
            } else {
                "Authorization Request must contain dcql_query"
            },
        )
        runCatching(query::precheck).exceptionOrNull()?.let { error ->
            return invalid(
                WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
                error.message ?: "Invalid dcql_query",
            )
        }

        val requestedFormats = query.credentials
            .mapNotNull { credentialQuery -> WalletPresentationFormatRegistry.resolve(credentialQuery.format.id.first()) }
            .toSet()
        val verifierFormats = request.clientMetadata?.vpFormatsSupported
        val walletSupportsRequestedFormat = requestedFormats.any(formatCapabilities.supportedFormats::contains)
        val verifierSupportsRequestedFormat = verifierFormats?.let {
            WalletPresentationFormatRegistry.supportsAny(
                verifierFormats = it,
                capabilities = formatCapabilities,
                requestedFormats = requestedFormats,
            )
        } ?: true
        if (!walletSupportsRequestedFormat || !verifierSupportsRequestedFormat) {
            return invalid(
                WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED,
                "The wallet supports none of the presentation formats requested by the verifier",
            )
        }

        val transactionData = runCatching {
            validateRequestTransactionData(
                transactionData = request.transactionData,
                typeRegistry = transactionDataTypeRegistry,
                credentialQueriesById = query.credentials.associateBy { it.id },
            )
        }.getOrElse { error ->
            return invalid(
                WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA,
                error.message ?: "Invalid transaction_data",
            )
        }

        return PresentationRequestValidationResult.Valid(transactionData)
    }

    /**
     * Validates the wallet-dependent part of `transaction_data` after credential matching.
     * Each transaction item must reference at least one DCQL query the wallet can satisfy.
     */
    fun validateTransactionDataCredentialAvailability(
        transactionData: List<DecodedTransactionData>,
        availableCredentialQueryIds: Set<String>,
    ): PresentationRequestError? = transactionData
        .firstOrNull { decoded ->
            decoded.transactionData.credentialIds.none(availableCredentialQueryIds::contains)
        }
        ?.let {
            PresentationRequestError(
                code = WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA,
                message = "The transaction_data references no credential available in the wallet",
            )
        }

    /** Validates whether the wallet can satisfy every required DCQL credential combination. */
    fun validateCredentialAvailability(
        query: DcqlQuery,
        availableCredentialQueryIds: Set<String>,
    ): PresentationRequestError? {
        val requirementsSatisfied = query.credentialSets
            ?.takeIf { it.isNotEmpty() }
            ?.filter { it.required }
            ?.all { set ->
                set.options.any { option ->
                    option.isNotEmpty() && option.all(availableCredentialQueryIds::contains)
                }
            }
            ?: query.credentials.all { it.id in availableCredentialQueryIds }

        return if (requirementsSatisfied) {
            null
        } else {
            PresentationRequestError(
                code = WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED,
                message = "The wallet has no credential combination that satisfies this request",
            )
        }
    }

    private fun requireDispatchableResponse(resolvedRequest: ResolvedAuthorizationRequest) {
        val request = resolvedRequest.authorizationRequest
        require(!request.clientId.isNullOrBlank()) {
            "Authorization Request client_id is required; an error response cannot be sent safely"
        }
        when (request.walletResponseMode()) {
            OpenID4VPResponseMode.FRAGMENT,
            OpenID4VPResponseMode.QUERY,
            OpenID4VPResponseMode.FORM_POST,
            -> require(!request.redirectUri.isNullOrBlank()) {
                "Authorization Request redirect_uri is required; an error response cannot be sent safely"
            }

            OpenID4VPResponseMode.DIRECT_POST,
            OpenID4VPResponseMode.DIRECT_POST_JWT,
            -> {
                require(!request.responseUri.isNullOrBlank()) {
                    "Authorization Request response_uri is required; an error response cannot be sent safely"
                }
                require(request.redirectUri == null) {
                    "redirect_uri must not be present with response_mode=${request.responseMode}"
                }
            }

            OpenID4VPResponseMode.DC_API,
            OpenID4VPResponseMode.DC_API_JWT,
            -> throw UnsupportedOperationException("Digital Credentials API response modes are not supported by this wallet flow")

            null -> throw IllegalArgumentException(
                "Authorization Request response mode cannot be determined; an error response cannot be sent safely",
            )
        }

        if (resolvedRequest is ResolvedAuthorizationRequest.Plain) {
            val redirectUri = request.redirectUri
            require(redirectUri != null && request.clientId == "redirect_uri:$redirectUri") {
                "A plain Authorization Request must bind client_id to redirect_uri before an error response can be sent safely"
            }
        }
    }

    private fun invalid(
        code: WalletPresentFunctionality2.OID4VPErrorCode,
        message: String,
    ): PresentationRequestValidationResult.Invalid =
        PresentationRequestValidationResult.Invalid(PresentationRequestError(code, message))

    private val supportedResponseTypes = setOf(
        OpenID4VPResponseType.VP_TOKEN,
        OpenID4VPResponseType.VP_TOKEN_ID_TOKEN,
    )
}

internal fun AuthorizationRequest.walletResponseMode(): OpenID4VPResponseMode? =
    responseMode ?: when (responseType) {
        OpenID4VPResponseType.VP_TOKEN,
        OpenID4VPResponseType.VP_TOKEN_ID_TOKEN,
        -> OpenID4VPResponseMode.FRAGMENT

        OpenID4VPResponseType.CODE -> OpenID4VPResponseMode.QUERY
        null -> null
    }
