package id.walt.policies.policies.status.status

import id.walt.policies.JsonObjectUtils.updateJsonObjectPlaceholders
import id.walt.policies.policies.status.model.StatusPolicyAttribute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URISyntaxException

const val STATUS_CREDENTIAL_PATH_PLACEHOLDER = "<STATUS-CREDENTIAL-PATH-PLACEHOLDER>"
private const val PLACEHOLDER_VALUE_SEPARATOR = "/"

object StatusTestUtils {

    @Serializable
    data class StatusResource(
        val id: String,
        val data: StatusResourceData,
    ) {
        @Serializable
        data class StatusResourceData(
            @SerialName("status-credential")
            val statusCredential: String,
            @SerialName("holder-credential")
            val holderCredential: JsonObject,
            val valid: Boolean,
            val attribute: StatusPolicyAttribute,
        )
    }

    class TestResourceReader {
        companion object {
            private val JSON_MAPPER = Json { ignoreUnknownKeys = true }
        }

        fun readResourcesBySubfolder(
            rootResourcePath: String, placeholderValue: String
        ): Map<String, List<StatusResource>> = getResourceAsFile(rootResourcePath).let { rootDir ->
            rootDir.listFiles()?.filter { it.isDirectory }?.associate { subfolder ->
                val entries = subfolder.listFiles { _, name -> name.endsWith(".json") }?.map { jsonFile ->
                    try {
                        val resourceData =
                            JSON_MAPPER.decodeFromString<StatusResource.StatusResourceData>(jsonFile.readText())
                        val computedId = "${subfolder.name}-${jsonFile.nameWithoutExtension}"
                        val updatedResourceData = resourceData.copy(
                            holderCredential = updateJsonObjectPlaceholders(
                                resourceData.holderCredential,
                                STATUS_CREDENTIAL_PATH_PLACEHOLDER,
                                PLACEHOLDER_VALUE_SEPARATOR,
                                placeholderValue,
                                computedId
                            )
                        )
                        StatusResource(
                            id = computedId, data = updatedResourceData
                        )
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

    data class StatusTestContext(
        val credential: JsonObject,
        val attribute: StatusPolicyAttribute? = null,
        val expectValid: Boolean,
    )
}