package id.walt.issuer2.domain

import id.walt.openid4vci.CredentialFormat
import kotlinx.serialization.json.*
import java.net.URI

/** Interprets issuer configuration, not the status value written into a credential. */
sealed interface CredentialStatusConfiguration {
    data class Fixed(val value: JsonElement?) : CredentialStatusConfiguration

    data class Preallocated(val groups: List<Group>) : CredentialStatusConfiguration

    data class Group(val statuses: JsonArray, val references: Set<String>)

    companion object {
        fun credentialValue(group: JsonArray, format: CredentialFormat): JsonElement =
            if (format == CredentialFormat.SD_JWT_VC || format == CredentialFormat.MSO_MDOC) group.single()
            else group

        /** Session snapshots use nested groups for every format, including flat SD-JWT/mdoc inputs. */
        fun isPreallocated(value: JsonElement?): Boolean =
            value is JsonArray && value.any { it is JsonArray }

        fun parse(
            value: JsonElement?,
            format: CredentialFormat,
            mapping: JsonObject? = null,
        ): CredentialStatusConfiguration {
            val singleStatusFormat = format == CredentialFormat.SD_JWT_VC || format == CredentialFormat.MSO_MDOC
            if (value !is JsonArray || (!singleStatusFormat && !isPreallocated(value))) return Fixed(value)
            // Keep grouped snapshots readable while accepting one complete status object per public
            // SD-JWT/mdoc array entry. W3C flat arrays retain their existing shared-status meaning.
            val outer = if (singleStatusFormat && value.all { it is JsonObject })
                JsonArray(value.map { JsonArray(listOf(it)) }) else value
            require(outer.isNotEmpty() && outer.all { it is JsonArray }) {
                "credentialStatus must contain only non-empty per-copy status groups"
            }
            require(format in setOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC,
                CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC)) {
                "Preallocated credentialStatus is unsupported for this credential format"
            }
            validateMapping(format, mapping)
            val seen = mutableSetOf<String>()
            return Preallocated(outer.map { item ->
                val statuses = item as JsonArray
                require(statuses.isNotEmpty() && statuses.all { it is JsonObject }) {
                    "Each credentialStatus group must contain status objects"
                }
                if (singleStatusFormat) {
                    require(statuses.size == 1) { "SD-JWT VC and mdoc status groups must contain exactly one entry" }
                }
                val references = statuses.flatMap { status -> references(status.jsonObject, format) }
                require(references.all(seen::add)) { "Preallocated credentialStatus contains duplicate status references" }
                Group(statuses, references.toSet())
            })
        }

        private fun references(status: JsonObject, format: CredentialFormat): List<String> {
            if (format == CredentialFormat.SD_JWT_VC || format == CredentialFormat.MSO_MDOC) {
                val list = status["status_list"] as? JsonObject
                    ?: throw IllegalArgumentException("Preallocated status requires status_list")
                val index = list["idx"] as? JsonPrimitive
                require(index != null && !index.isString && index.content.toULongOrNull() != null) {
                    "status_list.idx must be an unsigned integer"
                }
                return listOf(reference(uri(list["uri"], "status_list.uri"), index.content.toULong().toString()))
            }
            require(status["type"]?.jsonPrimitive?.content in setOf("BitstringStatusListEntry", "StatusList2021Entry")) {
                "Unsupported preallocated W3C status type"
            }
            require((status["statusPurpose"] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)
                ?.contentOrNull?.isNotBlank() == true) {
                "W3C statusPurpose is required"
            }
            val index = status["statusListIndex"] as? JsonPrimitive
            require(index != null && index.isString && index.content.matches(Regex("[0-9]+"))) {
                "statusListIndex must be a non-negative decimal string"
            }
            val canonicalIndex = index.content.trimStart('0').ifEmpty { "0" }
            return buildList {
                add(reference(uri(status["statusListCredential"], "statusListCredential"), canonicalIndex))
                status["id"]?.let {
                    val id = (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    require(!id.isNullOrBlank()) { "W3C status id must be a non-empty string" }
                    add("id:" + id)
                }
            }
        }

        private fun uri(value: JsonElement?, field: String): String {
            val text = (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            val uri = text?.let { runCatching { URI(it) }.getOrNull() }
            require(uri != null && uri.isAbsolute && uri.host != null && uri.scheme.lowercase() in setOf("http", "https")) {
                field + " must be an absolute HTTP(S) URI"
            }
            return requireNotNull(text)
        }

        private fun reference(uri: String, index: String): String =
            "list:" + JsonArray(listOf(JsonPrimitive(uri), JsonPrimitive(index))).toString()

        private fun validateMapping(format: CredentialFormat, mapping: JsonObject?) {
            if (format == CredentialFormat.MSO_MDOC) return
            // Mapping keys are literal. A jwt: key writes to the signed payload; jwt:vc replaces the W3C wrapper.
            val protected = if (format == CredentialFormat.SD_JWT_VC) setOf("status", "jwt:status")
                else setOf("credentialStatus", "jwt:credentialStatus", "jwt:vc")
            require(mapping.orEmpty().keys.none { it in protected }) { "Mappings must not overwrite preallocated credential status" }
        }
    }
}
