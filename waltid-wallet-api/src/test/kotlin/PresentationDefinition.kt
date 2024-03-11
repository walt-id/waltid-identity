//class PresentationDefinition {
//    companion object {
//        val minimalPresentationDefinition =
//            """
//                {
//                  "request_credentials": [
//                    "OpenBadgeCredential"
//                  ]
//                }
//            """.trimIndent()
//
//        val vpPoliciesDefinition =
//            """
//                {
//                  "vp_policies": [
//                    {
//                      "policy": "minimum-credentials",
//                      "args": 2
//                    },
//                    {
//                      "policy": "maximum-credentials",
//                      "args": 100
//                    }
//                  ],
//                  "request_credentials": [
//                    "OpenBadgeCredential",
//                    "VerifiableId"
//                  ]
//                }
//            """.trimIndent()
//
//        val vpPoliciesWithGlobalVcDefinition =
//            """
//                {
//                  "vp_policies": [
//                    {
//                      "policy": "minimum-credentials",
//                      "args": 2
//                    },
//                    {
//                      "policy": "maximum-credentials",
//                      "args": 100
//                    }
//                  ],
//                  "vc_policies": [
//                    "signature",
//                    "expired",
//                    "not-before"
//                  ],
//                  "request_credentials": [
//                    "OpenBadgeCredential",
//                    "VerifiableId"
//                  ]
//                }
//            """.trimIndent()
//        val vpVcSpecificCredentialPolicies =
//            """
//                {
//                  "vp_policies": [
//                    {
//                      "policy": "minimum-credentials",
//                      "args": 2
//                    },
//                    {
//                      "policy": "maximum-credentials",
//                      "args": 100
//                    }
//                  ],
//                  "vc_policies": [
//                    "signature",
//                    "expired",
//                    "not-before"
//                  ],
//                  "request_credentials": [
//                    "VerifiableId",
//                    "ProofOfResidence",
//                    {
//                      "credential": "OpenBadgeCredential",
//                      "policies": [
//                        "signature",
//                        {
//                          "policy": "schema",
//                          "args": {
//                            "type": "object",
//                            "required": [
//                              "issuer"
//                            ],
//                            "properties": {
//                              "issuer": {
//                                "type": "object"
//                              }
//                            }
//                          }
//                        }
//                      ]
//                    }
//                  ]
//                }
//            """.trimIndent()
//
//        val explicitPresentationDefinitionMaximumExample =
//            """
//                {
//                  "vp_policies": [
//                    {
//                      "policy": "minimum-credentials",
//                      "args": 2
//                    },
//                    {
//                      "policy": "maximum-credentials",
//                      "args": 100
//                    }
//                  ],
//                  "vc_policies": [
//                    "signature",
//                    "expired",
//                    "not-before"
//                  ],
//                  "request_credentials": [
//                    "VerifiableId",
//                    "ProofOfResidence",
//                    {
//                      "credential": "OpenBadgeCredential",
//                      "policies": [
//                        "signature",
//                        {
//                          "policy": "schema",
//                          "args": {
//                            "type": "object",
//                            "required": [
//                              "issuer"
//                            ],
//                            "properties": {
//                              "issuer": {
//                                "type": "object"
//                              }
//                            }
//                          }
//                        }
//                      ]
//                    }
//                  ],
//                  "presentation_definition": {
//                    "id": "<automatically assigned>",
//                    "input_descriptors": [
//                      {
//                        "id": "VerifiableId",
//                        "format": {
//                          "jwt_vc_json": {
//                            "alg": [
//                              "EdDSA"
//                            ]
//                          }
//                        },
//                        "constraints": {
//                          "fields": [
//                            {
//                              "path": [
//                                "${'$'}.type"
//                              ],
//                              "filter": {
//                                "type": "string",
//                                "pattern": "VerifiableId"
//                              }
//                            }
//                          ]
//                        }
//                      }
//                    ]
//                  }
//                }
//            """.trimIndent()
//
//        val presentationDefinitionPolicy =
//            """
//                {
//                  "vp_policies": [
//                    "signature",
//                    "expired",
//                    "not-before",
//                    "presentation-definition"
//                  ],
//                  "vc_policies": [
//                    "signature",
//                    "expired",
//                    "not-before"
//                  ],
//                  "request_credentials": [
//                    "ProofOfResidence",
//                    {
//                      "credential": "OpenBadgeCredential",
//                      "policies": [
//                        "signature",
//                        {
//                          "policy": "schema",
//                          "args": {
//                            "type": "object",
//                            "required": [
//                              "issuer"
//                            ],
//                            "properties": {
//                              "issuer": {
//                                "type": "object"
//                              }
//                            }
//                          }
//                        }
//                      ]
//                    }
//                  ]
//                }
//            """.trimIndent()
//    }
//}