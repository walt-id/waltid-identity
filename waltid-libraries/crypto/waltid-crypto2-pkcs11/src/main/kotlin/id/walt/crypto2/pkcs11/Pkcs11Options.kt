package id.walt.crypto2.pkcs11

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Pkcs11Options(
    val libraryPath: String,
    val slotListIndex: Int,
    val pinReference: String,
    val alias: String? = null,
) {
    init {
        require(libraryPath.isNotBlank() && '\n' !in libraryPath && '\r' !in libraryPath) {
            "PKCS11 library path is invalid"
        }
        require(slotListIndex >= 0) { "PKCS11 slot-list index cannot be negative" }
        require(pinReference.isNotBlank()) { "PKCS11 PIN reference cannot be blank" }
        require(alias == null || alias.isNotBlank()) { "PKCS11 alias cannot be blank" }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        internal fun decode(data: BinaryData): Pkcs11Options = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

class Pkcs11Pin(value: CharArray) {
    private val value = value.copyOf()

    internal fun copy(): CharArray = value.copyOf()

    override fun toString(): String = "Pkcs11Pin(***)"
}

fun interface Pkcs11PinResolver {
    suspend fun resolve(reference: String): Pkcs11Pin
}
