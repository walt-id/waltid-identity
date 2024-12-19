package presentationexchange

object PresentationRequests {

    val w3cVcTypeCorrectJsonPath = """
        {
          "vp_policies": [
            "signature",
            "expired",
            "not-before",
            "presentation-definition"
          ],
          "vc_policies": [
            "signature",
            "expired",
            "not-before"
          ],
          "request_credentials": [
            {
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",              
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.vc.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

    val w3cVcTypeInvalidJsonPath = """
        {
          "vp_policies": [
            "signature",
            "expired",
            "not-before",
            "presentation-definition"
          ],
          "vc_policies": [
            "signature",
            "expired",
            "not-before"
          ],
          "request_credentials": [
            {
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

}