package id.walt.crypto.keys

data class TSEKeyMetadata(
    val server: String,
    val accessKey: String,
    val id: String? = null
)
