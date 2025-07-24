package id.walt.issuer.issuance.openapi.issuerapi

import id.walt.issuer.issuance.IssuanceRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig

object SdJwtDocs {
    fun getSdJwtDocs(): RouteConfig.() -> Unit = {
        summary = "Signs credential using SD-JWT and starts an OIDC credential exchange flow."
        description =
            "This endpoint issues a W3C or SD-JWT-VC Verifiable Credential, and returns an issuance URL "

        request {
            statusCallbackUriHeader()
            sessionTtlHeader()
            body<IssuanceRequest> {
                required = true
                description =
                    "Pass the unsigned credential that you intend to issue in the body of the request."
                example("W3C SD-JWT example", IssuanceExamples.sdJwtW3CExample)
                example("W3C SD-JWT PDA1 example", IssuanceExamples.sdJwtW3CPDA1Example)
                example("SD-JWT-VC example", IssuanceExamples.sdJwtVCExample)
                example(
                    "SD-JWT-VC example featuring selectively disclosable sub and iat claims",
                    IssuanceExamples.sdJwtVCExampleWithSDSub
                )
                example(
                    "SD-JWT-VC example with issuer DID",
                    IssuanceExamples.sdJwtVCWithIssuerDidExample
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
        }
    }

    fun getSdJwtBatchDocs(): RouteConfig.() -> Unit =  {
        summary = "Signs a list of credentials with SD and starts an OIDC credential exchange flow."
        description =
            "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

        request {
            statusCallbackUriHeader()
            sessionTtlHeader()
            body<List<IssuanceRequest>> {
                required = true
                description =
                    "Pass the unsigned credential that you intend to issue as the body of the request."
                example("Batch example", IssuanceExamples.batchExampleSdJwt)
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