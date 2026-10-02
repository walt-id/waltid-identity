package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeWithMapping
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.did.dids.DidUtils
import id.walt.sdjwt.SDMap
import id.walt.w3c.JwtClaims
import id.walt.w3c.VcClaims
import id.walt.w3c.issuance.Issuer
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Instant

/**
 * The template context of an issuance, as every format builds it: `issuerId`, `issuerDid`, `subjectDid` and
 * `display`, with [extra] on top. Absent and empty values are left out, so a mapping that names one fails clearly
 * instead of receiving an empty value.
 *
 * `issuerDid` is the issuer id even when that is not a DID, e.g. an issuer URL: mappings such as the example
 * profiles' `"issuer": {"id": "<issuerDid>"}` depend on it, and only producing it for DIDs would make their
 * issuance fail.
 */
fun issuanceTemplateContext(
    issuerId: String?,
    subjectDid: String?,
    display: JsonArray? = null,
    extra: Map<String, JsonElement>? = null,
): Map<String, JsonElement> = buildMap {
    issuerId?.takeIf { it.isNotEmpty() }?.let {
        put("issuerId", JsonPrimitive(it))
        put("issuerDid", JsonPrimitive(it))
    }
    subjectDid?.takeIf { it.isNotEmpty() }?.let { put("subjectDid", JsonPrimitive(it)) }
    display?.takeIf { it.isNotEmpty() }?.let { put("display", it) }
    extra?.let { putAll(it) }
}

/**
 * Issues a W3C credential from data merged with a mapping ([CredentialDataMergeUtils]), signing with the W3C
 * library's [Issuer]. Moved from the W3C library, since the merge machinery serves every credential format.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object MergingIssuer {

    @Deprecated("Use the crypto2 overload accepting a Key and JwsAlgorithm")
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
            issuerKid = Issuer.getKidHeader(issuerKey, issuerDid),
            subjectDid = subjectDid,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtOptions)
            }
        )
    }

    @JsExport.Ignore
    suspend fun W3CVC.mergingJwtIssue(
        issuerKey: Crypto2Key,
        algorithm: JwsAlgorithm,
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
        context = context,
    ).run {
        val issuerDid = issuerId.takeIf(DidUtils::isDidUrl)
        w3cVc.signJws(
            issuerKey = issuerKey,
            algorithm = algorithm,
            issuerId = issuerId,
            issuerKid = Issuer.getKidHeader(issuerKey, issuerDid),
            subjectDid = subjectDid,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply { putAll(jwtOptions) },
        )
    }

    @Deprecated("Use the crypto2 overload accepting a Key and JwsAlgorithm")
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
        type: String ,
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
            issuerKid = Issuer.getKidHeader(issuerKey, issuerDid),
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

    @JsExport.Ignore
    suspend fun W3CVC.mergingSdJwtIssue(
        issuerKey: Crypto2Key,
        algorithm: JwsAlgorithm,
        issuerId: String,
        subjectDid: String,
        display: JsonArray = JsonArray(emptyList()),
        mappings: JsonObject,
        type: String,
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
        completeJwtWithDefaultCredentialData = completeJwtWithDefaultCredentialData,
        context = context,
    ).run {
        val issuerDid = issuerId.takeIf(DidUtils::isDidUrl)
        w3cVc.signSdJwt(
            issuerKey = issuerKey,
            algorithm = algorithm,
            issuerId = issuerId,
            issuerKid = Issuer.getKidHeader(issuerKey, issuerDid),
            subjectDid = subjectDid,
            disclosureMap = disclosureMap,
            additionalJwtHeaders = additionalJwtHeaders + ("typ" to JsonPrimitive(type)),
            additionalJwtOptions = additionalJwtOptions + jwtOptions,
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
        val mergedContext = issuanceTemplateContext(
            issuerId = issuerId,
            subjectDid = subjectDid,
            display = display,
            extra = context,
        )

        val mapped = this.mergeWithMapping(mappings, mergedContext, issuanceDataFunctions())

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
}
