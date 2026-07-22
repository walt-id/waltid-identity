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
        log.debug(generationRequest::safeLogDescription)

        return function.invoke(generationRequest)
    }

    /**
     * Type-safe overload of [createKey] for [TypedKeyGenerationRequest].
     *
     * Dispatches directly to the appropriate backend without any stringly-typed
     * discriminator or [kotlinx.serialization.json.JsonObject] config encoding.
     */
    suspend fun createKey(request: TypedKeyGenerationRequest): Key = when (request) {
        is TypedKeyGenerationRequest.Jwk -> JWKKey.generate(request.keyType)
        is TypedKeyGenerationRequest.Tse -> TSEKey.generate(request.keyType, request.config)
        is TypedKeyGenerationRequest.Azure -> AzureKeyRestApi.generate(request.keyType, request.config)
        is TypedKeyGenerationRequest.Oci -> OCIKeyRestApi.generateKey(request.keyType, request.config)
        is TypedKeyGenerationRequest.Aws -> AWSKeyRestAPI.generate(request.keyType, request.config)
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

internal fun KeyGenerationRequest.safeLogDescription(): String =
    "Creating $keyType key with backend $backend" + name?.let { " and name $it" }.orEmpty()

expect fun resolveSerializedKeyBlocking(json: JsonObject): Key
