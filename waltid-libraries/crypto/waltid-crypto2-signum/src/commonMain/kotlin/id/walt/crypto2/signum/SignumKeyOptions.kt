package id.walt.crypto2.signum

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SignumKeyOptions(
    val alias: String? = null,
    val policy: SignumKeyPolicy = SignumKeyPolicy(),
) {
    init {
        require(alias == null || alias.isNotBlank()) { "Signum key alias cannot be blank" }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        internal fun decode(data: BinaryData): SignumKeyOptions = json.decodeFromString(data.toByteArray().decodeToString())
    }
}
