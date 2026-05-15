package id.walt.openid4vci.validation

import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerializationException

internal fun Map<String, List<String>>.requireSingle(name: String): String {
    val values = this[name].orEmpty().filter { it.isNotBlank() }
    if (values.isEmpty()) throw SerializationException("Missing $name")
    if (values.size > 1) throw SerializationException("Multiple values for $name")
    return values.first()
}

// For optional single-valued parameters: allows 0 or 1 non-blank values, rejects duplicates.
internal fun Map<String, List<String>>.optionalSingle(name: String): String? {
    val values = this[name].orEmpty().filter { it.isNotBlank() }
    if (values.size > 1) throw SerializationException("Multiple values for $name")
    return values.firstOrNull()
}

internal fun Map<String, List<String>>.optionalAll(name: String): List<String> =
    this[name].orEmpty().flatMap { entry ->
        entry.split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

// For optional parameters that must not repeat; use when presence is optional but multiplicity is not allowed.
internal fun Map<String, List<String>>.rejectDuplicate(name: String) {
    val values = this[name].orEmpty()
    if (values.size > 1) throw SerializationException("Multiple values for $name not allowed")
}

internal fun Map<String, List<String>>.optionalAuthorizationDetails(): List<AuthorizationDetail> {
    val value = optionalSingle("authorization_details") ?: return emptyList()
    return try {
        kotlinx.serialization.json.Json
            .decodeFromString(ListSerializer(AuthorizationDetail.serializer()), value)
    } catch (e: Exception) {
        throw SerializationException("Invalid authorization_details: ${e.message}")
    }
}