@file:UseSerializers(JsonObjectKtorSerializer::class, JsonElementKtorSerializer::class)

package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal const val WalletApi2DefaultClientId = "eudiw-abca"

@Serializable
internal data class EmailPasswordRequest(
    val email: String,
    val password: String,
)

@Serializable
internal data class CreateWalletRequestDto(
    val keyStoreIds: List<String>? = null,
    val credentialStoreIds: List<String>? = null,
    val didStoreId: String? = null,
)

@Serializable
internal data class PersistedAuthorizationIssuance(
    val id: String,
    val offerUrl: String,
    val redirectUri: String,
    val did: String? = null,
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val nonceEndpoint: String? = null,
    val codeVerifier: String? = null,
    val authorizationState: String? = null,
    val credentialConfigurationId: String? = null,
)

@Serializable
internal data class RegisterResponse(
    val accountId: String? = null,
)

@Serializable
internal data class AuthSessionResponse(
    @SerialName("session_id")
    val sessionId: String? = null,
    val status: String? = null,
    val token: String? = null,
)

@Serializable
internal data class AccountWalletsResponse(
    val accountId: String? = null,
    val email: String? = null,
    val walletIds: List<String> = emptyList(),
)

@Serializable
internal data class WalletCreatedResponse(
    val walletId: String,
)

@Serializable
internal data class WalletInfoResponse(
    val walletId: String,
    val defaultKeyId: String? = null,
    val defaultDidId: String? = null,
)

@Serializable
internal data class GenerateKeyRequest(
    val backend: String,
    val keyType: String,
)

@Serializable
internal data class WalletKeyInfo(
    val keyId: String,
    val keyType: String? = null,
    val algorithm: String? = null,
)

@Serializable
internal data class CreateDidRequest(
    val method: String,
    val keyId: String? = null,
)

@Serializable
internal data class WalletDidEntry(
    val did: String,
    val document: JsonObject? = null,
)

@Serializable
internal data class StoredCredentialMetadataDto(
    val id: String,
    val format: String,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val addedAt: String? = null,
)

@Serializable
internal data class OfferUrlRequest(
    val offerUrl: String,
)

@Serializable
internal data class ReceiveCredentialRequestDto(
    val offerUrl: String,
    val txCode: String? = null,
    val did: String? = null,
    val clientId: String = WalletApi2DefaultClientId,
    val redirectUri: String? = null,
)

@Serializable
internal data class ReceiveCredentialResultDto(
    val credentialIds: List<String> = emptyList(),
    val deferredTransactionIds: Map<String, String> = emptyMap(),
)

@Serializable
internal data class GenerateAuthorizationUrlRequestDto(
    val offerUrl: String,
    val clientId: String = WalletApi2DefaultClientId,
    val redirectUri: String,
    val usePkce: Boolean = true,
)

@Serializable
internal data class GenerateAuthorizationUrlResultDto(
    val authorizationUrl: String,
    val state: String? = null,
    val codeVerifier: String? = null,
    val credentialConfigurationId: String,
    val credentialIssuerBaseUrl: String,
    val nonceEndpoint: String? = null,
)

@Serializable
internal data class ReceiveAuthorizedCredentialRequestDto(
    val code: String,
    val codeVerifier: String? = null,
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val credentialConfigurationId: String,
    val nonceEndpoint: String? = null,
    val clientId: String = WalletApi2DefaultClientId,
    val redirectUri: String,
    val did: String? = null,
)

@Serializable
internal data class ResolveOfferDetailedResponseDto(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String> = emptyList(),
    val grantType: String? = null,
    val preAuthorizedCode: String? = null,
    val txCodeRequired: Boolean = false,
    val credentialEndpoint: String,
    val tokenEndpoint: String? = null,
    val nonceEndpoint: String? = null,
    val issuer: OfferIssuerMetadataDto,
    val offeredCredentials: List<OfferedCredentialMetadataDto> = emptyList(),
    val transactionCode: OfferTransactionCodeRequirementDto? = null,
)

@Serializable
internal data class OfferIssuerMetadataDto(
    val credentialIssuer: String,
    val display: OfferMetadataDisplayDto? = null,
)

@Serializable
internal data class OfferMetadataDisplayDto(
    val name: String? = null,
    val locale: String? = null,
    val logoUri: String? = null,
    val logoAltText: String? = null,
    val description: String? = null,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
)

@Serializable
internal data class OfferedCredentialMetadataDto(
    val configurationId: String,
    val format: String,
    val scope: String? = null,
    val vct: String? = null,
    val doctype: String? = null,
    val display: OfferMetadataDisplayDto? = null,
    val claims: List<OfferClaimMetadataDto> = emptyList(),
)

@Serializable
internal data class OfferClaimMetadataDto(
    val path: List<String> = emptyList(),
    val mandatory: Boolean? = null,
    val displayName: String? = null,
)

@Serializable
internal data class OfferTransactionCodeRequirementDto(
    val inputMode: String? = null,
    val length: Int? = null,
    val description: String? = null,
)

@Serializable
internal data class PreviewPresentationRequestDto(
    val requestUrl: String,
    val keyId: String? = null,
)

@Serializable
internal data class PresentationPreviewResponseDto(
    val valid: Boolean,
    val keyId: String? = null,
    val clientId: String? = null,
    val verifier: PreviewVerifierMetadataDto? = null,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val responseEncryption: PreviewResponseEncryptionDto? = null,
    val transactionData: List<PreviewTransactionDataItemDto> = emptyList(),
    val credentialOptions: List<PreviewCredentialOptionDto> = emptyList(),
    val credentialRequirements: List<PreviewCredentialRequirementDto> = emptyList(),
    val error: PreviewErrorDto? = null,
)

@Serializable
internal data class PreviewVerifierMetadataDto(
    val name: String? = null,
    val locale: String? = null,
    val logoUri: String? = null,
    val clientUri: String? = null,
    val policyUri: String? = null,
    val termsOfServiceUri: String? = null,
)

@Serializable
internal data class PreviewResponseEncryptionDto(
    val required: Boolean = false,
    val keyManagementAlgorithm: String? = null,
    val contentEncryptionAlgorithm: String? = null,
    val verifierKeyId: String? = null,
    val verifierKeyThumbprint: String? = null,
)

@Serializable
internal data class PreviewTransactionDataItemDto(
    val type: String,
    val credentialQueryIds: List<String> = emptyList(),
    val rawJson: JsonObject,
    val details: JsonObject,
)

@Serializable
internal data class PreviewCredentialOptionDto(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean = false,
    val format: String,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val credentialData: JsonObject,
    val disclosures: List<PreviewCredentialDisclosureDto> = emptyList(),
)

@Serializable
internal data class PreviewCredentialDisclosureDto(
    val path: String,
    val name: String? = null,
    val value: JsonElement,
    val selectivelyDisclosable: Boolean,
    val required: Boolean,
    val selectable: Boolean,
)

@Serializable
internal data class PreviewCredentialRequirementDto(
    val options: List<List<String>> = emptyList(),
)

@Serializable
internal data class PreviewErrorDto(
    val code: String,
    val message: String? = null,
)

@Serializable
internal data class BuildVpTokenRequestDto(
    val requestUrl: String,
    val selectedCredentialOptions: List<CredentialSelectionDto> = emptyList(),
    val selectedDisclosureOptions: List<DisclosureSelectionDto>? = null,
    val keyId: String? = null,
    val did: String? = null,
)

@Serializable
internal data class CredentialSelectionDto(
    val queryId: String,
    val credentialId: String,
)

@Serializable
internal data class DisclosureSelectionDto(
    val queryId: String,
    val credentialId: String,
    val path: String,
)

@Serializable
internal data class BuildVpTokenResultDto(
    val vpToken: String,
    val idToken: String? = null,
)

@Serializable
internal data class SendAuthorizationResponseRequestDto(
    val requestUrl: String,
    val vpToken: String,
    val idToken: String? = null,
)

@Serializable
internal data class RejectPresentationRequestDto(
    val requestUrl: String,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

@Serializable
internal data class PresentCredentialRequestDto(
    val requestUrl: String,
    val did: String? = null,
)

@Serializable
internal data class WalletPresentResultDto(
    @SerialName("get_url")
    val getUrl: String? = null,
    @SerialName("form_post_html")
    val formPostHtml: String? = null,
    @SerialName("transmission_success")
    val transmissionSuccess: Boolean? = null,
    @SerialName("redirect_to")
    val redirectTo: String? = null,
)

@Serializable
internal data class PollDeferredRequestDto(
    val deferredCredentialEndpoint: String,
    val transactionId: String,
    val accessToken: String,
    val credentialIssuerBaseUrl: String? = null,
    val credentialConfigurationId: String? = null,
    val keyId: String? = null,
)
