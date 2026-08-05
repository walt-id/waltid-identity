package id.walt.crypto2.pkcs11

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.security.auth.DestroyFailedException
import javax.security.auth.Destroyable

/**
 * Location of a key on a PKCS#11 token.
 *
 * Token addressing is deliberately offered in both forms PKCS#11 defines, because deployments differ:
 * - [slotId] is the `CK_SLOT_ID` and is what HSM operators work with. A Thales Luna partition and a tpm2-pkcs11
 *   token both have a stable slot ID, whereas their position in the slot list can move when other tokens appear.
 * - [slotListIndex] is the position in the library's slot list. It is convenient for a single-token library such as
 *   SoftHSM, where the ID is generated at initialisation time and therefore not known in advance.
 *
 * Exactly one must be given. SunPKCS11 accepts both natively (`slot` / `slotListIndex`), so neither requires
 * enumerating the token ourselves.
 */
@Serializable
data class Pkcs11Options(
    val libraryPath: String,
    val pinReference: String,
    val slotId: Long? = null,
    val slotListIndex: Int? = null,
    val alias: String? = null,
    /**
     * Extra SunPKCS11 configuration lines, appended verbatim. An escape hatch for vendor requirements that have no
     * portable equivalent - for example a Luna `attributes(...)` override, or NSS arguments. Newlines are rejected
     * per line so a value cannot inject unrelated directives.
     */
    val providerConfigurationLines: List<String> = emptyList(),
) {
    init {
        require(libraryPath.isNotBlank() && '\n' !in libraryPath && '\r' !in libraryPath) {
            "PKCS11 library path is invalid"
        }
        require((slotId == null) != (slotListIndex == null)) {
            "Exactly one of PKCS11 slotId or slotListIndex must be set"
        }
        require(slotId == null || slotId >= 0) { "PKCS11 slot ID cannot be negative" }
        require(slotListIndex == null || slotListIndex >= 0) { "PKCS11 slot-list index cannot be negative" }
        require(pinReference.isNotBlank()) { "PKCS11 PIN reference cannot be blank" }
        require(alias == null || alias.isNotBlank()) { "PKCS11 alias cannot be blank" }
        providerConfigurationLines.forEach { line ->
            require(line.isNotBlank() && '\n' !in line && '\r' !in line) {
                "PKCS11 provider configuration line is invalid: $line"
            }
        }
    }

    /** Identifies the token these options address, so sessions can be shared per token rather than per key. */
    internal val tokenId: TokenId get() = TokenId(libraryPath, slotId, slotListIndex)

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        internal fun decode(data: BinaryData): Pkcs11Options = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

internal data class TokenId(val libraryPath: String, val slotId: Long?, val slotListIndex: Int?)

/**
 * A PKCS#11 user PIN.
 *
 * [Destroyable] because the PIN is the only credential protecting a token-resident key: a caller that holds one for
 * the lifetime of a process should be able to clear it. The copy handed to `KeyStore.load` is always zeroed by the
 * session factory regardless.
 */
class Pkcs11Pin(value: CharArray) : Destroyable {
    private var value: CharArray? = value.copyOf()

    internal fun copy(): CharArray =
        requireNotNull(value) { "PKCS11 PIN has been destroyed" }.copyOf()

    @Throws(DestroyFailedException::class)
    override fun destroy() {
        value?.fill('\u0000')
        value = null
    }

    override fun isDestroyed(): Boolean = value == null

    override fun toString(): String = "Pkcs11Pin(***)"
}

fun interface Pkcs11PinResolver {
    suspend fun resolve(reference: String): Pkcs11Pin
}
