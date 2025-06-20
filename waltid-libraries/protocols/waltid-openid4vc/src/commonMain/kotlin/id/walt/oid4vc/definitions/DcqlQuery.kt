package id.walt.oid4vc.definitions

/*
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class DcqlQuery(
    val credentials: List<DcqlQueryCredential>,
    override val customParameters: Map<String, JsonElement> = emptyMap()
): JsonDataObject() {
    override fun toJSON(): JsonObject =
        Json.encodeToJsonElement(this).jsonObject

    companion object : JsonDataObjectFactory<DcqlQuery>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement<DcqlQuery>(jsonObject)
    }


        @Serializable
    data class DcqlQueryCredential(
        val id: String,
        val format: CredentialFormat,
        val claims: DcqlQueryCredentialClaim
    ) {
        @Serializable
        data class DcqlQueryCredentialClaim(
            val path: List<String>
        )
    }
}
*/
