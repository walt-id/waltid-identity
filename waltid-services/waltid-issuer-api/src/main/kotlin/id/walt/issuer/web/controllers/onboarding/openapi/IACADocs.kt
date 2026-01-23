package id.walt.issuer.web.controllers.onboarding.openapi

import id.walt.issuer.services.onboarding.models.IACAOnboardingRequest
import id.walt.issuer.services.onboarding.models.IACAOnboardingResponse
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import kotlinx.serialization.json.Json

object IACADocs {

    fun requestConfig(): RequestConfig.() -> Unit = {
        body<IACAOnboardingRequest> {

            description =
                "Request payload for onboarding a new IACA (Issuing Authority Certification Authority) root certificate\n" +
                        "  in compliance with ISO/IEC 18013-5. The request includes metadata required to generate the self-signed\n" +
                        "  X.509 root certificate that will serve as a trust anchor in the mDL ecosystem."
            required = true

            example(
                name = "Required data fields only & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                      "certificateData": {
                        "country": "US",
                        "commonName": "Example IACA",
                        "issuerAlternativeNameConf": {
                          "uri": "https://iaca.example.com"
                        }
                      }
                    }
                """.trimIndent())
            }

            example(
                name = "Custom validity period & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "DE",
                            "commonName": "Bundesdruckerei IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://ca.bund.de"
                            },
                            "notBefore": "2026-01-01T00:00:00Z",
                            "notAfter": "2041-01-01T00:00:00Z"
                        }
                    }
                """.trimIndent())
            }

            example(
                name = "All data fields & local secp256r1 JWK signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "GR",
                            "commonName": "Η καλυτερότερη αρχή πιστοποίησης στον κόσμο",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.gov.gr",
                                "email": "iaca@gov.gr"
                            },
                            "stateOrProvinceName": "Αττική",
                            "organizationName": "Υπουργείο Μεταφορών",
                            "notBefore": "2026-01-01T00:00:00Z",
                            "notAfter": "2040-05-01T00:00:00Z",
                            "crlDistributionPointUri": "https://crl.gov.gr/iaca.crl"
                        }
                    }
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 TSE signing key with AppRole (Auth)"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 TSE signing key with Username & Password (Auth)"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 OCI signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
                        },
                        "ecKeyGenRequestParams": {
                            "backend": "oci",
                            "config": {
                                "vaultId": "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
                                "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
                            }
                        }
                    }
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 OCI REST API signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 AWS REST API signing key with AccessKey (Auth)"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 AWS REST API signing key with Role (Auth)"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }

            example(
                name = "Required data fields & secp256r1 Azure REST API signing key"
            ) {
                value = Json.decodeFromString<IACAOnboardingRequest>("""
                    {
                        "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "issuerAlternativeNameConf": {
                                "uri": "https://iaca.example.com"
                            }
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
                """.trimIndent())
            }
        }
    }

    fun responsesConfig(): ResponsesConfig.() -> Unit = {
        "200" to {
            description = "Successful IACA onboarding response"

            body<IACAOnboardingResponse> {

                example(
                    name = "Required data fields & local secp256r1 JWK signing key"
                ) {
                    value = Json.decodeFromString<IACAOnboardingResponse>("""
                        {
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
                          },
                          "certificatePEM": "-----BEGIN CERTIFICATE-----\nMIIBtTCCAVqgAwIBAgIUNlgkpoam39UxORhMNRkwuFzD9pQwCgYIKoZIzj0EAwIwJDELMAkGA1UEBhMCVVMxFTATBgNVBAMMDEV4YW1wbGUgSUFDQTAeFw0yNTA1MjgxMjIzMDFaFw00MDA1MjQxMjIzMDFaMCQxCzAJBgNVBAYTAlVTMRUwEwYDVQQDDAxFeGFtcGxlIElBQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASf9vUaZlNISGKgrfPwwapxvufFerKMVotH458o6eynBZxkVb7h07RAGdR44HS2i2Z2Ma5IkVf3uFdls9KlVmvjo2owaDAdBgNVHQ4EFgQUjCMRsfolTeK5Ds6MqOWj5Nx01BQwEgYDVR0TAQH/BAgwBgEB/wIBADAjBgNVHRIEHDAahhhodHRwczovL2lhY2EuZXhhbXBsZS5jb20wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0kAMEYCIQCnUfp3OyxcaPCT34SQ4dTNyNN0qgxKWpWIDeUXkrs7HwIhALFYrMrINeAats4ZWRxZMK6bykb9dcOwkmBCv96MoZVi\n-----END CERTIFICATE-----\n",
                          "certificateData": {
                            "country": "US",
                            "commonName": "Example IACA",
                            "notBefore": "2025-05-28T12:23:01Z",
                            "notAfter": "2040-05-24T12:23:01Z",
                            "issuerAlternativeNameConf": {
                              "uri": "https://iaca.example.com"
                            }
                          }
                        }
                    """.trimIndent())
                }
            }
        }

        "400" to {
            description = "Bad request"
        }
    }
}