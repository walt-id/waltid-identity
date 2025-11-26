@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.db.models

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.mdoc.dataelement.json.toJsonElement
import id.walt.mdoc.doc.MDoc
import id.walt.oid4vc.data.CredentialFormat
import id.walt.sdjwt.SDJwt
import id.walt.webwallet.manifest.provider.ManifestProvider
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

object WalletCredentials : Table("credentials") {
    val wallet = reference("wallet", Wallets)
    val id = varchar("id", 256)

    val document = text("document")
    val disclosures = text("disclosures").nullable()

    val addedOn = timestamp("added_on")
    val manifest = text("manifest").nullable()

    val deletedOn = timestamp("deleted_on").nullable().default(null)
    val pending = bool("pending").default(false)
    val format = varchar("format", 32).default(CredentialFormat.jwt_vc_json.value)

    override val primaryKey = PrimaryKey(wallet, id)
}

@Serializable
data class WalletCredential @OptIn(ExperimentalUuidApi::class) constructor(
    @Contextual
    val wallet: Uuid,
    val id: String,
    val document: String,
    val disclosures: String? = null,
    val addedOn: Instant,
    @Transient
    val manifest: String? = null,
    val deletedOn: Instant? = null,
    val pending: Boolean = false,
    val format: CredentialFormat,
    val parsedDocument: JsonObject? = parseDocument(document, id, format),
    @SerialName("manifest")
    val parsedManifest: JsonObject? = tryParseManifest(manifest),
) {

    companion object {
        fun parseDocument(document: String, id: String, format: CredentialFormat) =
            runCatching {
                when (format) {
                    CredentialFormat.ldp_vc ->
                        Json.parseToJsonElement(document).jsonObject

                    CredentialFormat.jwt_vc,
                    CredentialFormat.sd_jwt_dc,
                    CredentialFormat.sd_jwt_vc,
                    CredentialFormat.jwt_vc_json,
                    CredentialFormat.jwt_vc_json_ld ->
                        document.decodeJws().payload.run { jsonObject["vc"]?.jsonObject ?: jsonObject }

                    CredentialFormat.mso_mdoc ->
                        MDoc.fromCBORHex(document).toMapElement().toJsonElement().jsonObject
                    else -> throw IllegalArgumentException("Unknown credential format: " + format.value)
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

        fun parseFullDocument(document: String, disclosures: String?, id: String, format: CredentialFormat) = kotlin.runCatching {
            when (format) {
                CredentialFormat.jwt_vc, CredentialFormat.sd_jwt_vc, CredentialFormat.jwt_vc_json,
                CredentialFormat.jwt_vc_json_ld -> SDJwt.parse(document + (disclosures?.let { "~$it" } ?: "")).fullPayload

                else -> parseDocument(document, id, format)
            }
        }.onFailure { it.printStackTrace() }
            .getOrNull()
    }


    constructor(result: ResultRow) : this(
        wallet = result[WalletCredentials.wallet].value.toKotlinUuid(),
        id = result[WalletCredentials.id],
        document = result[WalletCredentials.document],
        disclosures = result[WalletCredentials.disclosures],
        addedOn = result[WalletCredentials.addedOn].toKotlinInstant(),
        manifest = result[WalletCredentials.manifest],
        deletedOn = result[WalletCredentials.deletedOn]?.toKotlinInstant(),
        pending = result[WalletCredentials.pending],
        format = CredentialFormat.fromValue(result[WalletCredentials.format])
            ?: throw IllegalArgumentException("Credential format couldn't be decoded")
    )
}
