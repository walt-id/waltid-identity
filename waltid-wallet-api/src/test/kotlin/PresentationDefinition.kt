class PresentationDefinition {
    companion object {
        val minimalPresentationDefinition =
            """
                {
                  "request_credentials": [
                    "OpenBadgeCredential"
                  ]
                }
            """.trimIndent()
        
        val vpPoliciesDefinition =
            """
                {
                  "vp_policies": [
                    {
                      "policy": "minimum-credentials",
                      "args": 2
                    },
                    {
                      "policy": "maximum-credentials",
                      "args": 100
                    }
                  ],
                  "request_credentials": [
                    "OpenBadgeCredential",
                    "VerifiableId"
                  ]
                }
            """.trimIndent()
    }
}