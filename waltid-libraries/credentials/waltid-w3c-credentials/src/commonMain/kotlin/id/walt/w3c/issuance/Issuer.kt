package id.walt.w3c.issuance

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidUtils
import id.walt.sdjwt.SDMap
import id.walt.w3c.JwtClaims
import id.walt.w3c.VcClaims
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeWithMapping
import id.walt.w3c.utils.W3CVcUtils.overwrite
import id.walt.w3c.utils.W3CVcUtils.update
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Instant

@OptIn(ExperimentalJsExport::class)
@JsExport
object Issuer {

    /**
     * Manually set data and issue credential
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.baseIssue(
        key: Key,
        issuerId: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeaders: Map<String, JsonElement>,
        additionalJwtOptions: Map<String, JsonElement>,
    ): String {
        val overwritten = overwrite(dataOverwrites)
        var updated = overwritten
        dataUpdates.forEach { (k, v) -> updated = updated.update(k, v) }

        return signJws(
            issuerKey = key,
            issuerId = issuerId,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeaders,
            additionalJwtOptions = additionalJwtOptions
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergingJwtIssue(
        issuerKey: Key,
        issuerId: String,
        subjectDid: String,

        mappings: JsonObject,

        additionalJwtHeader: Map<String, JsonElement>,
        additionalJwtOptions: Map<String, JsonElement>,
        display: JsonArray = JsonArray(emptyList()),
        completeJwtWithDefaultCredentialData: Boolean = true,
        context: Map<String, JsonElement>? = null,
    ) = mergingToVc(
        issuerId = issuerId,
        subjectDid = subjectDid,
        mappings = mappings,
        display = display,
        completeJwtWithDefaultCredentialData = completeJwtWithDefaultCredentialData,
        context = context
    ).run {
        val issuerDid = if (DidUtils.isDidUrl(issuerId)) issuerId else null
        w3cVc.signJws(
            issuerKey = issuerKey,
            issuerId = issuerId,
            issuerKid = getKidHeader(issuerKey, issuerDid),
            subjectDid = subjectDid,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtOptions)
            }
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergingSdJwtIssue(
        issuerKey: Key,
        issuerId: String,
        subjectDid: String,
        display: JsonArray = JsonArray(emptyList()),

        mappings: JsonObject,
        type: String = "JWT",
        additionalJwtHeaders: Map<String, JsonElement>,
        additionalJwtOptions: Map<String, JsonElement>,

        completeJwtWithDefaultCredentialData: Boolean = true,
        disclosureMap: SDMap,
        context: Map<String, JsonElement>? = null,
    ) = mergingToVc(
        issuerId = issuerId,
        subjectDid = subjectDid,
        mappings = mappings,
        display = display,
        completeJwtWithDefaultCredentialData,
        context = context
    ).run {
        val issuerDid = if (DidUtils.isDidUrl(issuerId)) issuerId else null
        w3cVc.signSdJwt(
            issuerKey = issuerKey,
            issuerId = issuerId,
            issuerKid = getKidHeader(issuerKey, issuerDid),
            subjectDid = subjectDid,
            disclosureMap = disclosureMap,
            additionalJwtHeaders = additionalJwtHeaders.toMutableMap().apply {
                put("typ", type.toJsonElement())
            },

            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtOptions)
            }
        )
    }

    data class IssuanceInformation(
        val w3cVc: W3CVC,
        val jwtOptions: Map<String, JsonElement>,
    )

    /**
     * Merge data with mappings and issue
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergingToVc(
        issuerId: String,
        subjectDid: String,

        mappings: JsonObject,
        display: JsonArray? = null,
        completeJwtWithDefaultCredentialData: Boolean = true,
        context: Map<String, JsonElement>? = null,
    ): IssuanceInformation {
        val mergedContext = mapOf(
            "issuerId" to issuerId,
            "issuerDid" to (if (DidUtils.isDidUrl(issuerId)) issuerId else null),
            "subjectDid" to subjectDid,
            "display" to display
        ).filterValues { value ->
            when (value) {
                is JsonElement -> value !is JsonNull &&
                        (value !is JsonObject || value.jsonObject.isNotEmpty()) &&
                        (value !is JsonArray || value.jsonArray.isNotEmpty())

                else -> value.toString().isNotEmpty()
            }
        }.mapValues { (_, value) ->
            when (value) {
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }.toMutableMap().apply {
            context?.let { putAll(it) }
        }

        val mapped = this.mergeWithMapping(mappings, mergedContext, dataFunctions)

        // For VCDM 2.0, rename V1.1-only date fields to their VCDM 2.0 equivalents
        val vc = if (mapped.vc.isV2()) {
            val m = mapped.vc.toMutableMap()
            m.remove("issuanceDate")?.let { v -> if ("validFrom" !in m) m["validFrom"] = v }
            m.remove("expirationDate")?.let { v -> if ("validUntil" !in m) m["validUntil"] = v }
            W3CVC(m)
        } else mapped.vc
        val jwtRes = mapped.results.mapKeys { it.key.removePrefix("jwt:") }.toMutableMap()

        fun completeJwtAttributes(attribute: String, completer: () -> JsonElement?) {
            if (attribute !in jwtRes) {
                val completed = completer.invoke()

                if (completed != null) {
                    jwtRes[attribute] = completed
                }
            }
        }

        if (completeJwtWithDefaultCredentialData) {
            completeJwtAttributes("jti") { vc["id"] }
            completeJwtAttributes(JwtClaims.NotAfter.getValue()) {
                vc[VcClaims.V1.NotAfter.getValue()]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
                    ?: vc[VcClaims.V2.NotAfter.getValue()]?.let { Instant.parse(it.jsonPrimitive.content) }
                        ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            // V1.1 uses issuanceDate, V2.0 uses validFrom — try both
            val issuanceInstant =
                (vc[VcClaims.V1.NotBefore.getValue()] ?: vc[VcClaims.V2.NotBefore.getValue()])
                    ?.let { Instant.parse(it.jsonPrimitive.content) }
            completeJwtAttributes("iat") { issuanceInstant?.epochSeconds?.let { JsonPrimitive(it) } }
            completeJwtAttributes("nbf") { issuanceInstant?.epochSeconds?.let { JsonPrimitive(it) } }
        }

        return IssuanceInformation(vc, jwtRes)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getKidHeader(issuerKey: Key, issuerDid: String? = null): String {
        return if (!issuerDid.isNullOrEmpty()) {
            if (issuerDid.startsWith("did:key"))
                issuerDid + "#" + issuerDid.removePrefix("did:key:")
            else
                issuerDid + "#" + issuerKey.getKeyId()
        } else issuerKey.getKeyId()
    }
}
