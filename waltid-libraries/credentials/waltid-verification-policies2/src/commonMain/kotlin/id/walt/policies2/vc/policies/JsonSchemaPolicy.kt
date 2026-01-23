package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.JsonSchemaVerificationException
import id.walt.webdatafetching.WebDataFetcher
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.SchemaType
import io.github.optimumcode.json.schema.ValidationError
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("schema")
data class JsonSchemaPolicy(
    val schema: JsonObject? = null,
    val schemaUrl: Url? = null,
    val defaultType: SchemaType? = null
) : CredentialVerificationPolicy2() {
    override val id = "schema"

    init {
        require(schema != null || schemaUrl != null) { "Schema or Schema URL has to be provided" }
    }

    @Transient
    private val schemaFetcher = schemaUrl?.let { WebDataFetcher<JsonObject>("schema-policy") }

    suspend fun getCurrentSchema(): JsonObject {
        return when {
            schemaUrl != null -> schemaFetcher!!.fetch<JsonObject>(schemaUrl)
            schema != null -> schema
            else -> throw IllegalStateException("No schema defined")
        }
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val currentSchema = getCurrentSchema()

        val jsonSchema = JsonSchema.fromJsonElement(currentSchema, defaultType)
        val errors = mutableListOf<ValidationError>()

        val valid = jsonSchema.validate(credential.credentialData, errors::add)

        return if (valid) {
            Result.success(JsonObject(mapOf("schema" to currentSchema)))
        } else {
            val serializableErrors = errors.map {
                SerializableValidationError(
                    schemaPath = it.schemaPath.toString(),
                    objectPath = it.objectPath.toString(),
                    message = it.message,
                    details = it.details.ifEmpty { null },
                    absoluteLocation = it.absoluteLocation?.toString()
                )
            }

            Result.failure(JsonSchemaVerificationException(serializableErrors))
        }
    }

    @Serializable
    data class SerializableValidationError(
        val schemaPath: String,
        val objectPath: String,
        val message: String,
        val details: Map<String, String>?,
        val absoluteLocation: String?,
    )
}
