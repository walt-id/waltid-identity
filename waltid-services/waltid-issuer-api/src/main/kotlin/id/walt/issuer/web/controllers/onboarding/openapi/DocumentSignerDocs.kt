package id.walt.issuer.web.controllers.onboarding.openapi

import id.walt.issuer.services.onboarding.models.DocumentSignerOnboardingRequest
import id.walt.issuer.services.onboarding.models.DocumentSignerOnboardingResponse
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import kotlinx.serialization.json.Json

object DocumentSignerDocs {

    fun requestConfig(): RequestConfig.() -> Unit = {
        body<DocumentSignerOnboardingRequest> {

            description =
                "Input for issuing a new Document Signer (DS) certificate. The request must reference an IACA signer\n" +
                        "(certificate data and signing key) and provide subject metadata and revocation configuration for the generated DS certificate.\n"
            required = true

            example(
                name = "Required data fields & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Custom validity period & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl",
                            "notBefore": "2026-01-01T00:00:00Z",
                            "notAfter": "2027-01-01T00:00:00Z"
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "All data fields & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "GR",
                                "commonName": "Η καλυτερότερη αρχή πιστοποίησης στον κόσμο",
                                "issuerAlternativeNameConf": {
                                    "email": "iaca@gov.gr",
                                    "uri": "https://iaca.gov.gr"
                                },
                                "stateOrProvinceName": "Αττική",
                                "organizationName": "Υπουργείο Μεταφορών",
                                "notBefore": "2026-01-01T00:00:00Z",
                                "notAfter": "2040-05-01T00:00:00Z",
                                "crlDistributionPointUri": "https://crl.gov.gr/iaca.crl"
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "GR",
                            "commonName": "Το καλυτερότερο παράρτημα του Υπουργείου Μεταφορών",
                            "crlDistributionPointUri": "https://crl.gov.gr/ds.crl",
                            "stateOrProvinceName": "Αττική",
                            "organizationName": "Υπουργείο Μεταφορών",
                            "localityName": "Χολαργός",
                            "notBefore": "2026-01-01T00:00:10Z",
                            "notAfter": "2026-06-01T00:00:00Z"
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 TSE signing key with AppRole (Auth)"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "tse",
                            "config": {
                                "server": "http://127.0.0.1:8200/v1/transit",
                                "auth": {
                                    "roleId": "9ec67fde-412a-d000-66fd-ba433560f092",
                                    "secretId": "65015f17-1f22-39f8-9c70-85af514e98f1"
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 TSE signing key with Username & Password (Auth)"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "tse",
                            "config": {
                                "server": "http://127.0.0.1:8200/v1/transit",
                                "auth": {
                                    "userpassPath": "userpass",
                                    "username": "myuser",
                                    "password": "mypassword"
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 OCI signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "oci",
                            "config": {
                                "vaultId": "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
                                "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 OCI REST API signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "oci",
                            "config": {
                                "tenancyOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
                                "compartmentOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
                                "userOcid": "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q",
                                "fingerprint": "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
                                "managementEndpoint": "entaftlvaaemy-management.kms.eu-frankfurt-1.oraclecloud.com",
                                "cryptoEndpoint": "entaftlvaaemy-crypto.kms.eu-frankfurt-1.oraclecloud.com",
                                "signingKeyPem": "-----BEGIN PRIVATE KEY-----\n\n-----END PRIVATE KEY-----\n"
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 AWS REST API signing key with AccessKey (Auth)"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "aws-rest-api",
                            "config": {
                                "auth": {
                                    "accessKeyId": "AKIA........QU5F",
                                    "secretAccessKey": "6YDr..................7Sr",
                                    "region": "eu-central-1"
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 AWS REST API signing key with Role (Auth)"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "aws-rest-api",
                            "config": {
                                "auth": {
                                    "roleName": "access",
                                    "region": "eu-central-1"
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            }

            example(
                name = "Required data fields & secp256r1 Azure REST API signing key"
            ) {
                value = Json.decodeFromString<DocumentSignerOnboardingRequest>(
                    """
                    {
                        "iacaSigner": {
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example IACA",
                                "notBefore": "2025-05-28T12:23:01Z",
                                "notAfter": "2040-05-24T12:23:01Z",                                
                                "issuerAlternativeNameConf": {
                                    "uri": "https://iaca.example.com"
                                }
                            },
                            "iacaKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M",
                                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs"
                                }
                            }
                        },
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example DS",
                            "crlDistributionPointUri": "https://iaca.example.com/crl"
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "azure",
                            "config": {
                                "auth": {
                                    "clientId": "client id",
                                    "clientSecret": "client secret",
                                    "tenantId": "tenant id",
                                    "keyVaultUrl": "url to the vault"
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            }
        }
    }

    fun responsesConfig(): ResponsesConfig.() -> Unit = {
        "200" to {
            description = "Successful Document Signer onboarding response"

            body<DocumentSignerOnboardingResponse> {

                example(
                    name = "Required data fields & local secp256r1 JWK signing key"
                ) {
                    value = Json.decodeFromString<DocumentSignerOnboardingResponse>(
                        """
                        {
                            "documentSignerKey": {
                                "type": "jwk",
                                "jwk": {
                                    "kty": "EC",
                                    "d": "ZSHgIcRvbwV9s224kHUaFqkEPShCAdwXocGl_w3M42Q",
                                    "crv": "P-256",
                                    "kid": "pX99OZjL2iNqM7OMkE1r1rYyuAObvPntewcDHdc2bMM",
                                    "x": "GWKpdL3jPoPJ5wKgSA-jxS2jgp-ZUDE6sIQbeB86vF0",
                                    "y": "F3xAwH96_xVciV7mFQslU_eRQgP-5pSZiNf8bjMoGfo"
                                }
                            },
                            "certificatePEM": "-----BEGIN CERTIFICATE-----\nMIICCDCCAa2gAwIBAgIUDo8kr194t6sttt6KL3YcnMtcaYYwCgYIKoZIzj0EAwIwJDELMAkGA1UEBhMCVVMxFTATBgNVBAMMDEV4YW1wbGUgSUFDQTAeFw0yNTA1MjkwNzE4MzlaFw0yNjA4MjkwNzE4MzlaMCIxCzAJBgNVBAYTAlVTMRMwEQYDVQQDDApFeGFtcGxlIERTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEGWKpdL3jPoPJ5wKgSA+jxS2jgp+ZUDE6sIQbeB86vF0XfEDAf3r/FVyJXuYVCyVT95FCA/7mlJmI1/xuMygZ+qOBvjCBuzAfBgNVHSMEGDAWgBSMIxGx+iVN4rkOzoyo5aPk3HTUFDAdBgNVHQ4EFgQU7S49LSeg/e0onfT44FVbL/rSKnswDgYDVR0PAQH/BAQDAgeAMCMGA1UdEgQcMBqGGGh0dHBzOi8vaWFjYS5leGFtcGxlLmNvbTAVBgNVHSUBAf8ECzAJBgcogYxdBQECMC0GA1UdHwQmMCQwIqAgoB6GHGh0dHBzOi8vaWFjYS5leGFtcGxlLmNvbS9jcmwwCgYIKoZIzj0EAwIDSQAwRgIhAMuSq75BPBXXBWGtIMd57fhRqpKf3Yzl3ldDdoQsK2xEAiEA/dmWLMLiJPV3UzmQS5MUHtn611z0VlL/k3YAdaVJ51c=\n-----END CERTIFICATE-----\n",
                            "certificateData": {
                                "country": "US",
                                "commonName": "Example DS",
                                "notBefore": "2025-05-29T07:18:39Z",
                                "notAfter": "2026-05-29T07:18:39Z",                                      
                                "crlDistributionPointUri": "https://iaca.example.com/crl"
                            }
                        }
                    """.trimIndent()
                    )
                }
            }
        }

        "400" to {
            description = "Bad request"
        }

    }
}