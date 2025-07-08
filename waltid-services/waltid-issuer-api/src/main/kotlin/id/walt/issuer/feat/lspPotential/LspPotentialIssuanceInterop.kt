package id.walt.issuer.feat.lspPotential

import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.CredentialFormat
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

object LspPotentialIssuanceInterop {
    val POTENTIAL_ISSUER_JWK_KEY: JWKKey = runBlocking {
        JWKKey.importJWK(
            ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PRIV + LspPotentialInterop.POTENTIAL_ISSUER_PUB)
                .toJSONString()
        ).getOrThrow()
    }
    val ISSUER_DID = runBlocking {
        DidJwkRegistrar().registerByKey(POTENTIAL_ISSUER_JWK_KEY, DidCreateOptions("jwk", mapOf())).did
    }

    fun createInteropSampleCredentialOfferUrimDL(issuanceRequestExample: String): String = runBlocking {
        createCredentialOfferUri(
            listOf(
                Json.decodeFromString<IssuanceRequest>(issuanceRequestExample).copy(authenticationMethod = AuthenticationMethod.NONE)
            ), CredentialFormat.mso_mdoc
        )
    }

    fun createInteropSampleCredentialOfferUriSdJwt(issuanceRequestExample: String): String = runBlocking {
        createCredentialOfferUri(
            listOf(
                Json.decodeFromString<IssuanceRequest>(issuanceRequestExample).copy(authenticationMethod = AuthenticationMethod.NONE)
            ), CredentialFormat.sd_jwt_vc
        )
    }
}

fun Application.lspPotentialIssuanceTestApi() {
    routing {
        route("lsp-potential", {
            tags = listOf("LSP Potential Interop test endpoints")
        }) {
            get("lspPotentialCredentialOfferT1") {
                val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUrimDL(
                    IssuanceExamples.mDLCredentialIssuanceData
                )
                call.respond(
                    HttpStatusCode.OK, offerUri
                )
            }
            get("lspPotentialCredentialOfferT2") {
                val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUriSdJwt(
                    IssuanceExamples.sdJwtVCData
                )
                call.respond(
                    HttpStatusCode.OK, offerUri
                )
            }
        }
    }
}
