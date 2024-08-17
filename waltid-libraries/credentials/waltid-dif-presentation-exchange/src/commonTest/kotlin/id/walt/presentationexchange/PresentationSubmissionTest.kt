package id.walt.presentationexchange

import kotlinx.serialization.json.Json
import kotlin.test.Test

class PresentationSubmissionTest {

    //language=JSON
    val presentationSubmission = """
        {
          "id": "a30e3b91-fb77-4d22-95fa-871689c322e2",
          "definition_id": "32f54163-7166-48f1-93d8-ff217bdb0653",
          "descriptor_map": [
            {
              "id": "banking_input_2",
              "format": "jwt_vc",
              "path": "${'$'}.verifiableCredential[0]"
            },
            {
              "id": "employment_input",
              "format": "ldp_vc",
              "path": "${'$'}.verifiableCredential[1]"
            },
            {
              "id": "citizenship_input_1",
              "format": "ldp_vc",
              "path": "${'$'}.verifiableCredential[2]"
            }
          ]
        }
    """.trimIndent()

    //language=JSON
    val nestedSubmission = """
        {
            "id": "a30e3b91-fb77-4d22-95fa-871689c322e2",
            "definition_id": "32f54163-7166-48f1-93d8-ff217bdb0653",
            "descriptor_map": [
              {
                "id": "banking_input_2",
                "format": "jwt_vp",
                "path": "${'$'}.outerClaim[0]",
                "path_nested": {
                  "id": "banking_input_2",
                  "format": "ldp_vc",
                  "path": "${'$'}.innerClaim[1]",
                  "path_nested": {
                    "id": "banking_input_2",
                    "format": "jwt_vc",
                    "path": "${'$'}.mostInnerClaim[2]"
                  }
                }
              }
            ]
          }
    """.trimIndent()



    @Test
    fun testPresentationSubmissionParsing() {
        println("presentationSubmission")
        Json.decodeFromString<PresentationSubmission>(presentationSubmission)

        println("nestedSubmission")
        Json.decodeFromString<PresentationSubmission>(nestedSubmission)

    }

}
