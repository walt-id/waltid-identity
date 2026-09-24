@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.itb

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.time.Instant

/** Session-correlated inputs supplied by the test bed, never replacement reference-service requests. */
sealed interface ItbWalletInteraction {
    class Offer(val url: Url, val transactionCode: String? = null) : ItbWalletInteraction
    class Presentation(val url: Url) : ItbWalletInteraction
    class DigitalCredentials(
        val endpoint: Url,
        val validationSession: String,
        val profile: String,
        val protocol: String,
        val payment: Payment? = null,
    ) : ItbWalletInteraction

    data class Payment(
        val attestationType: String, val merchant: String, val payeeId: String,
        val currency: String, val amount: String, val transactionId: String,
    )
}

class ItbWalletRejection(val code: String) : IllegalStateException("The wallet rejected the presentation request ($code)")

/**
 * Exercises the shared wallet with an explicitly supplied holder and credential store.
 * The portal supplies offers/requests; a reference-issuer callback completes authorization without implementing wallet cryptography.
 * Digital Credentials execution is a protocol bridge, not evidence of native platform delivery or consent.
 */
class ItbWalletDriver internal constructor(
    private val wallet: Wallet,
    private val client: HttpClient,
    private val trustedOrigin: Url,
    private val clientIdTrust: ClientIdTrustConfiguration,
    private val authorize: suspend (Url, Url) -> Url,
    private val scaAuthorizer: WalletScaPresentationAuthorizer? = null,
) {
    private val paymentTypes = TransactionDataTypeRegistry("urn:eudi:sca:payment:1")
    private val redirectUri = Url("openid://")

    suspend fun execute(interaction: ItbWalletInteraction) {
        when (interaction) {
            is ItbWalletInteraction.Offer -> receive(interaction)
            is ItbWalletInteraction.Presentation -> present(interaction)
            is ItbWalletInteraction.DigitalCredentials -> presentDigitalCredentials(interaction)
        }
    }

    private suspend fun receive(offer: ItbWalletInteraction.Offer) {
        val resolved = WalletIssuanceHandler.resolveOffer(ResolveOfferRequest(offerUrl = offer.url))
        val result = if (resolved.grantType == "authorization_code") {
            val authorization = WalletIssuanceHandler.generateAuthorizationUrl(
                wallet, GenerateAuthorizationUrlRequest(offerUrl = offer.url, redirectUri = redirectUri),
            )
            val callback = authorize(authorization.authorizationUrl, redirectUri)
            require(callback.protocol == redirectUri.protocol && callback.host == redirectUri.host) {
                "Unexpected authorization callback destination"
            }
            require(callback.parameters.getAll("state") == listOf(authorization.state)) {
                "Authorization callback state does not match the wallet request"
            }
            require(callback.parameters["error"] == null) { "Issuer rejected authorization" }
            val code = callback.parameters.getAll("code")?.singleOrNull()
                ?: error("Authorization callback has no unique code")
            WalletIssuanceHandler.receiveCredentialAuthCode(
                wallet,
                ReceiveAuthorizedCredentialRequest(
                    code = code, codeVerifier = authorization.codeVerifier,
                    credentialIssuer = authorization.credentialIssuerBaseUrl,
                    credentialEndpoint = resolved.credentialEndpoint,
                    credentialConfigurationId = authorization.credentialConfigurationId,
                    nonceEndpoint = authorization.nonceEndpoint, redirectUri = redirectUri,
                ),
                httpClient = client,
            )
        } else {
            require(resolved.grantType == "pre-authorized_code") {
                "The deployed offer has an unsupported grant"
            }
            require(!resolved.txCodeRequired || !offer.transactionCode.isNullOrBlank()) {
                "The deployed offer requires its transaction code"
            }
            WalletIssuanceHandler.receiveCredential(
                wallet, ReceiveCredentialRequest(offerUrl = offer.url, txCode = offer.transactionCode),
                httpClient = client,
            )
        }
        check(result.credentialIds.isNotEmpty() && result.deferredTransactionIds.isEmpty()) {
            "The wallet did not store the issued credential"
        }
    }

    private suspend fun present(interaction: ItbWalletInteraction.Presentation) {
        val preview = WalletPresentationHandler.previewPresentationWithTrust(
            wallet, PreviewPresentationRequest(interaction.url),
            transactionDataTypeRegistry = paymentTypes, clientIdTrustConfiguration = clientIdTrust,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        )
        usePreview(discard = { WalletPresentationHandler.discardPreview(wallet, preview.handle) }) {
            if (preview is PreviewPresentationResult.Invalid) throw ItbWalletRejection(preview.error.code.code)
            check(preview is PreviewPresentationResult.Ready)
            val selection = select(preview.credentialOptions, preview.credentialRequirements)
            val result = WalletPresentationHandler.submitPresentation(
                wallet, SubmitPresentationRequest(preview.handle, selection),
                transactionDataTypeRegistry = paymentTypes,
                scaAuthorizer = scaAuthorizer,
            )
            check(result.transmissionSuccess == true) {
                val category = verifierErrorCategory(result.verifierResponse)
                "The wallet did not transmit the presentation successfully" +
                    (category?.let { " (verifier error: $it)" } ?: "")
            }
        }
    }

    private suspend fun presentDigitalCredentials(interaction: ItbWalletInteraction.DigitalCredentials) {
        requireSameOrigin(interaction.endpoint)
        val response = client.post(interaction.endpoint) {
            header(HttpHeaders.Origin, trustedOrigin.toString().trimEnd('/'))
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("profile", interaction.profile)
                put("sessionId", interaction.validationSession)
                interaction.payment?.let {
                    put("attestation_type", it.attestationType)
                    put("merchant", it.merchant)
                    put("payee_id", it.payeeId)
                    put("currency", it.currency)
                    put("amount", it.amount)
                    put("transaction_id", it.transactionId)
                }
            }.toString())
        }
        check(response.status.isSuccess()) { "The ITB DC API descriptor endpoint rejected the request" }
        val descriptor = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        require(descriptor.getValue("sessionId").jsonPrimitive.content == interaction.validationSession) {
            "The DC API descriptor belongs to another validation session"
        }
        val request = descriptor.getValue("request").jsonObject
        require(request.getValue("protocol").jsonPrimitive.content == interaction.protocol) {
            "The DC API descriptor changed protocol"
        }
        require(descriptorExpiry(descriptor.getValue("expiresAt")).isAfter(Instant.now())) {
            "The DC API descriptor expired before wallet execution"
        }
        val responseEndpoint = Url(descriptor.getValue("responseEndpoint").jsonPrimitive.content)
        requireSameOrigin(responseEndpoint)
        val preview = WalletPresentationHandler.previewDcApiPresentation(
            wallet,
            PreviewDcApiPresentationRequest(
                interaction.protocol, request.getValue("data").jsonObject,
                trustedOrigin.toString().trimEnd('/'),
            ),
            transactionDataTypeRegistry = paymentTypes,
            clientIdTrustConfiguration = clientIdTrust,
        )
        val credential = WalletPresentationHandler.submitDcApiPresentation(
            wallet, SubmitDcApiPresentationRequest(
                preview.requestId, select(preview.credentialOptions, preview.credentialRequirements),
            ),
            transactionDataTypeRegistry = paymentTypes,
            scaAuthorizer = scaAuthorizer,
        )
        val submitted = client.post(responseEndpoint) {
            header(HttpHeaders.Origin, trustedOrigin.toString().trimEnd('/'))
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("protocol", credential.protocol); put("data", credential.data) }.toString())
        }
        check(submitted.status.isSuccess()) { "The ITB DC API response endpoint rejected the wallet response" }
    }

    private fun requireSameOrigin(url: Url) {
        require(url.protocol == trustedOrigin.protocol && url.host == trustedOrigin.host && url.port == trustedOrigin.port &&
            url.user == null && url.password == null) { "The ITB descriptor endpoint is outside the configured origin" }
    }

    companion object {
        /** Retain only a bounded protocol error identifier, never the verifier's response body. */
        internal fun verifierErrorCategory(response: JsonElement?): String? =
            ((response as? JsonObject)?.get("error") as? JsonPrimitive)
                ?.takeIf { it.isString && it.content == "missing_sca_credential" }
                ?.content

        /** Preserve the presentation failure if releasing its preview also fails. */
        internal suspend fun usePreview(discard: suspend () -> Unit, present: suspend () -> Unit) {
            val execution = runCatching { present() }
            val cleanupFailure = runCatching { discard() }.exceptionOrNull()?.takeUnless {
                // Submission consumes the handle even when transmission fails.
                it is PreviewSessionException && it.reason == PreviewSessionFailureReason.CONSUMED
            }
            execution.exceptionOrNull()?.let { failure ->
                cleanupFailure?.let(failure::addSuppressed)
                throw failure
            }
            cleanupFailure?.let { throw it }
        }

        internal fun descriptorExpiry(value: JsonElement): Instant {
            val primitive = value.jsonPrimitive
            require(!primitive.isString && primitive.longOrNull != null) { "The ITB descriptor expiry must be Unix seconds" }
            return Instant.ofEpochSecond(primitive.long)
        }

        /** Pick a complete advertised alternative for each required credential set, preserving wallet matching. */
        internal fun select(
            options: List<PresentationCredentialOption>,
            requirements: List<PresentationCredentialRequirement>,
        ): List<PresentationCredentialSelection> {
            val byQuery = options.groupBy { it.queryId }
            val queries = requirements.flatMap { requirement ->
                requirement.options.firstOrNull { alternative -> alternative.all { it in byQuery } }
                    ?: error("No stored credentials satisfy an ITB presentation requirement")
            }.distinct()
            check(queries.isNotEmpty()) { "The ITB request selected no credential requirements" }
            return queries.map { query -> PresentationCredentialSelection(query, byQuery.getValue(query).first().credentialId) }
        }
    }
}
