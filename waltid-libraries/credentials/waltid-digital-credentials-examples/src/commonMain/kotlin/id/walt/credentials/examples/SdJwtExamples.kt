package id.walt.credentials.examples

object SdJwtExamples {

    // SD-JWT
    //language=JSON
    val unsignedSdJwtVcNoDisclosablesExample = """
        {
          "vct": "https://credentials.example.com/identity_credential",
          "given_name": "John",
          "family_name": "Doe",
          "email": "johndoe@example.com",
          "phone_number": "+1-202-555-0101",
          "address": {
            "street_address": "123 Main St",
            "locality": "Anytown",
            "region": "Anystate",
            "country": "US"
          },
          "birthdate": "1940-01-01",
          "is_over_18": true,
          "is_over_21": true,
          "is_over_65": true
        }
    """.trimIndent()

    // SD-JWT
    //language=JSON
    val sdJwtVcWithDisclosablesExample = """
        {
          "_sd": [
            "09vKrJMOlyTWM0sjpu_pdOBVBQ2M1y3KhpH515nXkpY",
            "2rsjGbaC0ky8mT0pJrPioWTq0_daw1sX76poUlgCwbI",
            "EkO8dhW0dHEJbvUHlE_VCeuC9uRELOieLZhh7XbUTtA",
            "IlDzIKeiZdDwpqpK6ZfbyphFvz5FgnWa-sN6wqQXCiw",
            "JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE",
            "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI",
            "TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo",
            "jdrTE8YcbY4EifugihiAe_BPekxJQZICeiUQwY9QqxI",
            "jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4"
          ],
          "iss": "https://example.com/issuer",
          "iat": 1683000000,
          "exp": 1883000000,
          "vct": "https://credentials.example.com/identity_credential",
          "_sd_alg": "sha-256",
          "cnf": {
            "jwk": {
              "kty": "EC",
              "crv": "P-256",
              "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
              "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
            }
          }
        }
    """.trimIndent()

    //language=JSON
    val sdJwtVcExpiredExample = """
        {
          "_sd": [
            "09vKrJMOlyTWM0sjpu_pdOBVBQ2M1y3KhpH515nXkpY",
            "2rsjGbaC0ky8mT0pJrPioWTq0_daw1sX76poUlgCwbI",
            "EkO8dhW0dHEJbvUHlE_VCeuC9uRELOieLZhh7XbUTtA",
            "IlDzIKeiZdDwpqpK6ZfbyphFvz5FgnWa-sN6wqQXCiw",
            "JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE",
            "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI",
            "TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo",
            "jdrTE8YcbY4EifugihiAe_BPekxJQZICeiUQwY9QqxI",
            "jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4"
          ],
          "iss": "https://example.com/issuer",
          "iat": 1683000000,
          "exp": 1697958248,
          "vct": "https://credentials.example.com/identity_credential",
          "_sd_alg": "sha-256",
          "cnf": {
            "jwk": {
              "kty": "EC",
              "crv": "P-256",
              "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
              "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
            }
          }
        }
    """.trimIndent()

    //language=JSON
    val sdJwtVcFutureExample = """
        {
          "_sd": [
            "09vKrJMOlyTWM0sjpu_pdOBVBQ2M1y3KhpH515nXkpY",
            "2rsjGbaC0ky8mT0pJrPioWTq0_daw1sX76poUlgCwbI",
            "EkO8dhW0dHEJbvUHlE_VCeuC9uRELOieLZhh7XbUTtA",
            "IlDzIKeiZdDwpqpK6ZfbyphFvz5FgnWa-sN6wqQXCiw",
            "JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE",
            "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI",
            "TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo",
            "jdrTE8YcbY4EifugihiAe_BPekxJQZICeiUQwY9QqxI",
            "jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4"
          ],
          "iss": "https://example.com/issuer",
          "iat": 2076650025,
          "exp": 2392269225,
          "vct": "https://credentials.example.com/identity_credential",
          "_sd_alg": "sha-256",
          "cnf": {
            "jwk": {
              "kty": "EC",
              "crv": "P-256",
              "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
              "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
            }
          }
        }
    """.trimIndent()

    /*val sdJwtVcSignedExample = """
        eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImRjK3NkLWp3dCIsICJraWQiOiAiZG9jLXNpZ25lci0wNS0yNS0yMDIyIn0.eyJfc2QiOiBbIjA5dktySk1PbHlUV00wc2pwdV9wZE9CVkJRMk0xeTNLaHBINTE1blhrcFkiLCAiMnJzakdiYUMwa3k4bVQwcEpyUGlvV1RxMF9kYXcxc1g3NnBvVWxnQ3diSSIsICJFa084ZGhXMGRIRUpidlVIbEVfVkNldUM5dVJFTE9pZUxaaGg3WGJVVHRBIiwgIklsRHpJS2VpWmREd3BxcEs2WmZieXBoRnZ6NUZnbldhLXNONndxUVhDaXciLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiamRyVEU4WWNiWTRFaWZ1Z2loaUFlX0JQZWt4SlFaSUNlaVVRd1k5UXF4SSIsICJqc3U5eVZ1bHdRUWxoRmxNXzNKbHpNYVNGemdsaFFHMERwZmF5UXdMVUs0Il0sICJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiLCAiaWF0IjogMTY4MzAwMDAwMCwgImV4cCI6IDE4ODMwMDAwMDAsICJ2Y3QiOiAiaHR0cHM6Ly9jcmVkZW50aWFscy5leGFtcGxlLmNvbS9pZGVudGl0eV9jcmVkZW50aWFsIiwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJjbmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJaeGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ.cWT4VMs1G0iqUt-ajx98dCwq0I4djdqC9vX6ELCpjYBNrhRNK6u3wds9cSwB8REuA1RRCE9BprDDyjOVDLgLvg~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImlzX292ZXJfMTgiLCB0cnVlXQ~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgImlzX292ZXJfMjEiLCB0cnVlXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgImlzX292ZXJfNjUiLCB0cnVlXQ~
    """.trimIndent()*/

    // SD-JWT **VCDM**
    //language=JSON
    val sdJwtVcDmExample = """
        {
          "@context": [
            "https://www.w3.org/2018/credentials/v1",
            "http://data.europa.eu/snb/model/context/edc-ap"
          ],
          "vct": "empl:europeanDigitalCredential",

          "id": "http://example.org/credential132",

          "valid_from": "2010-10-01T00:00:00",
          "valid_until": "2024-09-25T00:00:00",

          "authentic_source": {
            "id": "http://example.org/issuer565049",
            "type": "Organisation",
            "legalName": {
              "en": "some legal name",
              "fr": "un nom légal"
            },
            "eIDASIdentifier": {
              "id": "http://example.org/126839",
              "type": "LegalIdentifier",
              "notation": "126839",
              "spatial": {
                "id": "http://publications.europa.eu/resource/authority/country/FRA",
                "type": "Concept",
                "inScheme": {
                  "id": "http://publications.europa.eu/resource/authority/country",
                  "type": "ConceptScheme"
                }
              }
            },

            "location": {
              "id": "http://example.org/loc1",
              "type": "Location",
              "address": {
                "id": "http://example.org/add1",
                "type": "Address",
                "countryCode": {
                  "id": "http://publications.europa.eu/resource/authority/country/FRA",
                  "type": "Concept",
                  "inScheme": {
                    "id": "http://publications.europa.eu/resource/authority/country",
                    "type": "ConceptScheme"
                  }
                }
              }
            }
          },

          "credentialProfiles": {
            "id": "http://data.europa.eu/snb/credential/bdc47cb449",
            "type": "Concept",
            "inScheme": {
              "id": "http://data.europa.eu/snb/credential/25831c2",
              "type": "ConceptScheme"
            }
          },

          "displayParameter": {
            "id": "http://example.org/display1",
            "type": "DisplayParameter",
            "title": {
              "en": "Some kind of credential"
            },
            "primaryLanguage": {
              "id": "http://publications.europa.eu/resource/authority/language/ENG",
              "type": "Concept",
              "inScheme": {
                "id": "http://publications.europa.eu/resource/authority/language",
                "type": "ConceptScheme"
              }
            },

            "language": [
              {
                "id": "http://publications.europa.eu/resource/authority/language/ENG",
                "type": "Concept",
                "inScheme": {
                  "id": "http://publications.europa.eu/resource/authority/language",
                  "type": "ConceptScheme"
                }
              },
              {
                "id": "http://publications.europa.eu/resource/authority/language/FRA",
                "type": "Concept",
                "inScheme": {
                  "id": "http://publications.europa.eu/resource/authority/language",
                  "type": "ConceptScheme"
                }
              }
            ],

            "individualDisplay": [
              {
                "id": "http://example.org/individualDisplay1",
                "type": "IndividualDisplay",
                "language": {
                  "id": "http://publications.europa.eu/resource/authority/language/ENG",
                  "type": "Concept",
                  "inScheme": {
                    "id": "http://publications.europa.eu/resource/authority/language",
                    "type": "ConceptScheme"
                  }
                },
                "displayDetail": [
                  {
                    "id": "http://example.org/displayDetail1",
                    "type": "DisplayDetail",
                    "image": {
                      "id": "http://example.org/image1",
                      "type": "MediaObject",
                      "content": "",
                      "contentEncoding": {
                        "id": "http://data.europa.eu/snb/encoding/6146cde7dd",
                        "type": "Concept",
                        "inScheme": {
                          "id": "http://data.europa.eu/snb/encoding/25831c2",
                          "type": "ConceptScheme"
                        }
                      },
                      "contentType": {
                        "id": "http://publications.europa.eu/resource/authority/file-type/JPEG",
                        "type": "Concept",
                        "inScheme": {
                          "id": "http://publications.europa.eu/resource/authority/file-type",
                          "type": "ConceptScheme"
                        }
                      }
                    },
                    "page": 1
                  }
                ]
              }
            ]
          },

          "claims": {
            "id": "http://example.org/pid1",
            "type": "Person",
            "birthName": {
              "en": "Maxi"
            },
            "familyName": {
              "en": "Power"
            },
            "fullName": {
              "en": "Max Power"
            },
            "givenName": {
              "en": "Max"
            },
            "hasClaim": {
              "id": "http://example.org/cl1",
              "type": "LearningAchievement",
              "awardedBy": {
                "id": "http://example.org/awardingProcess1",
                "type": "AwardingProcess",
                "awardingBody": {
                  "id": "http://example.org/org1",
                  "type": "Organisation",
                  "legalName": {
                    "en": "some legal name of the organisation"
                  },
                  "location": {
                    "id": "http://example.org/loc2",
                    "type": "Location",
                    "address": {
                      "id": "http://example.org/add2",
                      "type": "Address",
                      "countryCode": {
                        "id": "http://publications.europa.eu/resource/authority/country/FRA",
                        "type": "Concept",
                        "inScheme": {
                          "id": "http://publications.europa.eu/resource/authority/country",
                          "type": "ConceptScheme"
                        }
                      }
                    }
                  }
                },
                "educationalSystemNote": {
                  "id": "http://example.org/someEducationalSystem",
                  "type": "Concept",
                  "definition": {
                    "en": "the definition of the the concept for the educational system"
                  }
                }
              },
              "title": {
                "en": "some kind of learning achievement",
                "fr": "une sorte de réussite scolaire"
              }
            }
          },

          "evidence": {
            "elm:evidence": {
              "id": "http://example.org/evidence123",
              "dcType": {
                "id": "http://data.europa.eu/snb/evidence-type/c_18016257",
                "type": "Concept",
                "inScheme": {
                  "id": "http://data.europa.eu/snb/evidence-type/25831c2",
                  "type": "ConceptScheme"
                }
              }
            }
          },

          "terms_of_use": {
            "elm:terms_of_use": {
              "id": "http://example.org/termsOfUse1",
              "type": "TermsOfUse"
            }
          },

          "status": {
            "elm:credential_status": {
              "id": "http://example.org/credentialStatus1",
              "type": "CredentialStatus"
            }
          }
        }
    """.trimIndent()

    val sdJwtVcSignedExample2 = """
        eyJraWQiOiJKMUZ3SlA4N0M2LVFOX1dTSU9tSkFRYzZuNUNRX2JaZGFGSjVHRG5XMVJrIiwidHlwIjoidmMrc2Qtand0IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJodHRwczovL3RyaWFsLmF1dGhsZXRlLm5ldCIsIl9zZCI6WyIwczNiazZYcC02ZW1rV3cxTFd2OWthaGk5YjNUV0c2NDJLV3dpZEZKeHlvIiwiM3BSUGNUUjItSkJySjhVRjVkZGhtcDhwbDA3MEpheWpoMWEzZVVWRDZNZyIsIjZ6UEtKS2pzc2Y0Q1JNNmhUeDZZVUdQdzRBbm1ZWHZocnFuZDlmdTZMcUkiLCJBVnFKdDdkcWNEVWZLNmJPSEg0UTFEODVfMVNmLXRwM0d0QlM1Mk1Bb3FVIiwiQldydzdVV2YzdjF4cjdOOXFoSFpXMHMwa0FERDVGbFgtUmNQV2dCZEFJOCIsIkhtcHVfdVo4dWo4b2ViMXIyaGg2YmdUY3dNbEJoVHNrUjIxR2tZZVd4TW8iLCJNQ1JpVkRYc3I3MTJ1WW9NRUtWeEJfMmFxX0oweENfa08yNTdwQ3N0RlB3IiwiTUc3WElRV1Y5RFE2dC12WDdmZERUblZ6bnpTZUwwY2gtX0NtNkkyM3ZDWSIsIlB5VEVrajdFdUhScGljdFk5Z1ZpQTVhcTBrYTd2SzJZdDRrX04wbzlTb3ciXSwiaWF0IjoxNzA1MDE4MjA1LCJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwiLCJfc2RfYWxnIjoic2hhLTI1NiJ9.ll4JdW-ksNDyVGx-OTueQYojpUYXhUZ6J31fFKGall2SsT5LQt-I5w24AiYhDvxYWRGRCmJF5UI-_3SpNE83wQ~WyJJZEJuZ2xIcF9URGRyeUwycGJLZVNBIiwic3ViIiwiMTAwNCJd~WyJDd0lYNU11clBMZ1VFRnA2U2JhM0dnIiwiZ2l2ZW5fbmFtZSIsIkluZ2EiXQ~WyJveUYtR3Q5LXVwa1FkU0ZMX0pTekNnIiwiZmFtaWx5X25hbWUiLCJTaWx2ZXJzdG9uZSJd~WyJMZG9oSjQ5d2gwcTBubWNJUG92SVhnIiwiYmlydGhkYXRlIiwiMTk5MS0xMS0wNiJd~
    """.trimIndent()

    val sdJwtVcSignedExample2WithoutProvidedDisclosures = """
        eyJraWQiOiJKMUZ3SlA4N0M2LVFOX1dTSU9tSkFRYzZuNUNRX2JaZGFGSjVHRG5XMVJrIiwidHlwIjoidmMrc2Qtand0IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJodHRwczovL3RyaWFsLmF1dGhsZXRlLm5ldCIsIl9zZCI6WyIwczNiazZYcC02ZW1rV3cxTFd2OWthaGk5YjNUV0c2NDJLV3dpZEZKeHlvIiwiM3BSUGNUUjItSkJySjhVRjVkZGhtcDhwbDA3MEpheWpoMWEzZVVWRDZNZyIsIjZ6UEtKS2pzc2Y0Q1JNNmhUeDZZVUdQdzRBbm1ZWHZocnFuZDlmdTZMcUkiLCJBVnFKdDdkcWNEVWZLNmJPSEg0UTFEODVfMVNmLXRwM0d0QlM1Mk1Bb3FVIiwiQldydzdVV2YzdjF4cjdOOXFoSFpXMHMwa0FERDVGbFgtUmNQV2dCZEFJOCIsIkhtcHVfdVo4dWo4b2ViMXIyaGg2YmdUY3dNbEJoVHNrUjIxR2tZZVd4TW8iLCJNQ1JpVkRYc3I3MTJ1WW9NRUtWeEJfMmFxX0oweENfa08yNTdwQ3N0RlB3IiwiTUc3WElRV1Y5RFE2dC12WDdmZERUblZ6bnpTZUwwY2gtX0NtNkkyM3ZDWSIsIlB5VEVrajdFdUhScGljdFk5Z1ZpQTVhcTBrYTd2SzJZdDRrX04wbzlTb3ciXSwiaWF0IjoxNzA1MDE4MjA1LCJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwiLCJfc2RfYWxnIjoic2hhLTI1NiJ9.ll4JdW-ksNDyVGx-OTueQYojpUYXhUZ6J31fFKGall2SsT5LQt-I5w24AiYhDvxYWRGRCmJF5UI-_3SpNE83wQ
    """.trimIndent()

}
