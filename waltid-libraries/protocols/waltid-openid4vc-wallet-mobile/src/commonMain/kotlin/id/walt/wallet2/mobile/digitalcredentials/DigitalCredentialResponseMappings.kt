@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.digitalcredentials

import kotlinx.serialization.ExperimentalSerializationApi
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.mobile.presentation.PresentationTransactionData
import id.walt.wallet2.mobile.presentation.toMobileVerifierMetadata
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun DcApiCredentialResponse.toMobileDigitalCredentialResponse(): DigitalCredentialResponse =
    DigitalCredentialResponse(
        protocol = protocol,
        dataJson = Json.encodeToString(JsonObject.serializer(), data),
    )

internal fun AuthorizationRequest.toMobileDigitalCredentialRequestInfo(
    preferredLocales: List<String>,
    transactionData: List<PresentationTransactionData> = emptyList(),
): DigitalCredentialRequestInfo =
    DigitalCredentialRequestInfo(
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
