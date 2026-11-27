package id.walt.wallet2.persistence.encryption

/**
 * Raw key material used to unlock one encrypted wallet database.
 *
 * @property keyId Stable identifier for the database key.
 * @property material Defensive copy of the raw SQLCipher key material.
 */
public class DatabaseEncryptionKey(
    public val keyId: String,
    material: ByteArray,
) {
    /**
     * Raw SQLCipher key material.
     */
    public val material: ByteArray get() = materialBytes.copyOf()

    /**
     * Returns a new key value with optional replacements.
     */
    public fun copy(
        keyId: String = this.keyId,
        material: ByteArray = this.material,
    ): DatabaseEncryptionKey = DatabaseEncryptionKey(keyId, material)

    /**
     * Destructures the key identifier.
     */
    public operator fun component1(): String = keyId

    /**
     * Destructures a defensive copy of the key material.
     */
    public operator fun component2(): ByteArray = material

    /**
     * Compares key identifiers and key bytes.
     */
    override fun equals(other: Any?): Boolean =
        other is DatabaseEncryptionKey &&
            keyId == other.keyId &&
            materialBytes.contentEquals(other.materialBytes)

    /**
     * Hashes the key identifier and key bytes.
     */
    override fun hashCode(): Int =
        31 * keyId.hashCode() + materialBytes.contentHashCode()

    /**
     * Formats the key without exposing raw key material.
     */
    override fun toString(): String =
        "DatabaseEncryptionKey(keyId=$keyId, material=<redacted>)"

    private val materialBytes: ByteArray = material.copyOf()
}
