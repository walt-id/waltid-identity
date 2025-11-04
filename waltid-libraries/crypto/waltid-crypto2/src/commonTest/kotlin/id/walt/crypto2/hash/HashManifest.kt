package id.walt.crypto2.hash

import kotlinx.serialization.json.Json
import utils.loadResource

internal data class HashManifest(
    val algorithm: HashAlgorithm,
    val resourcePath: String,
) {
    val vectors: List<HashVector> by lazy {
        loadResource(resourcePath).decodeToString().run {
            Json.decodeFromString<List<HashVector>>(this)
        }
    }
}
