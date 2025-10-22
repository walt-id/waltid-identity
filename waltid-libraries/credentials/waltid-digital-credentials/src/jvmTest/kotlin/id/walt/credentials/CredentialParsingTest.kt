package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test


class CredentialParsingTest {

    @Test
    fun credentialParsingTest() = runTest {
        val results = ArrayList<Triple<CredentialDetectionResult, CredentialDetectionResult, String>>()

        suspend fun doCheck(index: Int, example: Pair<String, CredentialDetectionResult>, name: String, claims: Map<String, Any>? = null) {
            val (credentialExample, expected) = example
            println("$name $index (expecting ${expected})")
            val (detection, credential) = CredentialParser.detectAndParse(credentialExample)
            results.add(Triple(detection, expected, "$name $index"))
            println("$name $index parsed as: ${credential.toString().replace("\n", "")}")

            if (claims != null) {
                val credentialClaimsToCheck = Json.encodeToJsonElement(credential).jsonObject
                claims.entries.forEachIndexed { index, entry ->
                    println("Checking claim ${index + 1} (${entry.key}): expecting ${entry.value}")
                    val actual = credentialClaimsToCheck[entry.key]?.jsonPrimitive?.content
                    val expected = entry.value
                    check(actual == expected) { "Failed claim comparison: got $actual, expected $expected" }
                }
            }
        }

        W3CExampleList.allW3CCredentialExamples.forEachIndexed { index, example ->
            val (credentialTest, expectedResult) = example
            val (inputCredential, expectedClaims) = credentialTest
            doCheck(index, inputCredential to expectedResult, "w3c", expectedClaims)
        }
        SdJwtExampleList.allSdJwtVcCredentialExamples.forEachIndexed { index, example ->
            doCheck(index, example, "sdjwtvc")
        }
        MdocsExampleList.allMdocsExamples.forEachIndexed { index, example ->
            doCheck(index, example, "mdoc")
        }

        println("\nResults (${results.size}):")
        results.forEach { (detection, expected, descriptor) ->
            println("Correct: ${(detection == expected).toString().uppercase()} for $descriptor ($detection == $expected)")
        }
        println("OK: ${results.count { (detection, expected) -> detection == expected }}")
        println("Failed: ${results.count { (detection, expected) -> detection != expected }}")

        results.forEach { (detected, expected, descriptor) ->
            check(detected == expected) { "$descriptor: detected != expected: $detected != $expected" }
        }


    }

}
