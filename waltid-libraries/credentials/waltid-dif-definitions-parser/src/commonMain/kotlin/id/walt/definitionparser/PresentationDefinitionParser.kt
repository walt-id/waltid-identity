package id.walt.definitionparser

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Constraints.Field
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.OutputCollector
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private val log = KotlinLogging.logger { }

class JsonObjectEnquirer {

    private val compiledJsonPaths = HashMap<String, JsonPath>()

    private fun getJsonPath(path: String): JsonPath =
        compiledJsonPaths.getOrPut(path) { JsonPath.compile(path) }


    fun filterDocumentsByConstraints(documents: List<JsonObject>, constraints: List<Field>): List<JsonObject> =
        documents.filter { document ->
            constraints.all { field ->
                val resolvedPath = field.path.firstNotNullOfOrNull { document.resolveOrNull(getJsonPath(it)) }

                if (resolvedPath == null) {
                    false
                } else {
                    if (field.filter != null) {
                        val schema = JsonSchema.fromJsonElement(field.filter)
                        when(resolvedPath) {
                            is JsonArray -> resolvedPath.any { schema.validate(it, OutputCollector.flag()).valid }
                            else -> schema.validate(resolvedPath, OutputCollector.flag()).valid
                        }
                    } else true
                }
            }
        }
}

object PresentationDefinitionParser {

    fun matchCredentialsForInputDescriptor(credentials: List<JsonObject>, inputDescriptor: PresentationDefinition.InputDescriptor): List<JsonObject> {

        log.debug { "--- Checking descriptor ${inputDescriptor.name} --" }

        val enquirer = JsonObjectEnquirer()

        return enquirer.filterDocumentsByConstraints(credentials, inputDescriptor.constraints.fields!!)

    }

}
