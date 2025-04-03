package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import kotlin.test.Test


class CredentialParsingTest {

    @Test
    fun x() {
        val results = ArrayList<Triple<CredentialDetectionResult, CredentialDetectionResult, String>>()

        fun doCheck(index: Int, example: Pair<String, CredentialDetectionResult>, name: String) {
            val (credentialExample, expected) = example
            println("$name $index (expecting ${expected})")
            val (detection, credential) = CredentialParser.detectAndParse(credentialExample)
            results.add(Triple(detection, expected, "$name $index"))
            println("$name $index parsed as: ${credential.toString().replace("\n", "")}")
        }

        W3CExamples.allW3CCredentialExamples.forEachIndexed { index, example ->
            doCheck(index, example, "w3c")
        }
        SdJwtExamples.allSdJwtVcCredentialExamples.forEachIndexed { index, example ->
            doCheck(index, example, "sdjwtvc")
        }
        MdocsExamples.allMdocsExamples.forEachIndexed { index, example ->
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
