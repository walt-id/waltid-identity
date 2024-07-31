package id.walt.issuer.lspPotential

import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.mdoc.doc.MDocTypes
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

object LspPotentialIssuanceInterop {
  val POTENTIAL_ISSUER_KEY_JWK: String = ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PRIV + LspPotentialInterop.POTENTIAL_ISSUER_PUB).toJSONString()

  fun createInteropSampleCredentialOfferUri(credentialConfigurationId: String, credentialData: W3CVC?, mdocData: Map<String, JsonObject>?): String = runBlocking {
    val jwkKey = JWKKey.importJWK(POTENTIAL_ISSUER_KEY_JWK).getOrThrow()
    IssuanceRequest(
      Json.parseToJsonElement(KeySerialization.serializeKey(jwkKey)).jsonObject,
      "",
      credentialConfigurationId,
      credentialData,
      mdocData = mdocData,
      x5Chain = listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT),
      trustedRootCAs = listOf(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)
    ).let { createCredentialOfferUri(listOf(it)) }
  }
}

fun Application.lspPotentialIssuanceTestApi() {
  routing {
    route("lsp-potential", {
      tags = listOf("LSP Potential Interop test endpoints")
    }) {
      get("lspPotentialCredentialOfferT1") {
        val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUri(
          MDocTypes.ISO_MDL,
          null,
          mdocData = mapOf("org.iso.18013.5.1" to buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
            put("birth_date", "1980-01-01")
          })
        )
        context.respond(
          HttpStatusCode.OK, offerUri
        )
      }
      get("lspPotentialCredentialOfferT2") {
        val offerUri = LspPotentialIssuanceInterop.createInteropSampleCredentialOfferUri(
          "urn:eu.europa.ec.eudi:pid:1",
          credentialData = W3CVC(buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
          }),
          null
        )
        context.respond(
          HttpStatusCode.OK, offerUri
        )
      }
    }
  }
}
