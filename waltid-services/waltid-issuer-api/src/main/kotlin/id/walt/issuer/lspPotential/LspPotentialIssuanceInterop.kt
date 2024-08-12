package id.walt.issuer.lspPotential

import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.issuance.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.oid4vc.data.AuthenticationMethod
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

object LspPotentialIssuanceInterop {
  val POTENTIAL_ISSUER_JWK_KEY: JWKKey = runBlocking {
    JWKKey.importJWK(ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PRIV + LspPotentialInterop.POTENTIAL_ISSUER_PUB).toJSONString()).getOrThrow()
  }

  fun createInteropSampleCredentialOfferUri(issuanceRequestExample: String): String = runBlocking {
    createCredentialOfferUri(listOf(
      Json.decodeFromString<IssuanceRequest>(issuanceRequestExample).copy(authenticationMethod = AuthenticationMethod.NONE)
    ))
  }
}

fun Application.lspPotentialIssuanceTestApi() {
  routing {
    route("lsp-potential", {
      tags = listOf("LSP Potential Interop test endpoints")
    }) {
      get("lspPotentialCredentialOfferT1") {
        val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUri(
          IssuanceExamples.mDLCredentialIssuanceData
        )
        context.respond(
          HttpStatusCode.OK, offerUri
        )
      }
      get("lspPotentialCredentialOfferT2") {
        val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUri(
          IssuanceExamples.sdJwtVCData
        )
        context.respond(
          HttpStatusCode.OK, offerUri
        )
      }
    }
  }
}
