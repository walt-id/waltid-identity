package id.walt.webwallet.db.models

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.webwallet.manifest.provider.ManifestProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletCredentials : Table("credentials") {
    val wallet = reference("wallet", Wallets)
    val id = varchar("id", 256)

    val document = text("document")
    val disclosures = text("disclosures").nullable()

    val addedOn = timestamp("added_on")
    val manifest = text("manifest").nullable()

    val deletedOn = timestamp("deleted_on").nullable().default(null)
    val pending = bool("pending").default(false)

    override val primaryKey = PrimaryKey(wallet, id)
}

@Serializable
data class WalletCredential(
    val wallet: UUID,
    val id: String,
    val document: String,
    val disclosures: String?,
    val addedOn: Instant,
    @Transient
    val manifest: String? = null,
    val deletedOn: Instant?,
    val pending: Boolean = false,

    val parsedDocument: JsonObject? = parseDocument(document, id),
    @SerialName("manifest")
    val parsedManifest: JsonObject? = tryParseManifest(manifest),
) {

    companion object {
        fun parseDocument(document: String, id: String) =
            runCatching {
                when {
                    document.startsWith("{") -> Json.parseToJsonElement(document).jsonObject
                    document.startsWith("ey") -> document.decodeJws().payload
                        .run { jsonObject["vc"]?.jsonObject ?: jsonObject }

                    else -> throw IllegalArgumentException("Unknown credential format")
                }.toMutableMap().also {
                    it.putIfAbsent("id", JsonPrimitive(id))
                }.let {
                    JsonObject(it)
                }
            }.onFailure { it.printStackTrace() }
                .getOrNull()

        private fun tryParseManifest(manifest: String?) = runCatching {
            manifest?.let { ManifestProvider.json.decodeFromString<JsonObject>(it) }
        }.fold(onSuccess = {
            it
        }, onFailure = {
            null
        })
    }


    constructor(result: ResultRow) : this(
        wallet = result[WalletCredentials.wallet].value,
        id = result[WalletCredentials.id],
        document = result[WalletCredentials.document],
        disclosures = result[WalletCredentials.disclosures],
        addedOn = result[WalletCredentials.addedOn].toKotlinInstant(),
        manifest = result[WalletCredentials.manifest],
        deletedOn = result[WalletCredentials.deletedOn]?.toKotlinInstant(),
        pending = result[WalletCredentials.pending],
    )
}