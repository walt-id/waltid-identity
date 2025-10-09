package id.walt.crypto2.hash

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import utils.loadResource

internal data class HashManifest(
    val algorithm: HashAlgorithm,
    val resourcePath: String,
) {
    val vectors: List<HashVector> by lazy {
        val payload = loadResource(resourcePath).decodeToString()
        Json.decodeFromString(ListSerializer(HashVector.serializer()), payload).also { list ->
            list.forEach {
                require(HashAlgorithm.valueOf(it.algorithm.uppercase()) == algorithm) {
                    "Vector '${it.name}' in $resourcePath declared algorithm ${it.algorithm} but manifest expects $algorithm"
                }
            }
        }
    }
}
