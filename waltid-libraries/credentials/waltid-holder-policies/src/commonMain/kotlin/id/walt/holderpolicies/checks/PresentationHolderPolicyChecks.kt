package id.walt.holderpolicies.checks

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.formats.DigitalCredential
import id.walt.holderpolicies.HolderPolicyDisclosedCredential
import id.walt.holderpolicies.HolderPolicyTransactionData
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class HolderPolicyMatchQuantifier {
    @SerialName("any")
    ANY,

    @SerialName("all")
    ALL;

    internal fun evaluate(matches: Sequence<HolderPolicyMatch>): HolderPolicyMatch {
        val decisiveMatch = if (this == ANY) HolderPolicyMatch.MATCH else HolderPolicyMatch.NO_MATCH
        var result: HolderPolicyMatch? = null
        for (match in matches) {
            if (match == decisiveMatch) return match
            if (result == null || match == HolderPolicyMatch.UNAVAILABLE) result = match
        }
        return result ?: HolderPolicyMatch.NO_MATCH
    }
}

internal enum class HolderPolicyMatch {
    MATCH,
    NO_MATCH,
    UNAVAILABLE,
}

/** Checks the projected claims of selected credentials. The scope is a presentation query ID. */
@Serializable
@SerialName("disclosed_claims")
data class DisclosedClaimsHolderPolicyCheck(
    val match: HolderPolicyMatchQuantifier,
    @SerialName("credential_id")
    val credentialId: String? = null,
    @SerialName("claims_present")
    val claimsPresent: List<String> = emptyList(),
    @SerialName("claims_values")
    val claimsValues: Map<String, JsonElement> = emptyMap(),
) : HolderPolicyCheck {
    init {
        require(credentialId == null || credentialId.isNotBlank()) { "credential_id must be nonblank" }
        require(claimsPresent.isNotEmpty() || claimsValues.isNotEmpty()) {
            "A disclosed_claims check requires claims_present or claims_values"
        }
    }

    @Transient
    private val fields = JsonFieldChecks(claimsPresent, claimsValues)

    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean = false

    internal fun matchesDisclosures(disclosures: List<HolderPolicyDisclosedCredential>?): HolderPolicyMatch {
        if (disclosures == null) return HolderPolicyMatch.UNAVAILABLE
        return match.evaluate(disclosures.asSequence()
            .filter { credentialId == null || it.credentialId == credentialId }
            .map { credential -> credential.claims?.let(fields::matches) ?: HolderPolicyMatch.UNAVAILABLE })
    }
}

/** Checks the type and type-specific fields of validated presentation transaction data. */
@Serializable
@SerialName("transaction_data")
data class TransactionDataHolderPolicyCheck(
    val match: HolderPolicyMatchQuantifier,
    @SerialName("credential_id")
    val credentialId: String? = null,
    @SerialName("transaction_type")
    val transactionType: String? = null,
    @SerialName("fields_present")
    val fieldsPresent: List<String> = emptyList(),
    @SerialName("fields_values")
    val fieldsValues: Map<String, JsonElement> = emptyMap(),
) : HolderPolicyCheck {
    init {
        require(credentialId == null || credentialId.isNotBlank()) { "credential_id must be nonblank" }
        require(transactionType == null || transactionType.isNotBlank()) { "transaction_type must be nonblank" }
    }

    @Transient
    private val fields = JsonFieldChecks(fieldsPresent, fieldsValues)

    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean = false

    internal fun matchesTransactions(transactions: List<HolderPolicyTransactionData>?): HolderPolicyMatch {
        if (transactions == null) return HolderPolicyMatch.UNAVAILABLE
        return match.evaluate(transactions.asSequence()
            .filter { credentialId == null || credentialId in it.credentialIds }
            .map { transaction ->
                if (transactionType == null || transactionType == transaction.type) fields.matches(transaction.data)
                else HolderPolicyMatch.NO_MATCH
            })
    }
}

private class JsonFieldChecks(present: List<String>, values: Map<String, JsonElement>) {
    private val presencePaths = present.map { compile(it) }
    private val valuePaths = values.map { (path, expected) -> compile(path) to expected }

    fun matches(data: JsonObject): HolderPolicyMatch {
        val matches = presencePaths.all { it.resolveOrNull(data) != null } &&
            valuePaths.all { (path, expected) -> path.resolveOrNull(data) == expected }
        return if (matches) HolderPolicyMatch.MATCH else HolderPolicyMatch.NO_MATCH
    }

    private fun compile(path: String): JsonPath {
        require(path.isNotBlank()) { "Policy JSONPath must be nonblank" }
        return JsonPath.compile(path)
    }
}
