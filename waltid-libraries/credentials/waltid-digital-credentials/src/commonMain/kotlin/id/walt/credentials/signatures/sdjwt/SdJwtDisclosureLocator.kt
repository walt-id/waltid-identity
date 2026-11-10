package id.walt.credentials.signatures.sdjwt

import id.walt.credentials.UnreachableDisclosuresException
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.utils.JwtUtils
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.credentials.utils.SdJwtUtils.appendArrayIndex
import id.walt.credentials.utils.SdJwtUtils.appendClaimName
import kotlinx.serialization.json.*

/** Maps disclosures into the processed credential, where unmatched array digests are removed (RFC 9901 §7.1). */
internal object SdJwtDisclosureLocator {
    fun locate(payload: JsonObject, disclosures: List<SdJwtSelectiveDisclosure>): List<SdJwtSelectiveDisclosure> {
        val located = mutableListOf<SdJwtSelectiveDisclosure>()
        val seenDigests = mutableSetOf<String>()
        // Preserve the parser's legacy issuer encoding fallbacks; prefer the exact wire bytes.
        val byDigest = mutableMapOf<String, SdJwtSelectiveDisclosure>()
        disclosures.forEach { disclosure ->
            listOf(disclosure.asHashedFromEncoded(), disclosure.asHashed(), disclosure.asHashed2(), disclosure.asHashed3())
                .forEach { byDigest.getOrPut(it) { disclosure } }
        }

        fun resolve(hash: String): SdJwtSelectiveDisclosure? {
            require(seenDigests.add(hash)) { "Invalid SD-JWT: repeated digest (RFC 9901 §7.1 step 4)" }
            return byDigest[hash]
        }

        fun scan(element: JsonElement, path: List<JsonElement>) {
            fun visit(disclosure: SdJwtSelectiveDisclosure, location: List<JsonElement>) {
                located += disclosure.copy(location = location)
                scan(disclosure.value, location)
            }
            when (element) {
                is JsonObject -> {
                    element["_sd"]?.jsonArray?.forEach { digest ->
                        if (digest is JsonPrimitive && digest.isString) {
                            resolve(digest.content)?.let { disclosure ->
                                visit(disclosure, disclosure.name?.let { path.appendClaimName(it) } ?: path)
                            }
                        }
                    }
                    element.forEach { (key, value) -> if (key != "_sd") scan(value, path.appendClaimName(key)) }
                }
                is JsonArray -> {
                    var position = 0
                    element.forEach { item ->
                        if (item is JsonObject && item.size == 1 && item.containsKey("...")) {
                            val hash = item.getValue("...").jsonPrimitive.content
                            resolve(hash)?.let { visit(it, path.appendArrayIndex(position++)) }
                        } else {
                            scan(item, path.appendArrayIndex(position++))
                        }
                    }
                }
                else -> Unit
            }
        }

        // W3C JWT-VC claim paths are relative to the vc object, as in the credential parser.
        scan((payload["vc"] as? JsonObject) ?: payload, emptyList())
        val uniqueLocated = located.map { it.encoded }.toSet().size
        if (located.size != disclosures.size || uniqueLocated != disclosures.size) {
            throw UnreachableDisclosuresException(
                "Invalid disclosures: ${disclosures.size} provided but ${uniqueLocated} uniquely reachable " +
                    "from payload hashes (RFC 9901 §7.1 step 5)"
            )
        }
        return located
    }
}

/**
 * Recompute stale persisted locations using the complete available disclosure set, before selecting a subset.
 * Without [SelectivelyDisclosableVerifiableCredential.originalCredentialData], the payload is decoded from the
 * issuer-signed JWT; stored locations are never trusted.
 */
fun SelectivelyDisclosableVerifiableCredential.locatedDisclosures(): List<SdJwtSelectiveDisclosure> {
    val available = disclosures.orEmpty()
    val payload = originalCredentialData ?: issuerSignedPayload()
        ?: if (available.isEmpty()) return available
        else throw UnreachableDisclosuresException("Invalid disclosures: no issuer-signed payload to locate them in")
    return SdJwtDisclosureLocator.locate(payload, available)
}

/** The payload the credential parser locates disclosures in: the `vc` object when it holds `_sd`, else the JWT body. */
private fun SelectivelyDisclosableVerifiableCredential.issuerSignedPayload(): JsonObject? {
    val issuerJwt = (signedWithDisclosures ?: (this as? DigitalCredential)?.signed)
        ?.substringBefore("~")?.takeIf { it.isJwt() } ?: return null
    val body = JwtUtils.parseJwt(issuerJwt).second
    return (body["vc"] as? JsonObject)?.takeIf { "_sd" in it } ?: body
}
