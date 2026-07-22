package id.walt.crypto2.kms.aws.sdk

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AwsKmsSdkOptions(
    val primaryRegion: String,
    val alias: String? = null,
    val description: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val multiRegion: Boolean = false,
    val replicaRegions: List<String> = emptyList(),
    val failoverOrder: List<String>? = null,
) {
    init {
        require(primaryRegion.isNotBlank()) { "AWS primary region cannot be blank" }
        require(alias == null || alias.isNotBlank()) { "AWS alias cannot be blank" }
        require(description == null || description.isNotBlank()) { "AWS description cannot be blank" }
        require(replicaRegions.none(String::isBlank)) { "AWS replica region cannot be blank" }
        require(replicaRegions.distinct().size == replicaRegions.size) { "AWS replica regions cannot contain duplicates" }
        require(primaryRegion !in replicaRegions) { "AWS replica regions cannot contain the primary region" }
        require(multiRegion || replicaRegions.isEmpty()) { "AWS replica regions require a multi-region key" }
        failoverOrder?.let { order ->
            require(order.isNotEmpty() && order.none(String::isBlank)) { "AWS failover order cannot be empty or blank" }
            require(order.distinct().size == order.size) { "AWS failover order cannot contain duplicates" }
            require(order.all { it == primaryRegion || it in replicaRegions }) {
                "AWS failover order contains a region without a configured key"
            }
        }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    internal fun operationRegions(): List<String> = failoverOrder ?: listOf(primaryRegion) + replicaRegions

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        internal fun decode(data: BinaryData): AwsKmsSdkOptions = json.decodeFromString(data.toByteArray().decodeToString())
    }
}
