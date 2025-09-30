package id.walt.mdoc.encoding

/**
 * Checks if a ByteArray starts with a specific prefix.
 *
 * @param prefix The ByteArray prefix to check for.
 * @return True if this ByteArray starts with the given prefix, false otherwise.
 */
fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (prefix.size > this.size) return false
    return prefix.indices.all { this[it] == prefix[it] }
}
