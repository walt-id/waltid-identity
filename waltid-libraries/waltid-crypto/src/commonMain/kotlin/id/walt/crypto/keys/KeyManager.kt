package id.walt.crypto.keys

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.oci.OCIKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object KeyManager {
    private val log = KotlinLogging.logger { }

    fun keyManagerLogger() = log

    val types = HashMap<String, KType>()
    val keyTypeGeneration = HashMap<String, suspend (KeyGenerationRequest) -> Key>()

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
    }

    inline fun <reified T : Key> register(typeId: String, noinline createFunction: suspend (KeyGenerationRequest) -> T) {
        keyManagerLogger().trace { "Registering key type \"$typeId\" to ${T::class.simpleName}..." }
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
    // TODO: return Result<..>
    fun resolveSerializedKey(json: JsonObject): Key {
        val type = getRegisteredKeyType(json["type"]?.jsonPrimitive?.content ?: error("No type in serialized key"))

        val fields = json.filterKeys { it != "type" }
            //jwkKey is a stringified json
            .mapValues { if (it.value is JsonObject) it.value.toString().toJsonElement() else it.value }

        return Json.decodeFromJsonElement(serializer(type), JsonObject(fields)) as Key
    }


}