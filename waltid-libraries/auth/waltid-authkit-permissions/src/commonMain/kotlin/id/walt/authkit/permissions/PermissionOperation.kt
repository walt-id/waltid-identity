package id.walt.authkit.permissions

import kotlinx.serialization.Serializable

@Serializable
enum class PermissionOperation(val symbol: Char) {
    ADD('+'),
    REMOVE('-');

    companion object {
        private val operationMapping = enumValues<PermissionOperation>().associateBy { it.symbol }

        fun from(char: Char) = operationMapping[char] ?: error("Invalid operation: $char, allowed operations: ${entries.map { it.symbol }}")
    }
}
