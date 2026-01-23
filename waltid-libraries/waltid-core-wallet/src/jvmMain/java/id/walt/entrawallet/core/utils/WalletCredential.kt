@file:OptIn(ExperimentalTime::class)

package id.walt.entrawallet.core.utils

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.entrawallet.core.manifest.provider.ManifestProvider
import id.walt.mdoc.dataelement.json.toJsonElement
import id.walt.mdoc.doc.MDoc
import id.walt.oid4vc.data.CredentialFormat
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class WalletCredential(
    @Serializable(with = UuidSerializer::class) // required to serialize Uuid, until kotlinx.serialization uses Kotlin 2.1.0
    val wallet: Uuid,
    val id: String,
    val document: String,
    val disclosures: String?,
    val addedOn: Instant,
    @Transient
    val manifest: String? = null,
    val deletedOn: Instant?,
    val pending: Boolean = false,
    val format: CredentialFormat,
    val parsedDocument: JsonObject? = parseDocument(document, id, format),
    @SerialName("manifest")
    val parsedManifest: JsonObject? = tryParseManifest(manifest),
) {

    companion object {
        fun parseDocument(document: String, id: String, format: CredentialFormat) =
            runCatching {
                when(format) {
                    CredentialFormat.ldp_vc -> Json.parseToJsonElement(document).jsonObject
                    CredentialFormat.jwt_vc, CredentialFormat.sd_jwt_vc, CredentialFormat.jwt_vc_json,
                    CredentialFormat.jwt_vc_json_ld -> document.decodeJws().payload
                        .run { jsonObject["vc"]?.jsonObject ?: jsonObject }

                    CredentialFormat.mso_mdoc -> MDoc.fromCBORHex(document).toMapElement().toJsonElement().jsonObject
                    else -> throw IllegalArgumentException("Unknown credential format")
                }.toMutableMap().also {
                    it.putIfAbsent("id", JsonPrimitive(id))
                }.let {
                    JsonObject(it)
                }
            }.onFailure { it.printStackTrace() }
                .getOrNull()

        fun tryParseManifest(manifest: String?) = runCatching {
            manifest?.let { ManifestProvider.json.decodeFromString<JsonObject>(it) }
        }.fold(onSuccess = {
            it
        }, onFailure = {
            null
        })

        fun parseIssuerDid(credential: JsonObject?, manifest: JsonObject? = null) =
            credential?.jsonObject?.get("issuer")?.let {
                if (it is JsonObject) it.jsonObject["id"]?.jsonPrimitive?.content
                else it.jsonPrimitive.content
            } ?: manifest?.jsonObject?.get("input")?.jsonObject?.get("issuer")?.jsonPrimitive?.content

        fun getManifestLogo(manifest: JsonObject?) =
            manifest?.let { JsonUtils.tryGetData(it, "display.card.logo.uri")?.jsonPrimitive?.content }

        fun getManifestIssuerName(manifest: JsonObject?) =
            manifest?.let { JsonUtils.tryGetData(it, "display.card.issuedBy")?.jsonPrimitive?.content }
    }
}
