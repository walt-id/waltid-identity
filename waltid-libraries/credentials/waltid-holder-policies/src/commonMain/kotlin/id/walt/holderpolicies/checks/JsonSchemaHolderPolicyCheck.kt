package id.walt.holderpolicies.checks

import id.walt.credentials.formats.DigitalCredential
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.OutputCollector
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.all
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("schema")
data class JsonSchemaHolderPolicyCheck(
    val schema: JsonObject? = null,
    val schemas: List<JsonObject>? = null
) : HolderPolicyCheck {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    init {
        require((schema != null) xor (schemas != null)) { "You have to set either a single schema, or multiple schemas." }
    }

    @Transient
    private val applicableSchemas: List<JsonSchema> = (schemas ?: listOf(schema ?: error("Neither schemas nor schema were set."))).let {
        it.map { jsonSchema ->
            runCatching {
                JsonSchema.fromJsonElement(jsonSchema)
            }.getOrElse { ex ->
                throw IllegalArgumentException("Invalid JsonSchema specified for JsonSchemaHolderPolicyCheck: $jsonSchema", ex)
            }
        }
    }


    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean =
        credentials.all { credential ->
            log.trace { "-- Matching credential against schemas: ${schemas ?: schema}" }
            applicableSchemas.any { schema ->
                log.trace { "Checking credential: $credential" }
                schema.validate(credential.credentialData, OutputCollector.flag()).valid
                    .also { log.trace { "Credential matches schema: $it" } }
            }.also { log.trace { "Credential matches any schema: $it" } }
        }
}
