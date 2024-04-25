package id.walt.crypto.keys

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object KeyManager {
    private val log = KotlinLogging.logger { }
    private val types = HashMap<String, KType>()
    private val keyTypeGeneration = HashMap<String, suspend (KeyGenerationRequest) -> Key>()

    fun getRegisteredKeyType(type: String): KType = types[type] ?: error("Unknown key type: $type")

    init {
        register<JWKKey>("jwk") { generateRequest: KeyGenerationRequest -> JWKKey.generate(generateRequest.keyType) }
        register<TSEKey>("tse") { generateRequest: KeyGenerationRequest ->
            TSEKey.generate(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }
        register<OCIKeyRestApi>("oci-rest-api") { generateRequest: KeyGenerationRequest ->
            OCIKeyRestApi.generateKey(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }

        register <OCIKey>("oci") { generateRequest: KeyGenerationRequest ->
            OCIKey.generateKey(
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }
    }

    private inline fun <reified T : Key> register(typeId: String, noinline createFunction: suspend (KeyGenerationRequest) -> T) {
        log.trace { "Registering key type \"$typeId\" to ${T::class.simpleName}..." }
        val type = typeOf<T>()
        types[typeId] = type
        keyTypeGeneration[typeId] = createFunction
    }

    suspend fun createKey(generationRequest: KeyGenerationRequest): Key {
        val function = keyTypeGeneration[generationRequest.backend] ?: error("No such key backend registered: ${generationRequest.backend}")
        log.debug { "Creating key with generation request: $generationRequest" }

        return function.invoke(generationRequest)
    }

    fun resolveSerializedKey(jsonString: String): Key = resolveSerializedKey(json = Json.parseToJsonElement(jsonString).jsonObject)
    fun resolveSerializedKey(json: JsonObject): Key {
        val type = getRegisteredKeyType(json["type"]?.jsonPrimitive?.content ?: error("No type in serialized key"))

        val new = JsonObject(json.filterKeys { it != "type" })

        val key: Key = Json.decodeFromJsonElement(serializer(type), new) as Key
        return key
    }


}
