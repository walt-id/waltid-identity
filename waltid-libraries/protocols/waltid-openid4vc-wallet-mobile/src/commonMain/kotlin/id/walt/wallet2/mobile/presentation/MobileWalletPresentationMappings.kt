@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.presentation

import id.walt.openid4vp.clientidprefix.prefixes.ClientId
import id.walt.openid4vp.clientidprefix.prefixes.DecentralizedIdentifier
import id.walt.openid4vp.clientidprefix.prefixes.OpenIdFederation
import id.walt.openid4vp.clientidprefix.prefixes.PreRegistered
import id.walt.openid4vp.clientidprefix.prefixes.RedirectUri
import id.walt.openid4vp.clientidprefix.prefixes.Unsupported
import id.walt.openid4vp.clientidprefix.prefixes.VerifierAttestation
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.handlers.PreparedPresentationReview
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialRequestInfo
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialResponse
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun PresentationCredentialOption.toMobileCredentialOption(): MobileWalletPresentationCredentialOption =
    MobileWalletPresentationCredentialOption(
        queryId = queryId,
        credentialId = credentialId,
        multiple = multiple,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        credentialDataJson = credentialData.encodeJsonObject(),
        disclosures = disclosures.map { disclosure ->
            MobileWalletPresentationDisclosure(
                path = disclosure.path,
                name = disclosure.name,
                valueJson = Json.encodeToString(JsonElement.serializer(), disclosure.value),
                displayValue = disclosure.value.displayValue(),
                disposition = disclosureDisposition(disclosure.selectivelyDisclosable, disclosure.required, disclosure.selectable, disclosure.requested),
            )
        },
        metadataJson = metadata?.encodeJsonObject(),
    )

internal fun PresentationCredentialRequirement.toMobileCredentialRequirement(): MobileWalletPresentationCredentialRequirement =
    MobileWalletPresentationCredentialRequirement(options = options)

internal fun DcApiCredentialResponse.toMobileDigitalCredentialResponse(): MobileWalletDigitalCredentialResponse =
    MobileWalletDigitalCredentialResponse(
        protocol = protocol,
        dataJson = Json.encodeToString(JsonObject.serializer(), data),
    )

internal fun JsonObject.encodeJsonObject(): String =
    Json.encodeToString(JsonObject.serializer(), this)

internal fun WalletPresentResult.toMobilePresentationResult(): MobileWalletPresentationResult =
    verifierResponse?.let { Json.encodeToString(JsonElement.serializer(), it) }.let { responseJson ->
        val responseUrl = getUrl
        val formHtml = formPostHtml
        when {
            responseUrl != null -> {
                require(
                    transmissionSuccess == null &&
                        formHtml == null &&
                        responseJson == null &&
                        redirectTo == null
                ) {
                    "Prepared URL result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.OpenUrl(responseUrl)
            }

            formHtml != null -> {
                require(transmissionSuccess == null && responseJson == null && redirectTo == null) {
                    "Prepared form result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.SubmitForm(formHtml)
            }

            transmissionSuccess == true -> MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Transmitted presentation result is missing the verifier response"
                },
                redirectUrl = redirectTo,
            )

            transmissionSuccess == false -> MobileWalletPresentationResult.Transmitted.Failed(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Failed presentation transmission is missing the verifier response"
                },
            )

            else -> error("Presentation result has no protocol outcome")
        }
    }

internal fun AuthorizationRequest.toMobileRequestInfo(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
    responseEncryption: ResponseEncryption.Metadata? = null,
    transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
): MobileWalletPresentationRequestInfo {
    val verifiedClientId = requireNotNull(clientId) {
            "A validated presentation request must contain client_id."
        }
    return MobileWalletPresentationRequestInfo(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toMobileRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = requireNotNull(nonce) {
            "A validated presentation request must contain nonce."
        },
        responseEncryption = responseEncryption.toMobileResponseEncryption(),
        transactionData = transactionData,
        responseMode = responseMode?.let { mode ->
            Json.encodeToString(OpenID4VPResponseMode.serializer(), mode).trim('"')
        },
    )
}

internal fun AuthorizationRequest.toMobileDigitalCredentialRequestInfo(
    preferredLocales: List<String>,
    transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
): MobileWalletDigitalCredentialRequestInfo =
    MobileWalletDigitalCredentialRequestInfo(
        clientId = clientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        nonce = requireNotNull(nonce) {
            "A validated Digital Credentials request must contain nonce."
        },
        responseMode = responseMode?.let { mode ->
            Json.encodeToString(OpenID4VPResponseMode.serializer(), mode).trim('"')
        },
        transactionData = transactionData,
    )

internal fun AuthorizationRequest.toMobileRequestContext(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
): MobileWalletPresentationRequestContext {
    val verifiedClientId = requireNotNull(clientId) {
            "A reportable invalid presentation request must contain client_id."
        }
    return MobileWalletPresentationRequestContext(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toMobileRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = nonce,
        responseEncryption = null.toMobileResponseEncryption(),
    )
}

internal fun ResolvedAuthorizationRequest.toMobileRequestAuthentication(): MobileWalletRequestAuthentication =
    when (this) {
        is ResolvedAuthorizationRequest.Plain -> MobileWalletRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.UnsignedRequestObject -> MobileWalletRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.AuthenticatedRequestObject -> MobileWalletRequestAuthentication.Authenticated(
            compactRequestObject = requestObject,
            algorithm = authentication.algorithm,
            keyId = authentication.keyId,
            clientIdScheme = authentication.clientId.toMobileClientIdScheme(),
        )
    }

internal fun WalletPresentFunctionality2.OID4VPErrorCode.toMobileErrorCode(): MobileWalletPresentationErrorCode = when (this) {
    WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED -> MobileWalletPresentationErrorCode.accessDenied
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST -> MobileWalletPresentationErrorCode.invalidRequest
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_CLIENT -> MobileWalletPresentationErrorCode.invalidClient
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE -> MobileWalletPresentationErrorCode.invalidScope
    WalletPresentFunctionality2.OID4VPErrorCode.UNAUTHORIZED_CLIENT -> MobileWalletPresentationErrorCode.unauthorizedClient
    WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE -> MobileWalletPresentationErrorCode.unsupportedResponseType
    WalletPresentFunctionality2.OID4VPErrorCode.SERVER_ERROR -> MobileWalletPresentationErrorCode.serverError
    WalletPresentFunctionality2.OID4VPErrorCode.TEMPORARILY_UNAVAILABLE -> MobileWalletPresentationErrorCode.temporarilyUnavailable
    WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED -> MobileWalletPresentationErrorCode.vpFormatsNotSupported
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST_URI_METHOD -> MobileWalletPresentationErrorCode.invalidRequestUriMethod
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA -> MobileWalletPresentationErrorCode.invalidTransactionData
    WalletPresentFunctionality2.OID4VPErrorCode.WALLET_UNAVAILABLE -> MobileWalletPresentationErrorCode.walletUnavailable
}

internal fun PreparedPresentationReview.toMobileReview() =
    MobileWalletPreparedPresentationReview(credentials.map {
        MobileWalletPresentationCredentialClaims(it.queryId, it.credentialId, it.format, it.claims.toString(), it.wholeCredential,
            it.locations.map { location -> MobileWalletPresentationClaimLocation(
                JsonArray(location.sourcePath).toString(),
                JsonArray(location.receivedPath).toString(), location.sharedBy, location.alwaysCarried, location.isBinary) })
    })

private fun JsonElement.displayValue(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }



private fun ClientId.toMobileClientIdScheme(): MobileWalletClientIdScheme = when (this) {
    is PreRegistered -> MobileWalletClientIdScheme.PRE_REGISTERED
    is RedirectUri -> MobileWalletClientIdScheme.REDIRECT_URI
    is X509SanDns -> MobileWalletClientIdScheme.X509_SAN_DNS
    is X509Hash -> MobileWalletClientIdScheme.X509_HASH
    is DecentralizedIdentifier -> MobileWalletClientIdScheme.DECENTRALIZED_IDENTIFIER
    is VerifierAttestation -> MobileWalletClientIdScheme.VERIFIER_ATTESTATION
    is OpenIdFederation -> MobileWalletClientIdScheme.OPENID_FEDERATION
    is Unsupported -> error("Unsupported client identifier cannot be authenticated: $prefix")
}
