package id.walt.definitionparser

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Constraints.Field
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.OutputCollector
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private val log = KotlinLogging.logger { }

class JsonObjectEnquirer {

    private val compiledJsonPaths = HashMap<String, JsonPath>()

    private fun getJsonPath(path: String): JsonPath =
        compiledJsonPaths.getOrPut(path) { JsonPath.compile(path) }

    fun filterConstraint(document: JsonObject, field: Field): Boolean {
        val resolvedPath = field.path.firstNotNullOfOrNull { document.resolveOrNull(getJsonPath(it)) }

        return if (resolvedPath == null) {
            false
        } else {
            if (field.filter != null) {
                val schema = JsonSchema.fromJsonElement(field.filter)
                when (resolvedPath) {
                    is JsonArray -> resolvedPath.any { schema.validate(it, OutputCollector.flag()).valid }
                    else -> schema.validate(resolvedPath, OutputCollector.flag()).valid
                }
            } else true
        }
    }

    fun checkDocumentAgainstConstraints(document: JsonObject, constraints: List<Field>): Boolean =
        constraints.all { field ->
            filterConstraint(document, field)
        }

    fun filterDocumentsByConstraints(documents: Flow<JsonObject>, constraints: List<Field>): Flow<JsonObject> =
        documents.filter { document ->
            checkDocumentAgainstConstraints(document, constraints)
        }
}

object PresentationDefinitionParser {

    fun matchCredentialsForInputDescriptor(
        credentials: Flow<JsonObject>,
        inputDescriptor: PresentationDefinition.InputDescriptor,
    ): Flow<JsonObject> {

        log.trace { "--- Checking descriptor ${inputDescriptor.name} --" }

        val enquirer = JsonObjectEnquirer()

        return enquirer.filterDocumentsByConstraints(credentials, inputDescriptor.constraints.fields!!)

    }

}
