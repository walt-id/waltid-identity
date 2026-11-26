@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.presentation

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.consent.PaymentCredentialIssuer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Rules for authenticating OpenID4VP verifiers and interpreting transaction data during consent.
 * Collection inputs are captured on construction. The supplied X.509 trust store remains host-owned.
 * To replace a transaction profile, supply a new section with one entry for its type.
 */
public class MobileWalletPresentationConfig(
    verifierTrust: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    paymentCredentialIssuers: List<PaymentCredentialIssuer> = emptyList(),
) {
    /** Wallet-owned client metadata and the host's verifier trust anchors. */
    public val verifierTrust: ClientIdTrustConfiguration
        get() = ClientIdTrustConfiguration(
            x509TrustAnchors = anchors,
            trustedVerifierAttestationIssuers = attestationIssuers,
            preRegisteredClients = registeredMetadata.mapValues { (_, metadata) -> Json.decodeFromString(metadata) },
        )

    private val anchors = verifierTrust.x509TrustAnchors
    private val attestationIssuers = buildSet { addAll(verifierTrust.trustedVerifierAttestationIssuers) }
    // Pre-registered metadata contains nested caller-owned lists and JSON objects.
    private val registeredMetadata = verifierTrust.preRegisteredClients.mapValues { (_, metadata) ->
        Json.encodeToString(metadata)
    }

    /** Profiles accepted by this wallet, each with a unique type and field names. */
    public val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = buildList {
        for (profile in transactionDataProfiles) {
            require(profile.type.isNotBlank() && profile.displayName.isNotBlank()) {
                "Transaction profile type and name must be nonblank"
            }
            val fields = buildList { addAll(profile.fields) }
            require(fields.none(String::isBlank) && fields.distinct().size == fields.size) {
                "Transaction profile fields must be nonblank and unique"
            }
            add(profile.copy(fields = fields))
        }
        require(distinctBy { it.type }.size == size) { "Transaction profile types must be unique" }
    }

    /** Independent issuer verification keys used for payment consent. */
    public val paymentCredentialIssuers: List<PaymentCredentialIssuer> = buildList { addAll(paymentCredentialIssuers) }
}

/**
 * Transaction data profile accepted by the mobile wallet.
 *
 * Wallet apps should keep this list aligned with the ecosystem or service they trust.
 * Requests containing transaction data with a type outside this list are rejected before
 * the user can submit a presentation.
 *
 * @property type Collision-resistant OpenID4VP `transaction_data.type` value.
 * @property displayName Human-readable label for consent UI.
 * @property fields Supported transaction type-specific fields.
 */
public data class MobileWalletTransactionDataProfile(
    public val type: String,
    public val displayName: String = type,
    public val fields: List<String> = emptyList(),
)

internal fun List<MobileWalletTransactionDataProfile>.toTransactionDataTypeRegistry(): TransactionDataTypeRegistry =
    TransactionDataTypeRegistry(map { it.type }.toSet())
