package id.walt.credentials.issuance

import id.walt.credentials.utils.W3CDataMergeUtils.mergeWithMapping
import id.walt.credentials.utils.W3CVcUtils.overwrite
import id.walt.credentials.utils.W3CVcUtils.update
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.Key
import id.walt.sdjwt.SDMap
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
        did: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>
    ): String {
        val overwritten = overwrite(dataOverwrites)
        var updated = overwritten
        dataUpdates.forEach { (k, v) -> updated = updated.update(k, v) }

        return signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions
        )
    }
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergingJwtIssue(
        issuerKey: Key,
        issuerDid: String,
        issuerKid: String? = null,
        subjectDid: String,

        mappings: JsonObject,

        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>,

        completeJwtWithDefaultCredentialData: Boolean = true,
    ) = mergingToVc(
        issuerDid = issuerDid,
        subjectDid = subjectDid,
        mappings = mappings,
        completeJwtWithDefaultCredentialData
    ).run {
        w3cVc.signJws(
            issuerKey = issuerKey,
            issuerDid = issuerDid,
            issuerKid = issuerKid,
            subjectDid = subjectDid,
            additionalJwtHeader = additionalJwtHeader.toMutableMap().apply {
                put("typ", "JWT")
            },
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
        issuerDid: String,
        subjectDid: String,

        mappings: JsonObject,

        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>,

        completeJwtWithDefaultCredentialData: Boolean = true,
        disclosureMap: SDMap
    ) = mergingToVc(
        issuerDid = issuerDid,
        subjectDid = subjectDid,
        mappings = mappings,
        completeJwtWithDefaultCredentialData
    ).run {
        w3cVc.signSdJwt(
            issuerKey = issuerKey,
            issuerDid = issuerDid,
            subjectDid = subjectDid,
            disclosureMap = disclosureMap,
            additionalJwtHeader = additionalJwtHeader.toMutableMap().apply {
                put("typ", "JWT")
            },
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtOptions)
            }
        )
    }

    data class IssuanceInformation(
        val w3cVc: W3CVC,
        val jwtOptions: Map<String, JsonElement>
    )

    /**
     * Merge data with mappings and issue
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergingToVc(
        issuerDid: String,
        subjectDid: String,

        mappings: JsonObject,

        completeJwtWithDefaultCredentialData: Boolean = true,
    ): IssuanceInformation {
        val context = mapOf(
            "issuerDid" to JsonPrimitive(issuerDid),
            "subjectDid" to JsonPrimitive(subjectDid)
        )

        val mapped = this.mergeWithMapping(mappings, context, dataFunctions)

        val vc = mapped.vc
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
            completeJwtAttributes("exp") {
                vc["expirationDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("iat") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("nbf") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
        }

        return IssuanceInformation(vc, jwtRes)
    }
}
