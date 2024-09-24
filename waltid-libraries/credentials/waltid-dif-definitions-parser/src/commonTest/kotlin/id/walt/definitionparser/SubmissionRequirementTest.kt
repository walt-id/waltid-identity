package id.walt.definitionparser

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test

class SubmissionRequirementTest {

    //language=JSON
    val submissionRequirement = """
        [
          {
            "name": "Submission of educational transcripts",
            "purpose": "We need your complete educational transcripts to process your application",
            "rule": "all",
            "from": "A"
          },
          {
            "name": "Citizenship Proof",
            "purpose": "We need to confirm you are a citizen of one of the following countries before accepting your application",
            "rule": "pick",
            "count": 1,
            "from": "B"
          },
          {
            "name": "Eligibility to Work Proof",
            "purpose": "We need to prove you are eligible for full-time employment in 2 or more of the following countries",
            "rule": "pick",
            "min": 2,
            "from": "B"
          },
          {
            "name": "Confirm banking relationship or employment and residence proofs",
            "purpose": "Recent bank statements or proofs of both employment and residence will be validated to initiate your loan application but not stored",
            "rule": "pick",
            "count": 1,
            "from_nested": [
              {
                "rule": "all",
                "from": "A"
              },
              {
                "rule": "pick",
                "count": 2,
                "from": "B"
              }
            ]
          }
        ]
    """.trimIndent()

    //language=JSON
    val submissionRequirements = """
        [
          {
            "name": "Banking Information",
            "purpose": "We need you to prove you currently hold a bank account older than 12months.",
            "rule": "pick",
            "count": 1,
            "from": "A"
          },
          {
            "name": "Employment Information",
            "purpose": "We are only verifying one current employment relationship, not any other information about employment.",
            "rule": "all",
            "from": "B"
          },
          {
            "name": "Citizenship Information",
            "rule": "pick",
            "count": 1,
            "from_nested": [
              {
                "name": "United States Citizenship Proofs",
                "purpose": "We need you to prove your US citizenship.",
                "rule": "all",
                "from": "C"
              },
              {
                "name": "European Union Citizenship Proofs",
                "purpose": "We need you to prove you are a citizen of an EU member state.",
                "rule": "all",
                "from": "D"
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun submissionRequirementParsing() {
        Json.decodeFromString<List<SubmissionRequirement>>(submissionRequirement)
        Json.decodeFromString<List<SubmissionRequirement>>(submissionRequirements)
    }

}
