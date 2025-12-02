package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.JsonSchemaVerificationException
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.SchemaType
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("schema")
data class JsonSchemaPolicy(
    val schema: JsonObject,
    val defaultType: SchemaType? = null
) : id.walt.policies2.vc.policies.CredentialVerificationPolicy2() {
    override val id = "schema"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val jsonSchema = JsonSchema.fromJsonElement(schema, defaultType)
        val errors = mutableListOf<ValidationError>()

        val valid = jsonSchema.validate(credential.credentialData, errors::add)

        return if (valid) {
            Result.success(JsonObject(mapOf("schema" to schema)))
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
