package id.walt.credentials.utils

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import kotlinx.serialization.json.*

object SdJwtUtils {

    /**
     * Finds all JsonArray attributes named "_sd" recursively within this JsonObject.
     *
     * Only includes arrays that exclusively contain JsonPrimitive strings.
     * The path uses dot notation (e.g., "$.key1.key2._sd") and ignores array indices.
     *
     * @return A Map where keys are the JSON paths to the valid "_sd" arrays
     *         and values are the Set<String> contents of those arrays.
     */
    fun JsonObject.getSdArrays(): Map<String, Set<String>> {
        val results = mutableMapOf<String, Set<String>>()
        findSdArraysRecursive(this, "$", results)
        return results.toMap() // Return an immutable map
    }

    /**
     * Fix the following error for MongoDB before version 5 (2021) or DocumentDB:
     *
     * ```
     * Write operation error on server mongodb-database.svc.cluster.local:27017.
     * Write error: WriteError{
     *    code=52,
     *    message='The dollar ($) prefixed field '$._sd' in 'session.presentedCredentials.pid.0.disclosables.$._sd' is not valid for storage.'
     *  }
     * ```
     */
    fun Map<String, Set<String>>.dropDollarPrefix() =
        mapKeys { it.key.removePrefix("$.") }

    // Recursive helper function
    private fun findSdArraysRecursive(
        element: JsonElement,
        currentPath: String, // Path to the *containing* element
        results: MutableMap<String, Set<String>>
    ) {
        when (element) {
            is JsonObject -> {
                element.entries.forEach { (key, value) ->
                    // Construct the path for this specific key within the object
                    val elementPath = if (currentPath == "$") "$.$key" else "$currentPath.$key"

                    // Check if this is the target "_sd" key
                    if (key == "_sd" && value is JsonArray) {
                        // Validate that the array contains only strings
                        val stringSet = mutableSetOf<String>()
                        var allStrings = true
                        for (item in value) {
                            if (item is JsonPrimitive && item.isString) {
                                // Use content to get the actual string value
                                stringSet.add(item.content)
                            } else {
                                // Found a non-string or non-primitive element
                                allStrings = false
                                break
                            }
                        }

                        // If validation passed, add it to the results
                        if (allStrings) {
                            results[elementPath] = stringSet
                        }
                        // Don't recurse further *into* a found "_sd" array's contents,
                        // as we've already processed it.
                    } else {
                        // If it's not the target key, or not a valid array,
                        // recurse into the value to search deeper.
                        // Pass the path *to this element* down for further nesting.
                        findSdArraysRecursive(value, elementPath, results)
                    }
                }
            }

            is JsonArray -> {
                // Iterate through array elements. According to the requested path format
                // (e.g., "$.abc._sd"), array indices are not part of the path.
                // So, we pass the *current* path (path to the array) down when recursing.
                element.forEach { item ->
                    // Only need to explore potential containers of JsonObjects
                    if (item is JsonObject || item is JsonArray) {
                        findSdArraysRecursive(item, currentPath, results)
                    }
                }
            }
            // JsonPrimitive and JsonNull cannot contain "_sd" keys themselves,
            // so they are terminal nodes in the search.
            is JsonPrimitive -> {}
            is JsonNull -> {}
        }
    }


    fun parseDisclosureString(disclosures: String) =
        if (disclosures.isBlank()) null else
            disclosures.split("~").mapNotNull {
                if (it.isNotBlank()) {
                    val jsonArrayString = it.base64UrlDecode().decodeToString()
                    val jsonArray = Json.decodeFromString<JsonArray>(jsonArrayString)

                    SdJwtSelectiveDisclosure(
                        salt = jsonArray[0].jsonPrimitive.content,
                        name = jsonArray[1].jsonPrimitive.content,
                        value = jsonArray[2],
                        encoded = jsonArrayString
                    )
                } else null
            }

}
