package id.walt.issuance2

import id.walt.commons.logging.LoggingManager
import id.walt.commons.logging.setups.TraceLoggingSetup
import id.walt.oid4vc.data.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val url = "http://localhost:9001"

fun main() {
    LoggingManager.useLoggingSetup(TraceLoggingSetup)
    LoggingManager.setup()

    embeddedServer(CIO, port = 9001) {
        install(ContentNegotiation) {
            json()
        }
        install(CallLogging)

        routing {
            get("/.well-known/openid-credential-issuer") {


                val providerMetadata = OpenIDProviderMetadata.Draft13(
                    issuer = url,
                    authorizationEndpoint = "$url/authorize",
                    pushedAuthorizationRequestEndpoint = "$url/par",
                    tokenEndpoint = "$url/token",
                    credentialEndpoint = "$url/credential",
                    batchCredentialEndpoint = "$url/batch_credential",
                    deferredCredentialEndpoint = "$url/credential_deferred",
                    jwksUri = "$url/jwks",
                    grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
                    requestUriParameterSupported = true,
                    subjectTypesSupported = setOf(SubjectType.public),
                    credentialIssuer = url, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
                    responseTypesSupported = setOf(
                        "code",
                        "vp_token",
                        "id_token"
                    ),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
                    idTokenSigningAlgValuesSupported = setOf("ES256"), // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
                    credentialConfigurationsSupported = IssuanceOfferManager.getAllActiveSessionTypes().associate { activeSessionType ->
                        activeSessionType.last() to CredentialSupported(
                            // id = activeSessionType.last(),
                            format = CredentialFormat.jwt_vc_json,
                            cryptographicBindingMethodsSupported = setOf("did"),
                            credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256K")),
                            credentialDefinition = CredentialDefinition(type = activeSessionType)
                        )
                    }
                )

                call.respond(providerMetadata)
            }
        }
    }.start(wait = true)
}
