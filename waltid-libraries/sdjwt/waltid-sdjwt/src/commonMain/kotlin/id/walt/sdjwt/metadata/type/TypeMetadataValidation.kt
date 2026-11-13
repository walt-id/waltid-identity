package id.walt.sdjwt.metadata.type

import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import kotlinx.serialization.json.*

/** A prepared credential violates its declared type; values are deliberately omitted from diagnostics. */
class TypeMetadataValidationException(message: String) : IllegalArgumentException(message)

/**
 * Validate complete, prepared credentials before any signature is requested. The caller retains the
 * same payloads for signing. The loader owns transport policy; schema engines never perform I/O.
 */
suspend fun ResolvedTypeMetadata.validateForIssuance(
    credentials: List<SDPayload>,
    load: suspend (String) -> ByteArray,
) {
    require(credentials.isNotEmpty()) { "Issuance validation requires at least one credential" }
    val payloads = credentials.map { credential ->
        val payload = JsonObject(credential.fullPayload - "_sd_alg")
        if (payload["vct"] != JsonPrimitive(documents.first().vct)) {
            throw TypeMetadataValidationException("Credential VCT does not match its type metadata")
        }
        payload["vct#integrity"]?.let { pin ->
            try {
                require(pin is JsonPrimitive && pin.isString)
                MetadataIntegrity.verify(documents.first().bytes(), pin.content)
            } catch (_: IllegalArgumentException) {
                throw TypeMetadataValidationException("Credential metadata pin does not match its publication")
            }
        }
        validateClaims(payload, credential.sdMap, emptySet())
        payload
    }
    val compiled = TypeMetadataSchemas.compile(this, load)
    for (payload in payloads) for (schema in compiled) {
        if (!schema.validate(payload) {}) {
            throw TypeMetadataValidationException("Credential does not satisfy an applicable type schema")
        }
    }
}

/**
 * Check only decidable offer inputs. A pending top-level claim may be supplied/replaced by a later
 * mapping or authenticated data source. This does not validate schemas or execute data functions.
 */
fun ResolvedTypeMetadata.validateIssuanceInput(
    knownClaims: JsonObject,
    selectiveDisclosure: SDMap?,
    pendingClaims: Set<String> = emptySet(),
) {
    val effective = SDJwtVC.effectiveDisclosureMap(selectiveDisclosure, setOf("display", "_sd_alg"))
    validateClaims(knownClaims, effective, pendingClaims)
    for (claim in claims) {
        val root = claim.path.first() as? MetadataClaimPathComponent.Name ?: continue
        // These format claims are never selectively disclosable, irrespective of input or mapping.
        if (root.value in SDJwtVC.NON_SELECTIVELY_DISCLOSABLE_CLAIMS && claim.sd == ClaimSdMetadata.Always) {
            invalidClaim(claim, "requires selective disclosure of a protected format claim")
        }
        if (root.value in pendingClaims && claim.mandatory == true && claim.path.size == 1 &&
            claim.sd == ClaimSdMetadata.Always && effective[root.value]?.sd != true) {
            invalidClaim(claim, "requires selective disclosure")
        }
    }
}

private data class SelectedClaim(val value: JsonElement, val encoding: SDField?)

private fun ResolvedTypeMetadata.validateClaims(payload: JsonObject, encoding: SDMap, pendingClaims: Set<String>) {
    for (claim in claims) {
        if ((claim.path.first() as? MetadataClaimPathComponent.Name)?.value in pendingClaims) continue
        var selected = listOf(SelectedClaim(payload, SDField(false, children = encoding)))
        for (component in claim.path) selected = selected.flatMap { current ->
            fun missing(): List<SelectedClaim> {
                if (claim.mandatory == true) invalidClaim(claim, "is mandatory but absent")
                return emptyList()
            }
            when (component) {
                is MetadataClaimPathComponent.Name -> {
                    val value = current.value as? JsonObject ?: invalidClaim(claim, "requires an object")
                    value[component.value]?.let { listOf(SelectedClaim(it, current.encoding?.children?.get(component.value))) }
                        ?: missing()
                }
                is MetadataClaimPathComponent.Index -> {
                    val value = current.value as? JsonArray ?: invalidClaim(claim, "requires an array")
                    value.getOrNull(component.value)?.let {
                        listOf(SelectedClaim(it, current.encoding?.arrayChildren?.let { it.elements.getOrNull(component.value) ?: it.wildcard }))
                    } ?: missing()
                }
                MetadataClaimPathComponent.AnyIndex -> {
                    val value = current.value as? JsonArray ?: invalidClaim(claim, "requires an array")
                    value.mapIndexed { index, element ->
                        SelectedClaim(element, current.encoding?.arrayChildren?.let { it.elements.getOrNull(index) ?: it.wildcard })
                    }
                }
            }
        }
        for (value in selected) when (claim.sd) {
            ClaimSdMetadata.Always -> if (value.encoding?.sd != true) invalidClaim(claim, "requires selective disclosure")
            ClaimSdMetadata.Never -> if (value.encoding?.sd == true) invalidClaim(claim, "forbids selective disclosure")
            ClaimSdMetadata.Allowed, null -> Unit
        }
    }
}

private fun invalidClaim(claim: ResolvedMetadataClaim, reason: String): Nothing =
    throw TypeMetadataValidationException("Claim ${claim.json.getValue("path")} $reason")
