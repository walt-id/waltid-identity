package id.walt.crypto.keys

data class TSEKeyMetadata(
    val server: String,
    val accessKey: String,
    val namespace: String? = null,
    val id: String? = null
)
