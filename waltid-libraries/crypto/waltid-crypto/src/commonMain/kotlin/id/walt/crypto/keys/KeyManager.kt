package id.walt.crypto.keys

import id.walt.crypto.exceptions.KeyBackendNotSupportedException
import id.walt.crypto.exceptions.KeyTypeMissingException
import id.walt.crypto.keys.aws.AWSKeyRestAPI
import id.walt.crypto.keys.azure.AzureKeyRestApi
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.oci.OCIKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
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

    fun getRegisteredKeyBackend(type: String): KType = types[type] ?: throw KeyBackendNotSupportedException(type)

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
        register<AWSKeyRestAPI>("aws-rest-api") { generateRequest: KeyGenerationRequest ->
            AWSKeyRestAPI.generate(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }

        register<AzureKeyRestApi>("azure-rest-api") { generateRequest: KeyGenerationRequest ->
            AzureKeyRestApi.generate(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }

    }

    fun registerByType(type: KType, typeId: String, createFunction: suspend (KeyGenerationRequest) -> Key) {
        types[typeId] = type
        keyTypeGeneration[typeId] = createFunction
    }

    inline fun <reified T : Key> register(
        typeId: String,
        noinline createFunction: suspend (KeyGenerationRequest) -> T
    ) {
        keyManagerLogger().trace { "Registering key type \"$typeId\" to ${T::class.simpleName}..." }
        val type = typeOf<T>()
        registerByType(type, typeId, createFunction)
    }

    suspend fun createKey(generationRequest: KeyGenerationRequest): Key {
        val function = keyTypeGeneration[generationRequest.backend] ?: throw KeyBackendNotSupportedException(
            generationRequest.backend
        )
        log.debug { "Creating key with generation request: $generationRequest" }

        return function.invoke(generationRequest)
    }

    suspend fun resolveSerializedKey(jsonString: String): Key =
        resolveSerializedKey(json = Json.parseToJsonElement(jsonString).jsonObject)

    // TODO: return Result<..>
    suspend fun resolveSerializedKey(json: JsonObject): Key = json["type"]?.jsonPrimitive?.content?.let {
        val type = getRegisteredKeyBackend(it)
        val fields = json.filterKeys { it != "type" }.mapValues { it.value }
        Json.decodeFromJsonElement(serializer(type), JsonObject(fields)) as Key
    }?.apply { init() } ?: throw KeyTypeMissingException()

    fun resolveSerializedKeyBlocking(jsonString: String) =
        resolveSerializedKeyBlocking(json = Json.parseToJsonElement(jsonString).jsonObject)

}

expect fun resolveSerializedKeyBlocking(json: JsonObject): Key
