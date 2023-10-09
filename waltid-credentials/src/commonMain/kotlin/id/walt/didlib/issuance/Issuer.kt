package id.walt.didlib.issuance

import id.walt.core.crypto.keys.Key
import id.walt.didlib.utils.W3CVcUtils.overwrite
import id.walt.didlib.utils.W3CVcUtils.update
import id.walt.didlib.vc.list.W3CVC
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

object Issuer {

    /**
     * @param id: id
     */
    @Serializable
    data class ExtraData(
        val idLocation: String = "id",


    )

    /**
     *
     */
    suspend fun W3CVC.issue(
        key: Key,
        did: String,
        subject: String
    ) {
        this.overwrite(
            mapOf(
                "id" to JsonPrimitive("urn:uuid:${"uuid"}"),
                "issuanceDate" to JsonPrimitive(Clock.System.now().toString())
            )
        ).update(
            key = "credentialSubject",
            mapOf("id" to JsonPrimitive(subject))
        ).update(
            key = "issuer",
            mapOf("id" to JsonPrimitive(did))
        ).signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = subject
        )
    }

}
