package id.walt.issuer.issuance.openapi.issuerapi

import io.github.smiley4.ktoropenapi.config.RouteConfig
import kotlinx.serialization.json.JsonObject

object RawJwtDocs {
    fun getRawJwtDocs(): RouteConfig.() -> Unit = {
        summary = "Signs credential without using an credential exchange mechanism."
        description =
            "This endpoint issues (signs) an Verifiable Credential, but does not utilize an credential exchange " + "mechanism flow like OIDC or DIDComm to adapt and send the signed credential to an user. This means, that the " + "caller will have to utilize such an credential exchange mechanism themselves."

        request {
            body<JsonObject> {
                required = true
                description =
                    "Pass the unsigned credential that you intend to sign as the body of the request."
                example(
                    "UniversityDegreeCredential example",
                    IssuanceExamples.universityDegreeSignRequestCredentialExample
                )
                required = true
            }
        }

        response {
            "200" to {
                description = "Signed Credential (with the *proof* attribute added)"
                body<String> {
                    example(
                        "Signed UniversityDegreeCredential example",
                        IssuanceExamples.universityDegreeSignResponseCredentialExample
                    )
                }
            }
            "400" to {
                description = "The request could not be understood or was missing required parameters."
                body<String> {
                    example(IssuanceRequestErrors.MISSING_ISSUER_KEY) {
                        value = IssuanceRequestErrors.MISSING_ISSUER_KEY
                    }
                    example(IssuanceRequestErrors.INVALID_ISSUER_KEY_FORMAT) {
                        value = IssuanceRequestErrors.INVALID_ISSUER_KEY_FORMAT
                    }
                    example(IssuanceRequestErrors.MISSING_SUBJECT_DID) {
                        value = IssuanceRequestErrors.MISSING_SUBJECT_DID
                    }
                    example(IssuanceRequestErrors.MISSING_CREDENTIAL_DATA) {
                        value = IssuanceRequestErrors.MISSING_CREDENTIAL_DATA
                    }
                    example(IssuanceRequestErrors.INVALID_CREDENTIAL_DATA_FORMAT) {
                        value = IssuanceRequestErrors.INVALID_ISSUER_KEY_FORMAT
                    }
                }
            }
        }
    }
}
