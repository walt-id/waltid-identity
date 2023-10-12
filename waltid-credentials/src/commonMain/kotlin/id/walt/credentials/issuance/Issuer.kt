package id.walt.credentials.issuance

import id.walt.credentials.utils.W3CVcUtils.overwrite
import id.walt.credentials.utils.W3CVcUtils.update
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object Issuer {

    /**
     * @param id: id
     */
    @Serializable
    data class ExtraData(
        val idLocation: String = "id",


        )

    /**
     * Manually set data and issue credential
     */
    suspend fun W3CVC.baseIssue(
        key: Key,
        did: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, String>
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
}
