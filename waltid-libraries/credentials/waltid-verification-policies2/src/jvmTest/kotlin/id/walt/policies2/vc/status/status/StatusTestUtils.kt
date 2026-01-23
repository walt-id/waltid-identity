package id.walt.policies2.vc.status.status

import kotlinx.serialization.json.Json
import java.io.File
import java.net.URISyntaxException

const val STATUS_CREDENTIAL_PATH_PLACEHOLDER = "<STATUS-CREDENTIAL-PATH-PLACEHOLDER>"
const val PLACEHOLDER_VALUE_SEPARATOR = "/"

val JSON_MAPPER = Json { ignoreUnknownKeys = true }

object StatusTestUtils {


    class TestResourceReader {

        fun readResourcesBySubfolder(
            rootResourcePath: String, placeholderValue: String
        ): Map<String, List<TestStatusResource>> = getResourceAsFile(rootResourcePath).let { rootDir ->
            rootDir.listFiles()?.filter { it.isDirectory }?.associate { subfolder ->
                val entries = subfolder.listFiles { _, name -> name.endsWith(".json") }?.map { jsonFile ->
                    try {
                        val resourceData = JSON_MAPPER.decodeFromString<StatusResourceData>(jsonFile.readText())
                        val computedId = "${subfolder.name}-${jsonFile.nameWithoutExtension}"
                        val updatedResourceData = resourceData.updateHolderCredential(placeholderValue, computedId)
                            .updateStatusCredential(computedId)
                        TestStatusResource(id = computedId, data = updatedResourceData)
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to parse JSON file: ${jsonFile.path}", e)
                    }
                } ?: emptyList()
                subfolder.name to entries
            } ?: emptyMap()
        }

        private fun getResourceAsFile(resourcePath: String): File {
            val resourceUrl = this::class.java.classLoader.getResource(resourcePath)
                ?: throw IllegalArgumentException("Test resource path not found: $resourcePath (URL was null)")
            return try {
                File(resourceUrl.toURI())
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("Invalid test resource path syntax: $resourcePath", e)
            }
        }
    }
}
