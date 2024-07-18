package id.walt.verifier.lspPotential

import COSE.AlgorithmID
import COSE.OneKey
import cbor.Cbor
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMapBuilder
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

object LspPotentialVerificationInterop {
  const val POTENTIAL_ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIBQzCB66ADAgECAgjbHnT+6LsrbDAKBggqhkjOPQQDAjAYMRYwFAYDVQQDDA1NRE9DIFJPT1QgQ1NQMB4XDTI0MDUwMjEzMTMzMFoXDTI0MDUwMzEzMTMzMFowFzEVMBMGA1UEAwwMTURPQyBST09UIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeKMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgWM+JtnhdqbTzFD1S3byTvle0n/6EVALbkKCbdYGLn8cCICOoSETqwk1oPnJEEPjUbdR4txiNqkHQih8HKAQoe8t5\n" +
      "-----END CERTIFICATE-----\n"
  const val POTENTIAL_ROOT_CA_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
      "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCBXPx4eVTypvm0pQkFdqVXlORn+YIFNb+Hs5xvmG3EM8g==\n" +
      "-----END PRIVATE KEY-----\n"
  const val POTENTIAL_ROOT_CA_PUB = "-----BEGIN PUBLIC KEY-----\n" +
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeA==\n" +
      "-----END PUBLIC KEY-----\n"
  const val POTENTIAL_ISSUER_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIBRzCB7qADAgECAgg57ch6mnj5KjAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwHhcNMjQwNTAyMTMxMzMwWhcNMjUwNTAyMTMxMzMwWjAbMRkwFwYDVQQDDBBNRE9DIFRlc3QgSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gaMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhAI5wBBAA3ewqIwslhuzFn4rNFW9dkz2TY7xeImO7CraYAiAYhai1NzJ6abAiYg8HxcRdYpO4bu2Sej8E6CzFHK34Yw==\n" +
      "-----END CERTIFICATE-----"
  const val POTENTIAL_ISSUER_PUB = "-----BEGIN PUBLIC KEY-----\n" +
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n" +
      "-----END PUBLIC KEY-----\n"
  const val POTENTIAL_ISSUER_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
      "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAoniTdVyXlKP0x+rius1cGbYyg+hjf8CT88hH8SCwWFA==\n" +
      "-----END PRIVATE KEY-----\n"
  const val POTENTIAL_ISSUER_KEY_ID = "potential-lsp-issuer-key-01"
  val POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO = loadPotentialIssuerKeys()
  val POTENTIAL_JWT_CRYPTO_PROVIDER = SimpleJWTCryptoProvider(
    JWSAlgorithm.ES256,
    ECDSASigner(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PRIV + POTENTIAL_ISSUER_PUB).toECKey()), ECDSAVerifier(
      ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PUB).toECKey())
  )

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
    val rootCaCert = (factory.generateCertificate(POTENTIAL_ROOT_CA_CERT.byteInputStream())) as X509Certificate
    val issuerCert = (factory.generateCertificate(POTENTIAL_ISSUER_CERT.byteInputStream())) as X509Certificate
    val issuerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PUB)))
    val issuerPriv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PRIV)))
    return COSECryptoProviderKeyInfo(
      POTENTIAL_ISSUER_KEY_ID,
      AlgorithmID.ECDSA_256,
      issuerPub,
      issuerPriv,
      listOf(issuerCert),
      listOf(rootCaCert)
    )
  }
}

data class LSPPotentialIssueFormDataParam(
  val jwk: JsonObject,
)

fun Application.lspPotentialVerificationTestApi() {
  routing {
    route("lsp-potential", {
      tags = listOf("LSP Potential Interop test endpoints")
    }) {
      post("issueMdl", {
        summary = "Issue MDL for given device key, using internal issuer keys"
        description = "Give device public key JWK in form body."
        hidden = false
        request {
          body<LSPPotentialIssueFormDataParam> {
            mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
            example("jwk") {
              value = LSPPotentialIssueFormDataParam(
                Json.parseToJsonElement(ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toString()).jsonObject
              )
            }
          }
        }
      }) {
        val deviceJwk = context.request.call.receiveParameters().toMap()["jwk"]
        val devicePubKey = JWK.parse(deviceJwk!!.first()).toECKey().toPublicKey()

        val mdoc = MDocBuilder("org.iso.18013.5.1.mDL")
          .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDataElement())
          .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDataElement())
          .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
          .sign(
            ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)),
            DeviceKeyInfo(DataElement.fromCBOR(OneKey(devicePubKey, null).AsCBOR().EncodeToBytes())),
            SimpleCOSECryptoProvider(
              listOf(
                LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO
              )
            ), LspPotentialVerificationInterop.POTENTIAL_ISSUER_KEY_ID
          )
        println("SIGNED MDOC (mDL):")
        println(Cbor.encodeToHexString(mdoc))
        call.respond(mdoc.toCBORHex())
      }
      post("issueSdJwtVC", {
        summary = "Issue SD-JWT-VC for given holder key, using internal issuer keys"
        description = "Give holder public key JWK in form body."
        hidden = false
        request {
          body<LSPPotentialIssueFormDataParam> {
            mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
            example("jwk") {
              value = LSPPotentialIssueFormDataParam(
                Json.parseToJsonElement(ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toString().also {
                  println(it)
                }).jsonObject
              )
            }
          }
        }
      }) {
        val holderJwk = context.request.call.receiveParameters().toMap().get("jwk")!!.first()

        val sdJwtVc = SDJwtVC.sign(
          SDPayload.Companion.createSDPayload(buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
            put("birthdate", "1940-01-01")
          }, SDMapBuilder().addField("family_name", true).addField("given_name", true).addField("birthdate", true).build()),
          LspPotentialVerificationInterop.POTENTIAL_JWT_CRYPTO_PROVIDER, LspPotentialVerificationInterop.POTENTIAL_ISSUER_KEY_ID,
          Json.parseToJsonElement(holderJwk).jsonObject, LspPotentialVerificationInterop.POTENTIAL_ISSUER_KEY_ID,
          vct = "urn:eu.europa.ec.eudi:pid:1",
          additionalJwtHeader = mapOf("x5c" to listOf(LspPotentialVerificationInterop.POTENTIAL_ISSUER_CERT))
        )

        println("SIGNED SD-JWT-VC:")
        println(sdJwtVc.toString(false))
        call.respond(sdJwtVc.toString(false))
      }
    }
  }
}

