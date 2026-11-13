package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.json.*

/**
 * Traces identities through an already validated presentation. This does not reconstruct values or
 * choose disclosures: the presenter's exact encoded selection and SDPayload.fullPayload own those.
 * A known but withheld array disclosure advances only the source position; a decoy advances neither.
 */
internal fun sdJwtClaimLocations(
    payload: JsonObject,
    available: List<SdJwtSelectiveDisclosure>,
    presented: List<SdJwtSelectiveDisclosure>,
    byClaimPath: Map<String, Set<String>> = emptyMap(),
): List<PresentationClaimLocation> = buildList {
    val byDigest = available.associateBy { it.asHashedFromEncoded() }
    val included = presented.mapTo(mutableSetOf()) { it.encoded }
    val w3cWrapped = payload["vc"] is JsonObject
    fun visit(value: JsonElement, source: List<JsonElement>, received: List<JsonElement>, ancestors: Set<String>) {
        add(PresentationClaimLocation(source, received,
            sharedBy = if (ancestors.isEmpty()) emptyList() else byClaimPath.filterValues { it.containsAll(ancestors) }.keys.toList(),
            alwaysCarried = ancestors.isEmpty()))
        when (value) {
            is JsonObject -> {
                value.forEach { (key, child) ->
                    if (key != "_sd") {
                        val sourceChild = if (w3cWrapped && received.isEmpty() && key == "vc") emptyList()
                            else source + JsonPrimitive(key)
                        visit(child, sourceChild, received + JsonPrimitive(key), ancestors)
                    }
                }
                (value["_sd"] as? JsonArray)?.forEach { digest ->
                    val disclosure = byDigest[(digest as? JsonPrimitive)?.content]
                    if (disclosure != null && disclosure.encoded in included) {
                        val key = requireNotNull(disclosure.name)
                        visit(disclosure.value, source + JsonPrimitive(key), received + JsonPrimitive(key), ancestors + disclosure.encoded)
                    }
                }
            }
            is JsonArray -> {
                var sourceIndex = 0
                var receivedIndex = 0
                value.forEach { child ->
                    val wrapper = (child as? JsonObject)?.takeIf { it.size == 1 && "..." in it }
                    if (wrapper == null) {
                        visit(child, source + JsonPrimitive(sourceIndex++), received + JsonPrimitive(receivedIndex++), ancestors)
                    } else {
                        val disclosure = byDigest[(wrapper["..."] as? JsonPrimitive)?.content]
                        if (disclosure != null) {
                            val original = sourceIndex++
                            if (disclosure.encoded in included)
                                visit(disclosure.value, source + JsonPrimitive(original), received + JsonPrimitive(receivedIndex++), ancestors + disclosure.encoded)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
    visit(payload, emptyList(), emptyList(), emptySet())
}
