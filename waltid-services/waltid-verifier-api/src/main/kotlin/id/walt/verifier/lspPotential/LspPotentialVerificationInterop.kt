package id.walt.verifier.lspPotential

import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import cbor.Cbor
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.commons.interop.LspPotentialInterop
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMapBuilder
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private val logger = KotlinLogging.logger {}

object LspPotentialVerificationInterop {
    val POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO = loadPotentialIssuerKeys()
    val POTENTIAL_JWT_CRYPTO_PROVIDER = SimpleJWTCryptoProvider(
        JWSAlgorithm.ES256,
        ECDSASigner(
            ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PRIV + LspPotentialInterop.POTENTIAL_ISSUER_PUB)
                .toECKey()
        ), ECDSAVerifier(
            ECKey.parseFromPEMEncodedObjects(LspPotentialInterop.POTENTIAL_ISSUER_PUB).toECKey()
        )
    )

    private fun readKeySpec(pem: String): ByteArray {
        val publicKeyPEM = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace(System.lineSeparator().toRegex(), "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")

        return Base64.getDecoder().decode(publicKeyPEM)
    }

    private fun loadPotentialIssuerKeys(): COSECryptoProviderKeyInfo {
        val factory = CertificateFactory.getInstance("X.509")
        val rootCaCert =
            (factory.generateCertificate(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT.byteInputStream())) as X509Certificate
        val issuerCert =
            (factory.generateCertificate(LspPotentialInterop.POTENTIAL_ISSUER_CERT.byteInputStream())) as X509Certificate
        val issuerPub = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(readKeySpec(LspPotentialInterop.POTENTIAL_ISSUER_PUB)))
        val issuerPriv = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(readKeySpec(LspPotentialInterop.POTENTIAL_ISSUER_PRIV)))
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

@Serializable
data class LSPPotentialIssueFormDataParam(
    val jwk: JsonObject,
)

@OptIn(ExperimentalSerializationApi::class)
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
                        required = true
                        mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
                        example("jwk") {
                            value = LSPPotentialIssueFormDataParam(
                                Json.parseToJsonElement(
                                    ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toString()
                                ).jsonObject
                            )
                        }
                    }
                }
            }) {
                val deviceJwk = call.request.call.receiveParameters().toMap()["jwk"]
                val devicePubKey = JWK.parse(deviceJwk!!.first()).toECKey().toPublicKey()

                val mdoc = MDocBuilder(MDocTypes.ISO_MDL)
                    .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDataElement())
                    .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDataElement())
                    .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
                    .sign(
                        ValidityInfo(
                            Clock.System.now(),
                            Clock.System.now(),
                            Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)
                        ),
                        DeviceKeyInfo(DataElement.fromCBOR(OneKey(devicePubKey, null).AsCBOR().EncodeToBytes())),
                        SimpleCOSECryptoProvider(
                            listOf(
                                LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO
                            )
                        ), LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID
                    )
                logger.debug { "SIGNED MDOC (mDL):" }
                logger.debug { Cbor.encodeToHexString(mdoc) }
                call.respond(mdoc.toCBORHex())
            }
            post("issueSdJwtVC", {
                summary = "Issue SD-JWT-VC for given holder key, using internal issuer keys"
                description = "Give holder public key JWK in form body."
                hidden = false
                request {
                    body<LSPPotentialIssueFormDataParam> {
                        required = true
                        mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
                        example("jwk") {
                            value = LSPPotentialIssueFormDataParam(
                                Json.parseToJsonElement(
                                    ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toString().also {
                                        logger.debug { it }
                                    }).jsonObject
                            )
                        }
                    }
                }
            }) {
                val holderJwk = call.request.call.receiveParameters().toMap()["jwk"]!!.first()

                val sdJwtVc = SDJwtVC.sign(
                    SDPayload.Companion.createSDPayload(
                        buildJsonObject {
                            put("family_name", "Doe")
                            put("given_name", "John")
                            put("birthdate", "1940-01-01")
                        },
                        SDMapBuilder().addField("family_name", true).addField("given_name", true)
                            .addField("birthdate", true).build()
                    ),
                    LspPotentialVerificationInterop.POTENTIAL_JWT_CRYPTO_PROVIDER,
                    LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID,
                    Json.parseToJsonElement(holderJwk).jsonObject,
                    LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID,
                    vct = "urn:eu.europa.ec.eudi:pid:1",
                    additionalJwtHeader = mapOf("x5c" to listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT)),
                    nbf = Clock.System.now().epochSeconds - 1,
                    exp = Clock.System.now().epochSeconds + 60
                )

                logger.debug { "SIGNED SD-JWT-VC:" }
                logger.debug { sdJwtVc.toString(false) }
                call.respond(sdJwtVc.toString(false))
            }
        }
    }
}

