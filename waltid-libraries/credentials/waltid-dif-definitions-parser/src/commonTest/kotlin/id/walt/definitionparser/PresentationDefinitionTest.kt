package id.walt.definitionparser

import kotlinx.serialization.json.Json
import kotlin.test.Test

class PresentationDefinitionTest {

    //language=JSON
    val minimalExample = """
{
  "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
  "input_descriptors": [
    {
      "id": "wa_driver_license",
      "name": "Washington State Business License",
      "purpose": "We can only allow licensed Washington State business representatives into the WA Business Conference",
      "constraints": {
        "fields": [
          {
            "path": [
              "${'$'}.credentialSubject.dateOfBirth",
              "${'$'}.credentialSubject.dob",
              "${'$'}.vc.credentialSubject.dateOfBirth",
              "${'$'}.vc.credentialSubject.dob"
            ]
          }
        ]
      }
    }
  ]
}
""".trimIndent()

    //language=JSON
    val filterByCredentialType = """
{
  "id": "first simple example",
  "input_descriptors": [
    {
      "id": "A specific type of VC",
      "name": "A specific type of VC",
      "purpose": "We want a VC of this type",
      "constraints": {
        "fields": [
          {
            "path": [
              "${'$'}.type"
            ],
            "filter": {
              "type": "array",
              "contains": {
                "type": "string",
                "pattern": "^<the type of VC e.g. degree certificate>${'$'}"
              }
            }
          }
        ]
      }
    }
  ]
}
""".trimIndent()

    //language=JSON
    val twoFiltersSimplified = """
{
  "id": "Scalable trust example",
  "input_descriptors": [
    {
      "id": "any type of credit card from any bank",
      "name": "any type of credit card from any bank",
      "purpose": "Please provide your credit card details",
      "constraints": {
        "fields": [
          {
            "path": [
              "${'$'}.termsOfUse.type"
            ],
            "filter": {
              "type": "string",
              "pattern": "^https://train.trust-scheme.de/info${'$'}"
            }
          },
          {
            "path": [
              "${'$'}.termsOfUse.trustScheme"
            ],
            "filter": {
              "type": "string",
              "pattern": "^worldbankfederation.com${'$'}"
            }
          },
          {
            "path": [
              "${'$'}.type"
            ],
            "filter": {
              "type": "string",
              "pattern": "^creditCard${'$'}"
            }
          }
        ]
      }
    }
  ]
}
"""

    //language=JSON
    val twoFiltersRealWorld = """
{
  "id": "Scalable trust example",
  "input_descriptors": [
    {
      "id": "any type of credit card from any bank",
      "name": "any type of credit card from any bank",
      "purpose": "Please provide your credit card details",
      "constraints": {
        "fields": [
          {
            "path": [
              "${'$'}.termsOfUse"
            ],
            "filter": {
              "${'$'}defs": {
                "typeString": {
                  "type": "string",
                  "pattern": "^https://train.trust-scheme.de/info${'$'}"
                },
                "typeStringOrArray": {
                  "anyOf": [
                    {
                      "${'$'}ref": "#/${'$'}defs/typeString"
                    },
                    {
                      "type": "array",
                      "contains": {
                        "${'$'}ref": "#/${'$'}defs/typeString"
                      }
                    }
                  ]
                },
                "trustSchemeString": {
                  "type": "string",
                  "pattern": "^worldbankfederation.com${'$'}"
                },
                "trustSchemeStringOrArray": {
                  "anyOf": [
                    {
                      "${'$'}ref": "#/${'$'}defs/trustSchemeString"
                    },
                    {
                      "type": "array",
                      "contains": {
                        "${'$'}ref": "#/${'$'}defs/trustSchemeString"
                      }
                    }
                  ]
                },
                "tosObject": {
                  "type": "object",
                  "required": [
                    "type",
                    "trustScheme"
                  ],
                  "properties": {
                    "type": {
                      "${'$'}ref": "#/${'$'}defs/typeStringOrArray"
                    },
                    "trustScheme": {
                      "${'$'}ref": "#/${'$'}defs/trustSchemeStringOrArray"
                    }
                  }
                },
                "tosObjectOrArray": {
                  "anyOf": [
                    {
                      "${'$'}ref": "#/${'$'}defs/tosObject"
                    },
                    {
                      "type": "array",
                      "contains": {
                        "${'$'}ref": "#/${'$'}defs/tosObject"
                      }
                    }
                  ]
                }
              },
              "${'$'}ref": "#/${'$'}defs/tosObjectOrArray"
            }
          },
          {
            "path": [
              "${'$'}.type"
            ],
            "filter": {
              "type": "array",
              "contains": {
                "type": "string",
                "pattern": "^creditCard${'$'}"
              }
            }
          }
        ]
      }
    }
  ]
}
""".trimIndent()

    //language=JSON
    val formats = """
{
  "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
  "input_descriptors": [],
  "format": {
    "jwt": {
      "alg": [
        "EdDSA",
        "ES256K",
        "ES384"
      ]
    },
    "jwt_vc": {
      "alg": [
        "ES256K",
        "ES384"
      ]
    },
    "jwt_vp": {
      "alg": [
        "EdDSA",
        "ES256K"
      ]
    },
    "ldp_vc": {
      "proof_type": [
        "JsonWebSignature2020",
        "Ed25519Signature2018",
        "EcdsaSecp256k1Signature2019",
        "RsaSignature2018"
      ]
    },
    "ldp_vp": {
      "proof_type": [
        "Ed25519Signature2018"
      ]
    },
    "ldp": {
      "proof_type": [
        "RsaSignature2018"
      ]
    }
  }
}
""".trimIndent()

    //language=JSON
    val singleGroupExample = """
        {
          "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
          "submission_requirements": [
            {
              "name": "Citizenship Information",
              "rule": "pick",
              "count": 1,
              "from": "A"
            }
          ],
          "input_descriptors": [
            {
              "id": "citizenship_input_1",
              "name": "EU Driver's License",
              "group": [
                "A"
              ],
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "https://eu.com/claims/DriversLicense.json"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.issuer",
                      "${'$'}.vc.issuer",
                      "${'$'}.iss"
                    ],
                    "purpose": "We can only accept digital driver's licenses issued by national authorities of member states or trusted notarial auditors.",
                    "filter": {
                      "type": "string",
                      "pattern": "^did:example:gov1${'$'}|^did:example:gov2${'$'}"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.dob",
                      "${'$'}.vc.credentialSubject.dob",
                      "${'$'}.dob"
                    ],
                    "filter": {
                      "type": "string",
                      "format": "date"
                    }
                  }
                ]
              }
            },
            {
              "id": "citizenship_input_2",
              "name": "US Passport",
              "group": [
                "A"
              ],
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "hub://did:foo:123/Collections/schema.us.gov/passport.json"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.birth_date",
                      "${'$'}.vc.credentialSubject.birth_date",
                      "${'$'}.birth_date"
                    ],
                    "filter": {
                      "type": "string",
                      "format": "date"
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    //language=JSON
    val multiGroupExample = """
        {
          "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
          "submission_requirements": [
            {
              "name": "Banking Information",
              "purpose": "We can only remit payment to a currently-valid bank account in the US, Germany or France.",
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
              "name": "Eligibility to Drive on US Roads",
              "purpose": "We need to verify eligibility to drive on US roads via US or EU driver's license, but no biometric or identifying information contained there.",
              "rule": "pick",
              "count": 1,
              "from": "C"
            }
          ],
          "input_descriptors": [
            {
              "id": "banking_input_1",
              "name": "Bank Account Information",
              "purpose": "Bank Account required to remit payment.",
              "group": [
                "A"
              ],
              "constraints": {
                "limit_disclosure": "required",
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema",
                      "${'$'}.vc.credentialSchema"
                    ],
                    "filter": {
                      "allOf": [
                        {
                          "type": "array",
                          "contains": {
                            "type": "object",
                            "properties": {
                              "id": {
                                "type": "string",
                                "pattern": "^https://bank-standards.example.com#accounts${'$'}"
                              }
                            },
                            "required": [
                              "id"
                            ]
                          }
                        },
                        {
                          "type": "array",
                          "contains": {
                            "type": "object",
                            "properties": {
                              "id": {
                                "type": "string",
                                "pattern": "^https://bank-standards.example.com#investments${'$'}"
                              }
                            },
                            "required": [
                              "id"
                            ]
                          }
                        }
                      ]
                    }
                  },
                  {
                    "path": [
                      "${'$'}.issuer",
                      "${'$'}.vc.issuer",
                      "${'$'}.iss"
                    ],
                    "purpose": "We can only verify bank accounts if they are attested by a trusted bank, auditor or regulatory authority.",
                    "filter": {
                      "type": "string",
                      "pattern": "^did:example:123${'$'}|^did:example:456${'$'}"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.account[*].account_number",
                      "${'$'}.vc.credentialSubject.account[*].account_number",
                      "${'$'}.account[*].account_number"
                    ],
                    "purpose": "We can only remit payment to a currently-valid bank account in the US, France, or Germany, submitted as an ABA Acct # or IBAN.",
                    "filter": {
                      "type": "string",
                      "pattern": "^[0-9]{10-12}|^(DE|FR)[0-9]{2}\\s?([0-9a-zA-Z]{4}\\s?){4}[0-9a-zA-Z]{2}${'$'}"
                    },
                    "intent_to_retain": true
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.portfolio_value",
                      "${'$'}.vc.credentialSubject.portfolio_value",
                      "${'$'}.portfolio_value"
                    ],
                    "purpose": "A current portfolio value of at least one million dollars is required to insure your application",
                    "filter": {
                      "type": "number",
                      "minimum": 1000000
                    },
                    "intent_to_retain": true
                  }
                ]
              }
            },
            {
              "id": "banking_input_2",
              "name": "Bank Account Information",
              "purpose": "We can only remit payment to a currently-valid bank account.",
              "group": [
                "A"
              ],
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "pattern": "^https://bank-schemas.org/1.0.0/accounts.json|https://bank-schemas.org/2.0.0/accounts.json${'$'}"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.issuer",
                      "${'$'}.vc.issuer",
                      "${'$'}.iss"
                    ],
                    "purpose": "We can only verify bank accounts if they are attested by a trusted bank, auditor or regulatory authority.",
                    "filter": {
                      "type": "string",
                      "pattern": "^did:example:123${'$'}|^did:example:456${'$'}"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.account[*].id",
                      "${'$'}.vc.credentialSubject.account[*].id",
                      "${'$'}.account[*].id"
                    ],
                    "purpose": "We can only remit payment to a currently-valid bank account in the US, France, or Germany, submitted as an ABA Acct # or IBAN.",
                    "filter": {
                      "type": "string",
                      "pattern": "^[0-9]{10-12}|^(DE|FR)[0-9]{2}\\s?([0-9a-zA-Z]{4}\\s?){4}[0-9a-zA-Z]{2}${'$'}"
                    },
                    "intent_to_retain": true
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.account[*].route",
                      "${'$'}.vc.credentialSubject.account[*].route",
                      "${'$'}.account[*].route"
                    ],
                    "purpose": "We can only remit payment to a currently-valid account at a US, Japanese, or German federally-accredited bank, submitted as an ABA RTN or SWIFT code.",
                    "filter": {
                      "type": "string",
                      "pattern": "^[0-9]{9}|^([a-zA-Z]){4}([a-zA-Z]){2}([0-9a-zA-Z]){2}([0-9a-zA-Z]{3})?${'$'}"
                    },
                    "intent_to_retain": true
                  }
                ]
              }
            },
            {
              "id": "employment_input",
              "name": "Employment History",
              "purpose": "We are only verifying one current employment relationship, not any other information about employment.",
              "group": [
                "B"
              ],
              "constraints": {
                "limit_disclosure": "required",
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema",
                      "${'$'}.vc.credentialSchema"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "https://business-standards.org/schemas/employment-history.json"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.jobs[*].active"
                    ],
                    "filter": {
                      "type": "boolean",
                      "pattern": "true"
                    }
                  }
                ]
              }
            },
            {
              "id": "drivers_license_input_1",
              "name": "EU Driver's License",
              "group": [
                "C"
              ],
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "https://schema.eu/claims/DriversLicense.json"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.issuer",
                      "${'$'}.vc.issuer",
                      "${'$'}.iss"
                    ],
                    "purpose": "We can only accept digital driver's licenses issued by national authorities of EU member states or trusted notarial auditors.",
                    "filter": {
                      "type": "string",
                      "pattern": "did:example:gov1|did:example:gov2"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.dob",
                      "${'$'}.vc.credentialSubject.dob",
                      "${'$'}.dob"
                    ],
                    "purpose": "We must confirm that the driver was at least 21 years old on April 16, 2020.",
                    "filter": {
                      "type": "string",
                      "format": "date"
                    }
                  }
                ]
              }
            },
            {
              "id": "drivers_license_input_2",
              "name": "Driver's License from one of 50 US States",
              "group": [
                "C"
              ],
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "hub://did:foo:123/Collections/schema.us.gov/american_drivers_license.json"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.issuer",
                      "${'$'}.vc.issuer",
                      "${'$'}.iss"
                    ],
                    "purpose": "We can only accept digital driver's licenses issued by the 50 US states' automative affairs agencies.",
                    "filter": {
                      "type": "string",
                      "pattern": "did:example:gov1|did:web:dmv.ca.gov|did:example:oregonDMV"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.credentialSubject.birth_date",
                      "${'$'}.vc.credentialSubject.birth_date",
                      "${'$'}.birth_date"
                    ],
                    "purpose": "We must confirm that the driver was at least 21 years old on April 16, 2020.",
                    "filter": {
                      "type": "string",
                      "format": "date",
                      "forrmatMaximum": "1999-05-16"
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    //language=JSON
    val descriptorIdToken = """
        {
          "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
          "input_descriptors": [
            {
              "id": "employment_input_xyz_gov",
              "group": [
                "B"
              ],
              "name": "Verify XYZ Government Employment",
              "purpose": "Verifying current employment at XYZ Government agency as proxy for permission to access this resource",
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "${'$'}.credentialSchema.id",
                      "${'$'}.vc.credentialSchema.id"
                    ],
                    "filter": {
                      "type": "string",
                      "const": "https://login.idp.com/xyz.gov/.well-known/openid-configuration"
                    }
                  },
                  {
                    "path": [
                      "${'$'}.status"
                    ],
                    "filter": {
                      "type": "string",
                      "pattern": "^active${'$'}"
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    //language=JSON
    val verifiableCredentialExpiration = """
        {
          "id": "drivers_license_information",
          "name": "Verify Valid License",
          "purpose": "We need you to show that your driver's license will be valid through December of this year.",
          "constraints": {
            "fields": [
              {
                "path": ["${'$'}.credentialSchema.id", "${'$'}.vc.credentialSchema.id"],
                "filter": {
                  "type": "string",
                  "const": "https://yourwatchful.gov/drivers-license-schema.json"
                }
              },
              {
                "path": ["${'$'}.expirationDate"],
                "filter": {
                  "type": "string",
                  "format": "date-time"
                }
              }
            ]
          }
        }
    """.trimIndent()

    //language=JSON
    val verifiableCredentialRevocationStatus = """
        {
          "id": "drivers_license_information",
          "name": "Verify Valid License",
          "purpose": "We need to know that your license has not been revoked.",
          "constraints": {
            "fields": [
              {
                "path": ["${'$'}.credentialSchema.id", "${'$'}.vc.credentialSchema.id"],
                "filter": {
                  "type": "string",
                  "const": "https://yourwatchful.gov/drivers-license-schema.json"
                }
              },
              {
                "path": ["${'$'}.credentialStatus"]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun testPresentationDefinitionParsing() {
        println("minimalExample")
        Json.decodeFromString<PresentationDefinition>(minimalExample)

        println("filterByCredentialType")
        Json.decodeFromString<PresentationDefinition>(filterByCredentialType)

        println("twoFiltersSimplified")
        Json.decodeFromString<PresentationDefinition>(twoFiltersSimplified)

        println("twoFiltersRealWorld")
        Json.decodeFromString<PresentationDefinition>(twoFiltersRealWorld)

        println("formats")
        Json.decodeFromString<PresentationDefinition>(formats)


        println("singleGroupExample")
        Json.decodeFromString<PresentationDefinition>(singleGroupExample)

        println("multiGroupExample")
        Json.decodeFromString<PresentationDefinition>(multiGroupExample)

        println("descriptorIdToken")
        Json.decodeFromString<PresentationDefinition>(descriptorIdToken)

        println("verifiableCredentialExpiration")
        Json.decodeFromString<PresentationDefinition.InputDescriptor>(verifiableCredentialExpiration)
        println("verifiableCredentialRevocationStatus")
        Json.decodeFromString<PresentationDefinition.InputDescriptor>(verifiableCredentialRevocationStatus)
    }

}
