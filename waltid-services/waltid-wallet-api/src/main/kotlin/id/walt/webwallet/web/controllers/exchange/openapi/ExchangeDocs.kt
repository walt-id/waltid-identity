package id.walt.webwallet.web.controllers.exchange.openapi

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.sdjwt.SDJWTVCTypeMetadata
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object ExchangeDocs {
    fun getUseOfferRequestDocs(): RouteConfig.() -> Unit = {
        summary = "Claim credential(s) from an issuer"
        request {
            queryParameter<String>("did") { description = "The DID to issue the credential(s) to" }
            queryParameter<Boolean>("requireUserInput") { description = "Whether to claim as pending acceptance" }
            queryParameter<String>("pinOrTxCode") {
                description =
                    "The `pin` (Draft11), or `tx_code` (Draft13 and onwards), value that may be required as part of a pre-authorized code (credential issuance) flow"
            }
            body<String> {
                description = "The offer request to use"
            }
        }

        response(ExchangeOpenApiCommons.useOfferRequestEndpointResponseParams())
    }

    fun getMatchCredentialsForPresentationDefinitionDocs(): RouteConfig.() -> Unit = {
        summary = "Returns the credentials stored in the wallet that match the passed presentation definition"

        request {
            body<PresentationDefinition> { description = "Presentation definition to match credentials against" }
        }
        response {
            HttpStatusCode.OK to {
                body<List<JsonObject>> {
                    description = "Credentials that match the presentation definition"
                }
            }
        }
    }

    fun getUnmatchedCredentialsForPresentationDefinition(): RouteConfig.() -> Unit = {
        summary =
            "Returns the credentials that are required by the presentation definition but not found in the wallet"

        request {
            body<PresentationDefinition> { description = "Presentation definition" }
        }
        response {
            HttpStatusCode.OK to {
                body<List<FilterData>> {
                    description = "Filters that failed to fulfill the presentation definition"
                }
            }
        }
    }

    fun getUsePresentationRequestDocs(): RouteConfig.() -> Unit = {
        summary = "Present credential(s) to a Relying Party"

        request {
            body<UsePresentationRequest>()
        }
        response(ExchangeOpenApiCommons.usePresentationRequestResponse())
    }

    fun getResolvePresentationRequestDocs(): RouteConfig.() -> Unit =
        {
            summary = "Return resolved / parsed presentation request"

            request {
                body<String> { description = "PresentationRequest to resolve/parse" }
            }
            response {
                HttpStatusCode.OK to {
                    body<String>()
                }
            }
        }

    fun getResolveCredentialOfferDocs(): RouteConfig.() -> Unit = {
        summary = "Return resolved / parsed credential offer"

        request {
            body<String> { description = "Credential offer request to resolve/parse" }
        }
        response {
            HttpStatusCode.OK to {
                body<CredentialOffer> {
                    description = "Resolved credential offer"
                }
            }
        }
    }

    fun getResolveVctUrlDocs(): RouteConfig.() -> Unit = {
        summary =
            "Receive an verifiable credential type (VCT) URL and return resolved vct object as described in IETF SD-JWT VC"
        request {
            queryParameter<String>("vct") {
                description = "The value of the vct in URL format"
                example("Example") { value = "https://example.com/mycustomvct" }
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Resolved VCT"
                body<SDJWTVCTypeMetadata>()
            }
        }
    }

    fun getResolveIssuerOpenIDMetadataDocs(): RouteConfig.() -> Unit = {
        summary = "Resolved Issuer OpenID Metadata"
        request {
            queryParameter<String>("issuer")
        }
        response {
            HttpStatusCode.OK to {
                description = "Resolved Issuer OpenID Metadata"
                body<OpenIDProviderMetadata>()
            }
        }
    }

}