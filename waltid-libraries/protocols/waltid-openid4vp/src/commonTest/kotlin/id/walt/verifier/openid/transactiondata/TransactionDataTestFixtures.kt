package id.walt.verifier.openid.transactiondata

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object TransactionDataTestFixtures {
    const val SUPPORTED_TRANSACTION_DATA_TYPE = DEMO_TRANSACTION_DATA_TYPE

    fun transactionData(
        type: String = SUPPORTED_TRANSACTION_DATA_TYPE,
        credentialIds: List<String> = listOf("payment_credential"),
        amount: String = "42.00",
        payee: String = "ACME Corp",
        hashAlgorithms: List<String>? = null,
        requireCryptographicHolderBinding: Boolean = true,
    ): String = buildJsonObject {
        put("type", type)
        put("credential_ids", buildJsonArray {
            credentialIds.forEach { add(JsonPrimitive(it)) }
        })
        put("require_cryptographic_holder_binding", requireCryptographicHolderBinding)
        if (hashAlgorithms != null) {
            put("transaction_data_hashes_alg", buildJsonArray {
                hashAlgorithms.forEach { add(JsonPrimitive(it)) }
            })
        }
        put("amount", amount)
        put("payee", payee)
        put("currency", "EUR")
    }.toString().encodeToByteArray().encodeToBase64Url()

    fun transactionDataWithoutHolderBindingRequirement(
        type: String = SUPPORTED_TRANSACTION_DATA_TYPE,
        credentialId: String = "payment_credential",
    ): String = buildJsonObject {
        put("type", type)
        put("credential_ids", buildJsonArray { add(JsonPrimitive(credentialId)) })
        put("amount", "42.00")
        put("payee", "ACME Corp")
        put("currency", "EUR")
    }.toString().encodeToByteArray().encodeToBase64Url()

    fun sdJwtCredentialQueries(requireCryptographicHolderBinding: Boolean = true): Map<String, CredentialQuery> = mapOf(
        "payment_credential" to CredentialQuery(
            id = "payment_credential",
            format = CredentialFormat.DC_SD_JWT,
            meta = SdJwtVcMeta(vctValues = listOf("https://issuer.example/payment_credential")),
            requireCryptographicHolderBinding = requireCryptographicHolderBinding,
        ),
    )
}
