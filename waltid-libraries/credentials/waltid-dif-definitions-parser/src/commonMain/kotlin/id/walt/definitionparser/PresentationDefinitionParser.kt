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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val log = KotlinLogging.logger { }

class JsonObjectEnquirer {

    private val compiledJsonPaths = HashMap<String, JsonPath>()

    private fun getJsonPath(path: String): JsonPath =
        compiledJsonPaths.getOrPut(path) { JsonPath.compile(path) }

    fun filterConstraint(document: JsonObject, field: Field): Boolean {
        log.trace { "Processing constraint field: ${field.name ?: field.id ?: field}" }
        /* Alternative if both vc-wrapped and non-wrapped should be used equally:*/
          val resolvedPath = field.path.firstNotNullOfOrNull {
              document.resolveOrNull(getJsonPath(it))
                  ?: if (it.startsWith("$.vc.")) document.resolveOrNull(getJsonPath("$." + it.removePrefix("$.vc.")))
                  else null
          }
        // Alternative if vc-wrapped should be preferred, but non-wrapped used as fallback:
        //val resolvedPath = field.path.firstNotNullOfOrNull { document.resolveOrNull(getJsonPath(it)) }


        log.trace { "Result of resolving ${field.path}: $resolvedPath" }

        return if (resolvedPath == null) {
            log.trace { "Unresolved field, failing constraint (Path ${field.path} not found in document $document)." }
            false
        } else {
            log.trace { "Processing field filter: ${field.filter}" }
            if (field.filter != null) {
                val schema = JsonSchema.fromJsonElement(field.filter)
                when {
                    field.filter["type"]?.jsonPrimitive?.contentOrNull?.lowercase() == "string" && resolvedPath is JsonArray -> resolvedPath.any {
                        schema.validate(it, OutputCollector.flag()).valid
                    }
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
            log.trace { "Checking document against constraints: $document" }
            checkDocumentAgainstConstraints(document, constraints)
        }
}

object PresentationDefinitionParser {

    fun matchCredentialsForInputDescriptor(
        credentials: Flow<JsonObject>,
        inputDescriptor: PresentationDefinition.InputDescriptor,
    ): Flow<JsonObject> {

        log.trace { "--- Checking descriptor (name ${inputDescriptor.name}, id ${inputDescriptor.id}) --" }

        val enquirer = JsonObjectEnquirer()

        return enquirer.filterDocumentsByConstraints(credentials, inputDescriptor.constraints.fields!!)

    }

}
