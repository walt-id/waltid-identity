package id.walt.issuer2.domain

import id.walt.mdoc.objects.mso.Status.StatusListInfo
import id.walt.mdoc.objects.mso.UniformResourceIdentifier
import id.walt.openid4vci.CredentialFormat
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.encodedPath
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URISyntaxException

/** Interprets issuer configuration, not the status value written into a credential. */
sealed interface CredentialStatusConfiguration {
    data class Fixed(val value: JsonElement?) : CredentialStatusConfiguration

    data class Preallocated(val groups: List<Group>) : CredentialStatusConfiguration

    data class Group(val statuses: JsonArray, val references: Set<String>)

    private data class TokenStatusReference(val uri: URI, val index: ULong)

    companion object {
        private val percentEscape = Regex("%[0-9A-Fa-f]{2}")

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
                val list = tokenStatusReference(status)
                val uri = if (format == CredentialFormat.MSO_MDOC) URI(mdocStatusListUri(list.uri).string) else list.uri
                return listOf(reference(uri, list.index.toString()))
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
                add(reference(statusListUri(status["statusListCredential"], "statusListCredential"), canonicalIndex))
                status["id"]?.let {
                    val id = (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    require(!id.isNullOrBlank()) { "W3C status id must be a non-empty string" }
                    add("id:" + id)
                }
            }
        }

        internal fun tokenStatusList(status: JsonElement): StatusListInfo {
            val list = tokenStatusReference(status)
            return StatusListInfo(list.index, mdocStatusListUri(list.uri))
        }

        private fun tokenStatusReference(status: JsonElement): TokenStatusReference {
            val list = (status as? JsonObject)?.get("status_list") as? JsonObject
                ?: throw IllegalArgumentException("Preallocated status requires status_list")
            val index = (list["idx"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toULongOrNull()
            requireNotNull(index) { "status_list.idx must be an unsigned integer" }
            return TokenStatusReference(statusListUri(list["uri"], "status_list.uri"), index)
        }

        private fun statusListUri(value: JsonElement?, field: String): URI {
            val text = (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            val uri = try {
                text?.let(::URI)
            } catch (_: URISyntaxException) {
                null
            }
            require(uri?.host != null && (uri.scheme.equals("https", true) || uri.scheme.equals("http", true))) {
                "$field must be an absolute HTTP(S) URI"
            }
            require(uri.port <= 65535) { "$field must have a valid port" }
            require(uri.rawFragment == null) { "$field must be a fragment-free URI" }
            return uri
        }

        private fun mdocStatusListUri(uri: URI): UniformResourceIdentifier {
            val url = try {
                URLBuilder(uri.toString())
            } catch (e: URLParserException) {
                throw IllegalArgumentException("status_list.uri is not a valid HTTP(S) URL", e)
            }
            url.host = url.host.lowercase()
            if (url.encodedPath.isEmpty()) url.encodedPath = "/"
            return UniformResourceIdentifier(url.build())
        }

        private fun reference(uri: URI, index: String): String {
            val scheme = uri.scheme.lowercase()
            val defaultPort = if (scheme == "https") 443 else 80
            // Use raw components so query order and reserved delimiters remain significant.
            val ownershipUri = buildString {
                append(scheme).append("://")
                uri.rawUserInfo?.let { append(normalizePercentEscapes(it)).append('@') }
                append(uri.host.lowercase())
                if (uri.port != -1 && uri.port != defaultPort) append(':').append(uri.port)
                append(canonicalStatusListPath(uri.rawPath))
                uri.rawQuery?.let { append('?').append(normalizePercentEscapes(it)) }
            }
            return "list:" + JsonArray(listOf(JsonPrimitive(ownershipUri), JsonPrimitive(index))).toString()
        }

        private fun normalizePercentEscapes(component: String): String =
            percentEscape.replace(component) { match ->
                when (val decoded = match.value.substring(1).toInt(16).toChar()) {
                    in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '.', '_', '~' -> decoded.toString()
                    else -> match.value.uppercase()
                }
            }

        // Decode unreserved escapes first so literal and percent-encoded dot segments compare alike.
        private fun canonicalStatusListPath(path: String): String {
            val normalized = normalizePercentEscapes(path)
            val segments = mutableListOf<String>()
            for (segment in normalized.split('/')) {
                when (segment) {
                    "." -> Unit
                    ".." -> if (segments.size > 1) segments.removeAt(segments.lastIndex)
                    else -> segments.add(segment)
                }
            }
            if (normalized.endsWith("/.") || normalized.endsWith("/..")) segments.add("")
            return segments.joinToString("/").ifEmpty { "/" }
        }

        internal fun validateMapping(format: CredentialFormat, mapping: JsonObject?) {
            if (format == CredentialFormat.MSO_MDOC) return
            // Mapping keys are literal. A jwt: key writes to the signed payload; jwt:vc replaces the W3C wrapper.
            val protected = if (format == CredentialFormat.SD_JWT_VC) setOf("status", "jwt:status")
                else setOf("credentialStatus", "jwt:credentialStatus", "jwt:vc")
            require(mapping.orEmpty().keys.none { it in protected }) { "Mappings must not overwrite preallocated credential status" }
        }
    }
}
