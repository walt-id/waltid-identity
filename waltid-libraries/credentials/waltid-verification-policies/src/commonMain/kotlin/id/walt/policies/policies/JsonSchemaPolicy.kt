package id.walt.policies.policies

import id.walt.w3c.utils.VCFormat
import id.walt.policies.CredentialDataValidatorPolicy
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExport
class JsonSchemaPolicy : CredentialDataValidatorPolicy(
) {

    override val name = "schema"
    override val description =
        "Verifies a credentials data against a JSON Schema (Draft 7 - see https://json-schema.org/specification-links#draft-7)."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    @Serializable
    data class SerializableValidationError(
        val schemaPath: String,
        val objectPath: String,
        val message: String,
        val details: Map<String, String>?,
        val absoluteLocation: String?,
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val schema = runCatching {
            when (args) {
                is String -> JsonSchema.fromDefinition(args)
                is JsonElement -> JsonSchema.fromJsonElement(args)
                else -> throw IllegalArgumentException("Provided JSON Schema is not an String or JsonElement")
            }
        }.getOrElse {
            when (it) {
                is IllegalArgumentException -> throw IllegalArgumentException("Provided JSON schema is not valid: ${it.message}")
                else -> throw it
            }
        }

        val errors = mutableListOf<ValidationError>()
        val success = schema.validate(data, errors::add)

        return if (success) {
            Result.success(args ?: args.toString())
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

            Result.failure(id.walt.policies.JsonSchemaVerificationException(serializableErrors))
        }
    }
}
