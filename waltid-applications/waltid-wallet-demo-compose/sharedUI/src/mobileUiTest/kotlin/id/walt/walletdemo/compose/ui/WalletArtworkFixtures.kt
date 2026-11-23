package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.CredentialCardDisplayData
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Frozen public artwork with synthetic assessments: layout proof, never an issued adverse PID. */
internal object WalletArtworkFixtures {
    const val Origin = "https://wallet-visual.example/artwork/"
    private val data by lazy {
        Json.parseToJsonElement(SyntheticCredentialImageFiles.read("credential-artwork.json").decodeToString()).jsonObject
            .also { check(it.getValue("schemaVersion").jsonPrimitive.int == 1) }
    }
    private val entries by lazy { data.getValue("cards").jsonArray.map { it.jsonObject } }
    val images by lazy {
        data.getValue("artwork").jsonArray.associate { asset ->
            val file = asset.jsonObject.getValue("file").jsonPrimitive.content
            Origin + file to SyntheticCredentialImageFiles.read(file)
        }
    }
    val cards by lazy { entries.map { card ->
        CredentialCardDisplayData(id = card.getValue("id").jsonPrimitive.content,
            title = card.getValue("title").jsonPrimitive.content, issuer = "Layout fixture",
            backgroundColor = card.getValue("background").jsonPrimitive.content,
            backgroundImageUri = card["image"]?.jsonPrimitive?.content?.let { Origin + it }, textColor = "#FFFFFF")
    } }
    val statuses by lazy { WalletDemoCredentialStatuses(entries.associate { card ->
        card.getValue("id").jsonPrimitive.content to WalletVisualFixtures.credentialStatus(card.getValue("status").jsonPrimitive.content)
    }) }
}
