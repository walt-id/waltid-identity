package id.walt.issuer.issuance.openapi.issuerapi

import id.walt.issuer.issuance.IssuanceRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig

object JwtDocs {
    fun getJwtDocs(): RouteConfig.() -> Unit = {
        summary = "Signs credential with JWT and starts an OIDC credential exchange flow."
        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

        request {
            statusCallbackUriHeader()
            sessionTtlHeader()
            body<IssuanceRequest> {
                required = true
                description =
                    "Pass the unsigned credential that you intend to issue as the body of the request."
                example(
                    "OpenBadgeCredential example",
                    IssuanceExamples.openBadgeCredentialIssuanceExample
                )
                example(
                    "UniversityDegreeCredential example",
                    IssuanceExamples.universityDegreeIssuanceCredentialExample
                )
                example(
                    "OpenBadgeCredential example with Authorization Code Flow and Id Token",
                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithIdToken
                )
                example(
                    "OpenBadgeCredential example with Authorization Code Flow and Vp Token",
                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithVpToken
                )
                example(
                    "OpenBadgeCredential example with Authorization Code Flow and Username/Password Token",
                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithUsernamePassword
                )
                example(
                    "EBSI-VECTOR interoperability test - InTimeIssuance Draft11",
                    IssuanceExamples.ebsiCTExampleAuthInTimeDraft11
                )
                example(
                    "EBSI-VECTOR interoperability test - DeferredIssuance Draft11",
                    IssuanceExamples.ebsiCTExampleAuthDeferredDraft11
                )
                example(
                    "EBSI-VECTOR interoperability test - PreAuthIssuance Draft11",
                    IssuanceExamples.ebsiCTExamplePreAuthDraft11
                )
                example(
                    "EBSI-VECTOR interoperability test - InTimeIssuance Draft13",
                    IssuanceExamples.ebsiCTExampleAuthInTimeDraft13
                )
                example(
                    "EBSI-VECTOR interoperability test - DeferredIssuance Draft13",
                    IssuanceExamples.ebsiCTExampleAuthDeferredDraft13
                )
                example(
                    "EBSI-VECTOR interoperability test - PreAuthIssuance Draft13",
                    IssuanceExamples.ebsiCTExamplePreAuthDraft13
                )
                required = true
            }
        }

        response {
            "200" to {
                description = "Credential signed (with the *proof* attribute added)"
                body<String> {
                    example("Issuance URL") {
                        value =
                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                    }
                }
            }
            "400" to {
                description = "Bad request - The request could not be understood or was missing required parameters."
                body<String> {
                    example(IssuanceRequestErrors.MISSING_ISSUER_KEY) {
                        value = IssuanceRequestErrors.MISSING_ISSUER_KEY
                    }
                    example(IssuanceRequestErrors.INVALID_ISSUER_KEY_FORMAT) {
                        value = IssuanceRequestErrors.INVALID_ISSUER_KEY_FORMAT
                    }
                    example(IssuanceRequestErrors.MISSING_ISSUER_DID) {
                        value = IssuanceRequestErrors.MISSING_ISSUER_DID
                    }
                    example(IssuanceRequestErrors.MISSING_CREDENTIAL_CONFIGURATION_ID) {
                        value = IssuanceRequestErrors.MISSING_CREDENTIAL_CONFIGURATION_ID
                    }
                    example(IssuanceRequestErrors.MISSING_CREDENTIAL_DATA) {
                        value = IssuanceRequestErrors.MISSING_CREDENTIAL_DATA
                    }
                    example(IssuanceRequestErrors.INVALID_CREDENTIAL_DATA_FORMAT) {
                        value = IssuanceRequestErrors.INVALID_CREDENTIAL_DATA_FORMAT
                    }
                }
            }
        }
    }

    fun getJwtBatchDocs(): RouteConfig.() -> Unit = {
        summary = "Signs a list of credentials and starts an OIDC credential exchange flow."
        description = "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

        request {
            statusCallbackUriHeader()
            sessionTtlHeader()
            body<List<IssuanceRequest>> {
                required = true
                description =
                    "Pass the unsigned credential that you intend to issue as the body of the request."
                example("Batch example", IssuanceExamples.batchExampleJwt)
                required = true
            }
        }

        response {
            "200" to {
                description = "Credential signed (with the *proof* attribute added)"
                body<String> {
                    example("Issuance URL") {
                        value =
                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                    }
                }
            }
        }
    }

}
