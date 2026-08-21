package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class DemoTransactionDataProfilesResult(
    val profiles: List<MobileWalletTransactionDataProfile>,
    val warning: String? = null,
)

internal suspend fun DemoWalletConfig.resolveDemoTransactionDataProfiles(): DemoTransactionDataProfilesResult {
    val url = transactionDataProfilesUrl.trim()
    if (url.isEmpty()) {
        return transactionDataProfilesUnavailable("transactionDataProfiles.url is not configured")
    }

    return runCatching {
        fetchTransactionDataProfiles(url)
    }.fold(
        onSuccess = { profiles ->
            DemoTransactionDataProfilesResult(profiles = profiles.withTemporaryVerificationPaymentCard())
        },
        onFailure = { error -> transactionDataProfilesUnavailable(error.message ?: error::class.simpleName.orEmpty()) },
    )
}

private suspend fun fetchTransactionDataProfiles(url: String) =
    transactionDataProfilesClient.use { client ->
        client.get(url).body<List<TransactionDataProfileDto>>().map {
            MobileWalletTransactionDataProfile(
                type = it.type,
                displayName = it.displayName,
                fields = it.fields,
            )
        }
    }.takeIf { it.isNotEmpty() } ?: error("No transaction data profiles returned from $url")

private fun transactionDataProfilesUnavailable(reason: String): DemoTransactionDataProfilesResult {
    println("Transaction data profiles unavailable: $reason")
    return DemoTransactionDataProfilesResult(
        profiles = listOf(temporaryVerificationPaymentCardProfile),
        warning = "Remote transaction data profiles could not be loaded; only the local verification payment_card profile is available.",
    )
}

/**
 * Temporary local verification aid. The live request uses type `payment_card` from a
 * different issuer; do not treat this as a product seed. Remove once the loaded
 * profiles already include this type.
 */
private val temporaryVerificationPaymentCardProfile = MobileWalletTransactionDataProfile(
    type = "payment_card",
    displayName = "Payment Card",
    fields = listOf("merchant_name", "amount"),
)

private fun List<MobileWalletTransactionDataProfile>.withTemporaryVerificationPaymentCard(): List<MobileWalletTransactionDataProfile> =
    if (any { it.type == temporaryVerificationPaymentCardProfile.type }) this
    else this + temporaryVerificationPaymentCardProfile

private val transactionDataProfilesClient: HttpClient
    get() = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = TRANSACTION_DATA_PROFILES_TIMEOUT_MS
            connectTimeoutMillis = TRANSACTION_DATA_PROFILES_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = false
                }
            )
        }
    }

@Serializable
private data class TransactionDataProfileDto(
    val type: String,
    val displayName: String,
    val fields: List<String> = emptyList(),
)

private const val TRANSACTION_DATA_PROFILES_TIMEOUT_MS = 3_000L
