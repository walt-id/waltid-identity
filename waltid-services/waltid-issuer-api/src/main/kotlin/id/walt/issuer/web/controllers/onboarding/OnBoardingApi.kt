package id.walt.issuer.web.controllers.onboarding

import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.issuer.services.onboarding.OnboardingService
import id.walt.issuer.services.onboarding.models.DocumentSignerOnboardingRequest
import id.walt.issuer.services.onboarding.models.IACAOnboardingRequest
import id.walt.issuer.web.controllers.onboarding.openapi.DocumentSignerDocs
import id.walt.issuer.web.controllers.onboarding.openapi.IACADocs
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.onboardingApi() {
    routing {
        route("onboard", {
            tags = listOf("Onboarding Service")
        }) {
            route("iso-mdl") {
                post(
                    path = "iacas",
                    builder = {
                        summary = "Issue an Issuing Authority Certification Authority (IACA) certificate for mDL."

                        description =
                            "Creates a self-signed X.509 root certificate representing an Issuing Authority Certification Authority (IACA),\n" +
                                    "  compliant with the ISO/IEC 18013-5 specification for mobile driver's licenses (mDL). The generated certificate\n" +
                                    "  serves as a trust anchor in the mDL public key infrastructure.\n" +
                                    "\n" +
                                    "  The certificate includes:\n" +
                                    "  - Basic Constraints: `CA=true`, `pathLenConstraint=0`\n" +
                                    "  - Key Usage: `keyCertSign`, `cRLSign` (marked critical)\n" +
                                    "  - Subject Key Identifier\n" +
                                    "  - Issuer Alternative Name(s) (URI and/or email)\n" +
                                    "  - Optional CRL Distribution Point (URI)\n" +
                                    "\n" +
                                    "  ### Validations:\n" +
                                    "  - `country` must be a valid ISO 3166-1 alpha-2 country code (e.g., `\"US\"`)\n" +
                                    "  - Optional fields (`stateOrProvinceName`, `organizationName`) must not be blank if specified\n" +
                                    "  - `notAfter`, if present, must be greater than `notBefore` & the current time\n" +
                                    "  - The certificate's validity period cannot exceed 20 years\n" +
                                    "\n" +
                                    "  ### Defaults:\n" +
                                    "  - A local `secp256r1` key will be generated if **no** other backend is specified\n" +
                                    "  - If `notBefore` is not provided, the current system time is used\n" +
                                    "  - If `notAfter` is not provided, it defaults to 20 years after `notBefore`" +
                                    "\n" +
                                    "  ### Notes:\n" +
                                    "  - Supported keys: `secp256r1`, `secp384r1` and `secp521r1`"

                        request(IACADocs.requestConfig())

                        response(IACADocs.responsesConfig())
                    }
                ) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = OnboardingService.onboardIACA(call.receive<IACAOnboardingRequest>()),
                    )
                }
                post(
                    path = "document-signers",
                    builder = {
                        summary = "Issue a Document Signer (DS) certificate for mDL."

                        description = ">\n" +
                                "  Issues an X.509 Document Signer (DS) certificate compliant with ISO/IEC 18013-5 for signing\n" +
                                "  mobile driver's license (mDL) documents. The certificate is issued by an\n" +
                                "  IACA and includes all required fields and extensions for mDL conformance.\n" +
                                "\n" +
                                "  The certificate includes:\n" +
                                "  - Key Usage: `digitalSignature` (marked critical)\n" +
                                "  - Extended Key Usage: `1.0.18013.5.1.2` (Document Signer)\n" +
                                "  - Subject Key and Authority Key identifiers\n" +
                                "  - Issuer Alternative Name (URI and/or email, from IACA)\n" +
                                "  - CRL Distribution Point (URI)\n" +
                                "\n" +
                                "  ### Validations:\n" +
                                "  - `country` and `stateOrProvinceName` (if specified) must match those of the IACA\n" +
                                "  - `country` must be a valid ISO 3166-1 alpha-2 code\n" +
                                "  - Optional strings (`organizationName`, `stateOrProvinceName`) must not be blank if provided\n" +
                                "  - `notAfter`, if present, must be greater than `notBefore` & the current time\n" +
                                "  - The certificate's validity period cannot exceed 457 days\n" +
                                "\n" +
                                "  ### Defaults:\n" +
                                "  - A local `secp256r1` key will be generated if **no** other backend is specified\n" +
                                "  - If `notBefore` is not provided, the current system time is used\n" +
                                "  - If `notAfter` is not provided, it defaults to 457 days after `notBefore`\n" +
                                "\n" +
                                "  ### Notes:\n" +
                                "  - Supported keys: `secp256r1`, `secp384r1` and `secp521r1`"

                        request(DocumentSignerDocs.requestConfig())

                        response(DocumentSignerDocs.responsesConfig())
                    }
                ) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = OnboardingService.onboardDocumentSigner(
                            request = call.receive<DocumentSignerOnboardingRequest>(),
                        ),
                    )

                }
            }
            post("issuer", {
                summary = "Onboards a new issuer."
                description = "Creates an issuer keypair and an associated DID based on the provided configuration."

                request {
                    body<OnboardingRequest> {
                        required = true
                        description = "Issuer onboarding request (key & DID) config."
                        example(
                            "did:jwk + JWK key (Ed25519)",
                            IssuanceExamples.issuerOnboardingRequestDefaultEd25519Example
                        )
                        example(
                            "did:jwk + JWK key (secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestDefaultSecp256r1Example
                        )
                        example(
                            "did:jwk + JWK key (secp256k1)",
                            IssuanceExamples.issuerOnboardingRequestDefaultSecp256k1Example
                        )
                        example("did:jwk + JWK key (RSA)", IssuanceExamples.issuerOnboardingRequestDefaultRsaExample)
                        example("did:web + JWK key (Secp256k1)", IssuanceExamples.issuerOnboardingRequestDidWebExample)
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + AppRole (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleAppRole
                        )
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + UserPass (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleUserPass
                        )
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + AccessKey (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleAccessKey
                        )
                        example(
                            "did:jwk + OCI key (Oracle Cloud Infrastructure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestOciExample
                        )
                        example(
                            "did:jwk + OCI REST API key  (Oracle Cloud Infrastructure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestOciRestApiExample
                        )
                        example(
                            "did:jwk + AWS REST API key  (AWS - Secp256r1) + AccessKey (Auth)",
                            IssuanceExamples.issuerOnboardingRequestAwsRestApiExampleWithDirectAccess
                        )
                        example(
                            "did:jwk + AWS REST API key  (AWS - Secp256r1) + Role (Auth)",
                            IssuanceExamples.issuerOnboardingRequestAwsRestApiExampleWithRole
                        )
                        example(
                            "did:jwk + Azure REST API key  (Azure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestAzureRestApiExample
                        )
                        example(
                            "did:jwk + AWS SDK key  (AWS - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestAwsSdkExample
                        )
                        example(
                            "did:jwk + Azure SDK key  (Azure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestAzureSdkExample
                        )
                        required = true
                    }
                }

                response {
                    "200" to {
                        description = "Issuer onboarding response"
                        body<IssuerOnboardingResponse> {
                            example(
                                "Local JWK key (Secp256r1) + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseDefaultExample,
                            )
                            example(
                                "Local JWK key (Secp256r1) + did:web",
                                IssuanceExamples.issuerOnboardingResponseDidWebExample,
                            )
                            example(
                                "Remote TSE Ed25519 key + did:key",
                                IssuanceExamples.issuerOnboardingResponseTseExample,
                            )
                            example(
                                "Remote OCI Secp256r1 key + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseOciExample,
                            )
                            example(
                                "Remote OCI REST API Secp256r1 key + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseOciRestApiExample,
                            )
                        }
                    }
                    "400" to {
                        description = "Bad request"

                    }
                }
            }) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = OnboardingService.didIssuerOnboard(
                        request = call.receive<OnboardingRequest>(),
                    ),
                )
            }
        }
    }
}