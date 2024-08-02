package id.walt.issuer.lspPotential

import COSE.AlgorithmID
import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.doc.MDocTypes
import id.walt.oid4vc.data.AuthenticationMethod
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object LspPotentialIssuanceInterop {
    val POTENTIAL_ISSUER_KEY_JWK =
        ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PRIV + LspPotentialInterop.POTENTIAL_ISSUER_PUB)
            .toJSONString()

    fun readKeySpec(pem: String): ByteArray {
        val publicKeyPEM = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace(System.lineSeparator().toRegex(), "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")

        return Base64.getDecoder().decode(publicKeyPEM)
    }

    fun loadPotentialIssuerKeys(): COSECryptoProviderKeyInfo {
        val factory = CertificateFactory.getInstance("X.509")
        val rootCaCert = (factory.generateCertificate(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT.byteInputStream())) as X509Certificate
        val issuerCert = (factory.generateCertificate(LspPotentialInterop.POTENTIAL_ISSUER_CERT.byteInputStream())) as X509Certificate
        val issuerPub =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(readKeySpec(LspPotentialInterop.POTENTIAL_ISSUER_PUB)))
        val issuerPriv =
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(readKeySpec(LspPotentialInterop.POTENTIAL_ISSUER_PRIV)))
        return COSECryptoProviderKeyInfo(
            LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID,
            AlgorithmID.ECDSA_256,
            issuerPub,
            issuerPriv,
            listOf(issuerCert),
            listOf(rootCaCert)
        )
    }
}

fun Application.lspPotentialIssuanceTestApi() {
    routing {
        route("lsp-potential", {
            tags = listOf("LSP Potential Interop test endpoints")
        }) {
            get("lspPotentialCredentialOfferT1") {
                val jwkKey = JWKKey.importJWK(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_KEY_JWK).getOrThrow()
                val offerUri = IssuanceRequest(
                    authenticationMethod = AuthenticationMethod.NONE,
                    issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(jwkKey)).jsonObject,
                    issuerDid = "",
                    credentialConfigurationId = MDocTypes.ISO_MDL,
                    credentialData = null,
                    mdocData = mapOf("org.iso.18013.5.1" to buildJsonObject {
                        put("family_name", "Doe")
                        put("given_name", "John")
                        put("birth_date", "1980-01-01")
                    }),
                    x5Chain = listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT),
                    trustedRootCAs = listOf(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)
                ).let { createCredentialOfferUri(listOf(it)) }
                context.respond(
                    HttpStatusCode.OK, offerUri
                )
            }
            get("lspPotentialCredentialOfferT2") {
                val jwkKey = JWKKey.importJWK(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_KEY_JWK).getOrThrow()
                val offerUri = IssuanceRequest(
                    authenticationMethod = AuthenticationMethod.NONE,
                    issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(jwkKey)).jsonObject,
                    issuerDid = "",
                    credentialConfigurationId = "urn:eu.europa.ec.eudi:pid:1",
                    credentialData = W3CVC(buildJsonObject {
                        put("family_name", "Doe")
                        put("given_name", "John")
                    }),
                    mdocData = null
                ).let { createCredentialOfferUri(listOf(it)) }
                context.respond(
                    HttpStatusCode.OK, offerUri
                )
            }
        }
    }
}
