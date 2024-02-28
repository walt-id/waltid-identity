package id.walt.webwallet.seeker

import id.walt.webwallet.db.models.WalletCredential
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EntraCredentialTypeSeeker : Seeker<String> {
    override fun get(credential: WalletCredential): String =
        credential.parsedDocument?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.content ?: "n/a"
}