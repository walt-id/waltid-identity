@file:OptIn(ExperimentalTime::class)

package id.walt.oid4vc

import cbor.Cbor
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.*
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.w3c.utils.VCFormat
import io.kotest.core.spec.style.AnnotationSpec
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class MDocTest: AnnotationSpec() {

  val ISSUER_KEY_ID = "ISSUER_KEY"
  val DEVICE_KEY_ID = "DEVICE_KEY"
  val mdoc_auth_response = ("{\n" +
      "  \"presentation_submission\": {\n" +
      "    \"definition_id\": \"mDL-sample-req\",\n" +
      "    \"id\": \"mDL-sample-res\",\n" +
      "    \"descriptor_map\": [\n" +
      "      {\n" +
      "        \"id\": \"org.iso.18013.5.1.mDL\",\n" +
      "        \"format\": \"mso_mdoc\",\n" +
      "        \"path\": \"\$\"\n" +
      "      }\n" +
      "    ]\n" +
      "  },\n" +
      "  \"vp_token\": \"o2ZzdGF0dXMAZ3ZlcnNpb25jMS4waWRvY3VtZW50c4GjZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbGRldmljZVNpZ25lZKJqZGV2aWNlQXV0aKFvZGV2aWNlU2lnbmF0dXJlhEOhASag9lhAZIIUI8retZS5btJ9TGyaMt7j1nQm1DUy5FyG_98yKOOWNOtizwY41CipQOMGZ5d7Plh722-YQrSCpZTNBIYjxmpuYW1lU3BhY2Vz2BhBoGxpc3N1ZXJTaWduZWSiamlzc3VlckF1dGiEQ6EBJqEYIVkCYDCCAlwwggIBoAMCAQICCkdSCck8KAChX_8wCgYIKoZIzj0EAwIwRTELMAkGA1UEBhMCVVMxKTAnBgNVBAMMIElTTzE4MDEzLTUgVGVzdCBDZXJ0aWZpY2F0ZSBJQUNBMQswCQYDVQQIDAJOWTAeFw0yNDA0MjgyMTAyMjNaFw0yNTA3MjkyMTAyMjNaMEQxCzAJBgNVBAYTAlVTMSgwJgYDVQQDDB9JU08xODAxMy01IFRlc3QgQ2VydGlmaWNhdGUgRFNDMQswCQYDVQQIDAJOWTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABDdOFaKr9WxgpFWlzF8VmfchBvTwC1oH1MaP685sHKGmreQPVsqbSlHABGTWPrcnbhlPbQLrDsZH03ggndfjw7yjgdkwgdYwHQYDVR0OBBYEFGUpDcssvlnvVrvfRW1P-KRafe5aMB8GA1UdIwQYMBaAFEz_lSXgZZtQ7BxDClpyjcQbTTrPMA4GA1UdDwEB_wQEAwIHgDAdBgNVHREEFjAUgRJleGFtcGxlQGlzb21kbC5jb20wHQYDVR0SBBYwFIESZXhhbXBsZUBpc29tZGwuY29tMC8GA1UdHwQoMCYwJKAioCCGHmh0dHBzOi8vZXhhbXBsZS5jb20vSVNPbURMLmNybDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMAoGCCqGSM49BAMCA0kAMEYCIQCvw8wYtoDlQlBzqMYF6U0KXK1fFC5f0NETmKktxq-jWQIhAKOIt0zsjXCO2TJvtCa81HQDOoDOCvc4Tp5jzp4rW7VDWQK62BhZArWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Fxb3JnLmlzby4xODAxMy41LjGrAFggJU2b_85ISFXlEQWLKnOZVmRs1xSzYsZwWe0Z1Nju4yUBWCC6jOuodOY0wsyiy1cVQZ1trp9MdS40ma6NoiqSCw3i_AJYINNVwMahFR_eg3WdYKd_mlT7jcpBlUo4efrVfaljh1qUA1gg18RTMj2oZ361MmmRKRskRJxLZr8U8y8BjYePiE0MDrIEWCBAXKSrlBnPKnWZ5ovf0-tH6yS-_fLq0jtlV6lo_m2xkAVYIChjHaujPFotPAVarU6OS9bOUGJM2i8Su0QHcGd8LUIqBlggEPSlRSQU3qO8WGlhdybrFvOED7ClhKoXNnaz7iEYYG0HWCBdHiKvThj-f0ujtxCpB-rDOr2j5K6Dus7A4wlVA1FesghYIOcFkpH5fl3zQDlmzrt0uOqp37_3RYcsl11ju8WBF0Q0CVggRxt5r6QHia1VtAc2pWWASpR-FtxUWwSriOJRAA3xUNwKWCBJKSm9xIOQawO8CVvCxg_B-1LOrUU_syVoouJRsC2cXm1kZXZpY2VLZXlJbmZvoWlkZXZpY2VLZXmkAQIgASFYIFfRF0B86kxJpllzlXbiSPjaamzG1FL6ZOL9VKkdPecLIlgglApkmUibrqPDNOcJi0q0zSbX440venAe0K1Xrn3X70BnZG9jVHlwZXVvcmcuaXNvLjE4MDEzLjUuMS5tRExsdmFsaWRpdHlJbmZvo2l2YWxpZEZyb23AdDIwMjQtMDQtMjhUMjE6MDI6MjVaanZhbGlkVW50aWzAdDIwMjQtMDUtMDhUMjE6MDI6MjRaZnNpZ25lZMB0MjAyNC0wNC0yOFQyMTowMjoyNFpYQNMckHB3uEeFbz7re-heKVBrD6L9MiAQBk5IRhF1U9cfIq5lanDt5cnWBOEEV77VxJXDF-pbja-murf1S_9ymnxqbmFtZVNwYWNlc6Fxb3JnLmlzby4xODAxMy41LjGL2BhZCDukaGRpZ2VzdElEBWZyYW5kb21QZWUgWBRENQw29qWDPQ9duHFlbGVtZW50SWRlbnRpZmllcmhwb3J0cmFpdGxlbGVtZW50VmFsdWVZB-3_2P_gABBKRklGAAEBAAAAAAAAAP_iAihJQ0NfUFJPRklMRQABAQAAAhgAAAAABDAAAG1udHJSR0IgWFlaIAAAAAAAAAAAAAAAAGFjc3AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAD21gABAAAAANMtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACWRlc2MAAADwAAAAdHJYWVoAAAFkAAAAFGdYWVoAAAF4AAAAFGJYWVoAAAGMAAAAFHJUUkMAAAGgAAAAKGdUUkMAAAGgAAAAKGJUUkMAAAGgAAAAKHd0cHQAAAHIAAAAFGNwcnQAAAHcAAAAPG1sdWMAAAAAAAAAAQAAAAxlblVTAAAAWAAAABwAcwBSAEcAQgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWFlaIAAAAAAAAG-iAAA49QAAA5BYWVogAAAAAAAAYpkAALeFAAAY2lhZWiAAAAAAAAAkoAAAD4QAALbPcGFyYQAAAAAABAAAAAJmZgAA8qcAAA1ZAAAT0AAAClsAAAAAAAAAAFhZWiAAAAAAAAD21gABAAAAANMtbWx1YwAAAAAAAAABAAAADGVuVVMAAAAgAAAAHABHAG8AbwBnAGwAZQAgAEkAbgBjAC4AIAAyADAAMQA2_9sAQwAQCwwODAoQDg0OEhEQExgoGhgWFhgxIyUdKDozPTw5Mzg3QEhcTkBEV0U3OFBtUVdfYmdoZz5NcXlwZHhcZWdj_9sAQwEREhIYFRgvGhovY0I4QmNjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2Nj_8AAEQgAsAB5AwEiAAIRAQMRAf_EABoAAAMBAQEBAAAAAAAAAAAAAAADBAUGBwH_xAAuEAACAgEDAgQFAwUAAAAAAAAAAwQTIwUUM0NTJGNzgwEGFTSjFkSTJTVRVbP_xAAWAQEBAQAAAAAAAAAAAAAAAAAAAwT_xAAWEQEBAQAAAAAAAAAAAAAAAAAAAxP_2gAMAwEAAhEDEQA_AOXAAJJGgAAABbUKtAaBLulH3er_AMAUgS7oLQKgAAAAAAAAAUNFDQAU0aSygFW2tFNACqoBQVAA31WhaHqhb5QDVNG9IltBvKSF6m2gKilQSKAAAAAAAVKariCS2pRLFVa0BsWA2U06OL8uKGwFVGyoNWTGb8uK6RB-nJR2Q0K5OSV8r4srTLlaM2K2pp6CSz1WqqBk4OVpbYuXpCukb09VraldLlMuerayvKaEkHEVErVFSuIJAAAJAAACCVyl-lqIJXKakAKydHFUaiiCKXqDUaNACQLaiCps_wApRfUNqKiDYKUqpSjnNUgVWnZGXqkW1QHBtxAobP8AumiovKGU0AAJAAACWVyl8VtRBK5RoVk6iLPVUXqnq7pySmqG1WqtUFdHZKlDbcpxsBrWtUo62q2KFVVo205eU2UrFaKiz225ZQHWkrcpKprW9X2irL1QPPtUxSmqFReIq17-6SvVFKxKDLUAABIAKGgKaovVFtUKV6VpqRVVNqCsksWB5RsqqVFqqG1CpXEGplqyz1KU2o6ipqov37fdUcvoyvH2nZcqqgINqpsXzW5SD6Wq3q-kbMBVUVShoGXFgVNxcRe23lUrKNqFNbU1VQHn09Tfqjbe6NGz8rbaiUMtQAoAkUNFDQGqL4sqppANUFZN7dNxYrSWe1o2LxCpUW0NRugtVbUdGclFgSt1iOo2rWqVlygFtTWjWylK5W1eqNqxBUSCrbeLKDVctvK0qIJ89UDK1uIqOS1ltuqN_iIBsqUpspre60UGAoAABQ0UADRooAN6K3wo1Uq0y4De6X1KaGps6XVaX905eLFVbbumqNlVTf3VvuhVqDeUlUqriaNtJAacl82yvFKV2jo5UqpTWt6RwcqVupTZTeqVSqUKACrKA90BQAAAFTQa0U1tQSsWICqLxGzAymDA4i9TaiSrrYsBVQ1WlqV1SCBPVVlNRUpXdCptQNDddoFKJDL17FpbWtOIby4jsvmOUprdr2srTl21N5alFUqoAKthitU1VQpqmqKpFAABJfKitUrEogqL_dt7pK1Vv2vSCqVXKoqaprWi4vw-Pw-OP4ZWcZuxYqm2ta3iUBlwDZ2BAqA2LFU3unZRVeFUSVYMXS2tNmLoylF6lVDbQqFKUog1TVFQFeaKn6ztcSuU5eVKbPbbaBqQLVaXKn9VuIy9ZUpUVXSaXym1QIsXtK_KY2vKqlKi9pQSKUq3FaKbuoGJtqhrVVKNTVIDVcSsX_IqkxlKU1WUq2vmilNUpuVXEX7qL_q_ygKbF2EVTeW3lJWxWxek06iVFtlKge60xpTW7prcoGNlVlN5U9X0tSlN8U3lFaxGUnS4vd5SVsXaqiylcTQNlWWBteqo6iB9qo4PS57d_l_Kd4rixEmqQaZc-VixGo3iOcnyuq0kMuUEBX9XbK_ajVW9VWVrcQ1srwFSv4mlUjVVStUytxKyt9UwZTbdUa3zTUbFlaXo2XqmDFU1rcRVJfKardKy4jeVKVKVi5fNOSlKaqVUdGrS27VTQMufaqeFoawqVUprVGWSH__Z2BhYW6RoZGlnZXN0SUQIZnJhbmRvbVC0gDHM3xUFKaiFRu1DAnUXcWVsZW1lbnRJZGVudGlmaWVyamJpcnRoX2RhdGVsZWxlbWVudFZhbHVl2QPsajE5OTAtMDEtMDHYGFhTpGhkaWdlc3RJRAdmcmFuZG9tUNPRb_Jle7E5D-hepAv3TxVxZWxlbWVudElkZW50aWZpZXJqZ2l2ZW5fbmFtZWxlbGVtZW50VmFsdWVlQWxpY2XYGFhbpGhkaWdlc3RJRAFmcmFuZG9tUPKBXZijF1d3_R04NtJz7C1xZWxlbWVudElkZW50aWZpZXJqaXNzdWVfZGF0ZWxlbGVtZW50VmFsdWXZA-xqMjAyMC0wMS0wMdgYWFykaGRpZ2VzdElEAGZyYW5kb21QgHykf2kk9Y9_jhM0BAAitHFlbGVtZW50SWRlbnRpZmllcmtleHBpcnlfZGF0ZWxlbGVtZW50VmFsdWXZA-xqMjAyNS0wMS0wMdgYWFSkaGRpZ2VzdElECWZyYW5kb21QulAkqm6fqkRXlxcbNvrUc3FlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVlU21pdGjYGFhbpGhkaWdlc3RJRARmcmFuZG9tUOTooDeEwCnlGLbbzY-ver5xZWxlbWVudElkZW50aWZpZXJvZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZWhBQkNEMTIzNNgYWFWkaGRpZ2VzdElECmZyYW5kb21Q_ctRuMUlAkselcS8sFjbJHFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYlVT2BhYW6RoZGlnZXN0SUQGZnJhbmRvbVC_I_4SIn8VRu_qWxcclHpNcWVsZW1lbnRJZGVudGlmaWVycWlzc3VpbmdfYXV0aG9yaXR5bGVsZW1lbnRWYWx1ZWZOWSxVU0HYGFjvpGhkaWdlc3RJRAJmcmFuZG9tUFoPu1Ae76m2ftDBo8H1DU9xZWxlbWVudElkZW50aWZpZXJyZHJpdmluZ19wcml2aWxlZ2VzbGVsZW1lbnRWYWx1ZYKjamlzc3VlX2RhdGXZA-xqMjAyMC0wMS0wMWtleHBpcnlfZGF0ZdkD7GoyMDI1LTAxLTAxdXZlaGljbGVfY2F0ZWdvcnlfY29kZWFCo2ppc3N1ZV9kYXRl2QPsajIwMjAtMDEtMDFrZXhwaXJ5X2RhdGXZA-xqMjAyNS0wMS0wMXV2ZWhpY2xlX2NhdGVnb3J5X2NvZGViQkXYGFhdpGhkaWdlc3RJRANmcmFuZG9tUADrjtIGo37dMzctfKHT9J1xZWxlbWVudElkZW50aWZpZXJ2dW5fZGlzdGluZ3Vpc2hpbmdfc2lnbmxlbGVtZW50VmFsdWVjVVNB\"\n" +
      "}").let { Json.parseToJsonElement(it).jsonObject }

  @Test
  fun testParseMdocVPTokenExample() {
    // ### Parse vp_token response
    val tokenResponse = TokenResponse.fromJSON(mdoc_auth_response)
    assertNotNull(tokenResponse.presentationSubmission)
    assertEquals(expected = VCFormat.mso_mdoc, actual = tokenResponse.presentationSubmission.descriptorMap.firstOrNull()?.format)
    assertNotNull(tokenResponse.vpToken)

    // ### Parse mdoc device response
    val deviceResponse = DeviceResponse.fromCBORBase64URL(tokenResponse.vpToken.jsonPrimitive.content)
    assertEquals(1, deviceResponse.documents.size)
  }

  fun writeKeyPairAndCert(name: String, keyPair: KeyPair, cert: X509Certificate) {
    FileWriter("$name-priv.pem").also {
      it.write(
        "-----BEGIN PRIVATE KEY-----${System.lineSeparator()}" +
            java.util.Base64.getEncoder().encodeToString(keyPair.private.encoded) +
                "${System.lineSeparator()}-----END PRIVATE KEY-----${System.lineSeparator()}"
      )
      it.flush()
      it.close()
    }
    FileWriter("$name-pub.pem").also {
      it.write(
        "-----BEGIN PUBLIC KEY-----${System.lineSeparator()}" +
          java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded) +
                "${System.lineSeparator()}-----END PUBLIC KEY-----${System.lineSeparator()}"
      )
      it.flush()
      it.close()
    }
    FileWriter("$name-cert.pem").also {
      it.write(
        "-----BEGIN CERTIFICATE-----${System.lineSeparator()}" +
            java.util.Base64.getEncoder().encodeToString(cert.encoded) +
                "${System.lineSeparator()}-----END CERTIFICATE-----${System.lineSeparator()}"
      )
      it.flush()
      it.close()
    }
  }

  @BeforeAll
  fun beforeAll() {
    Security.addProvider(BouncyCastleProvider())
  }

  fun createIssuerKeyAndCertificates() {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    // create key pair for test CA
    val rootCaKeyPair = kpg.genKeyPair()
    val issuerKeyPair = kpg.genKeyPair()
    // create CA certificate
    val rootCaCertificate = X509v3CertificateBuilder(
      X500Name("CN=MDOC ROOT CSP"), BigInteger.valueOf(SecureRandom().nextLong()),
      Date(), Date(System.currentTimeMillis() + 24L * 3600 * 1000), X500Name("CN=MDOC ROOT CA"),
      SubjectPublicKeyInfo.getInstance(rootCaKeyPair.public.encoded)
    ) .addExtension(Extension.basicConstraints, true, BasicConstraints(false)) // TODO: Should be CA! Should not pass validation when false!
      .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)) // Key usage not validated.
      .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(rootCaKeyPair.private)).let {
        JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
      }
    // create issuer certificate
    val issuerCertificate = X509v3CertificateBuilder(X500Name("CN=MDOC ROOT CA"), BigInteger.valueOf(SecureRandom().nextLong()),
      Date(), Date(System.currentTimeMillis() + 365L * 24L * 3600 * 1000), X500Name("CN=MDOC Test Issuer"),
      SubjectPublicKeyInfo.getInstance(issuerKeyPair.public.encoded)
    ) .addExtension(Extension.basicConstraints, true, BasicConstraints(false))
      .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
      .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(rootCaKeyPair.private)).let {
        JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
      }

    writeKeyPairAndCert("root-ca", rootCaKeyPair, rootCaCertificate)
    writeKeyPairAndCert("issuer", issuerKeyPair, issuerCertificate)
  }

  fun readKeySpec(fileName: String): ByteArray {
    val key = String(File(fileName).readBytes(), Charset.defaultCharset())

    val publicKeyPEM = key
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace(System.lineSeparator().toRegex(), "")
      .replace("-----END PUBLIC KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")

    return Base64.from(publicKeyPEM).decode()
  }

  fun loadOrCreateIssuerKeys(): COSECryptoProviderKeyInfo {
    if(arrayOf("issuer", "root-ca").any {
      !File("$it-priv.pem").exists() || !File("$it-pub.pem").exists() || !File("$it-cert.pem").exists()
    }) {
      createIssuerKeyAndCertificates()
    }
    val factory = CertificateFactory.getInstance("X.509")
    val rootCaCert = (factory.generateCertificate(FileInputStream("root-ca-cert.pem"))) as X509Certificate
    val issuerCert = (factory.generateCertificate(FileInputStream("issuer-cert.pem"))) as X509Certificate
    val issuerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(readKeySpec("issuer-pub.pem")))
    val issuerPriv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(readKeySpec("issuer-priv.pem")))
    return COSECryptoProviderKeyInfo(ISSUER_KEY_ID, AlgorithmID.ECDSA_256, issuerPub, issuerPriv, listOf(issuerCert), listOf(rootCaCert))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun testLSPPotentialTrack3() {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    val deviceKeyPair = kpg.genKeyPair()
    val issuerProviderKeyInfo = loadOrCreateIssuerKeys()
    // 1) Create self-issued mDL credential (Issuer)
    // build mdoc of type mDL and sign using issuer key with holder binding to device key
    val lspPotentialCryptoProvider = SimpleCOSECryptoProvider(listOf(issuerProviderKeyInfo))
    val mdoc = MDocBuilder(MDocTypes.ISO_MDL)
      .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDataElement())
      .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDataElement())
      .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
      .sign(
        ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365*24, DateTimeUnit.HOUR)),
        DeviceKeyInfo(DataElement.fromCBOR(OneKey(deviceKeyPair.public, null).AsCBOR().EncodeToBytes())),
        lspPotentialCryptoProvider, ISSUER_KEY_ID
      )
    println("SIGNED MDOC (mDL):")
    println(Cbor.encodeToHexString(mdoc))

    // 2) Save mDL to wallet (Wallet)
    // nothing to do in this test case

    // 3) Create OID4VP presentation request (Verifier)
    val ephemeralReaderKey = runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
    val presReq = OpenID4VP.createPresentationRequest(
      PresentationDefinitionParameter.fromPresentationDefinition(
        PresentationDefinition(
          inputDescriptors = listOf(
            InputDescriptor(
              id = mdoc.docType.value,
              format = mapOf(VCFormat.mso_mdoc to VCFormatDefinition(setOf("EdDSA", "ES256"))),
              constraints = InputDescriptorConstraints(
                limitDisclosure = DisclosureLimitation.required,
                fields = listOf(
                  InputDescriptorField(
                    path = listOf("$['org.iso.18013.5.1']['family_name']"),
                    intentToRetain = false
                  ),
                  InputDescriptorField(
                    path = listOf("$['org.iso.18013.5.1']['given_name']"),
                    intentToRetain = false
                  ),
                  InputDescriptorField(
                    path = listOf("$['org.iso.18013.5.1']['birth_date']"),
                    intentToRetain = false
                  )
                )
              )
            )
          )
        )
      ), responseMode = ResponseMode.direct_post_jwt, responseTypes = setOf(ResponseType.VpToken), redirectOrResponseUri = "http://blank",
      nonce = randomUUIDString(), state = "test", clientId = "walt.id", clientIdScheme = ClientIdScheme.X509SanDns,
      clientMetadataParameter = ClientMetadataParameter.fromClientMetadata(
        OpenIDClientMetadata(listOf("http://localhost"),
          jwks = buildJsonObject {
//            The mdoc reader shall set the use JWK parameter (public key use) to the static JSON String value enc
//                and set the alg JWK parameter to the static JSON String value ECDH-ES to indicate which JWK in the
//            jwks Authorization Request parameter can be used for key agreement to encrypt the response (see [7]). [ISO-18013-7_240312]
            put("keys", JsonArray(listOf(runBlocking { ephemeralReaderKey.getPublicKey().exportJWKObject().let {
              JsonObject(it + ("use" to JsonPrimitive("enc")) + ("alg" to JsonPrimitive("ECDH-ES")))
            } })))
          },
          authorizationEncryptedResponseAlg = "ECDH-ES", authorizationEncryptedResponseEnc = "A256GCM")
      )
    )
    val authUrl = OpenID4VP.getAuthorizationUrl(presReq, "mdoc-openid4vp://") // ISO-18013-7 page 20 (B.3.1.3.1)
    println(authUrl)

    // 4) Parse presentation request (Wallet)
    val parsedPresReq = runBlocking { OpenID4VP.parsePresentationRequestFromUrl(authUrl) }
    assertEquals(presReq, parsedPresReq)
    assertEquals(parsedPresReq.presentationDefinition?.inputDescriptors?.get(0)?.format?.keys?.first(), VCFormat.mso_mdoc)
    val parsedReaderKey = parsedPresReq.clientMetadata?.jwks?.get("keys")?.jsonArray?.first {
      it.jsonObject.containsKey("use") && it.jsonObject.containsKey("alg") &&
          it.jsonObject["use"]!!.jsonPrimitive.content == "enc" &&
          it.jsonObject["alg"]!!.jsonPrimitive.content == parsedPresReq.clientMetadata.authorizationEncryptedResponseAlg
    } ?: throw Exception("No ephemeral reader key found")

    // 5) Create OID4VP presentation response (Wallet)
    val mdocNonce = randomUUIDString()
    val presentedMdoc = mdoc.presentWithDeviceSignature(MDocRequestBuilder(mdoc.docType.value).also {
      parsedPresReq.presentationDefinition!!.inputDescriptors.forEach { inputDescriptor ->
        inputDescriptor.constraints!!.fields!!.forEach { field ->
          field.addToMdocRequest(it)
        }
      }
    }.build(), DeviceAuthentication(sessionTranscript = ListElement(listOf(
      NullElement(), NullElement(),
      OpenID4VP.generateMDocOID4VPHandover(parsedPresReq, mdocNonce)
    )), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))),
      SimpleCOSECryptoProvider(listOf(
        COSECryptoProviderKeyInfo(DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, deviceKeyPair.private))), DEVICE_KEY_ID)

    val deviceResponse = DeviceResponse(listOf(presentedMdoc))

    val oid4vpResponse = OpenID4VP.generatePresentationResponse(PresentationResult(
      presentations = listOf(JsonPrimitive(deviceResponse.toCBORBase64URL())),
      presentationSubmission = PresentationSubmission(
        "response_1", "request_1",
        listOf(DescriptorMapping(mdoc.docType.value, VCFormat.mso_mdoc, "$"))
      )
    ))
    assertNotNull(oid4vpResponse.vpToken)
    // post as form parameters in direct_post.jwt mode (Wallet)
    val ephemeralWalletKey = runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
    val encKey = parsedPresReq.clientMetadata.jwks["keys"]?.jsonArray?.first {
      jwk -> JWK.parse(jwk.toString()).keyUse?.equals(KeyUse.ENCRYPTION) ?: false }?.jsonObject ?: throw Exception("No ephemeral reader key found")

    val formParams = oid4vpResponse.toDirectPostJWTParameters(encKey,
      alg = parsedPresReq.clientMetadata.authorizationEncryptedResponseAlg ?: "ECDH-ES",
      enc = parsedPresReq.clientMetadata.authorizationEncryptedResponseEnc ?: "A256GCM",
      mapOf(
        "epk" to runBlocking{ ephemeralWalletKey.getPublicKey().exportJWKObject() },
        "apu" to JsonPrimitive(Base64URL.encode(mdocNonce).toString()),
        "apv" to JsonPrimitive(Base64URL.encode(parsedPresReq.nonce!!).toString())
      )
    )

    // 6) Parse presentation response (Verifier)
    // load from form parameters
    assertTrue(TokenResponse.isDirectPostJWT(formParams))
    val parsedResponse = TokenResponse.fromDirectPostJWT(formParams, runBlocking{ ephemeralReaderKey.exportJWKObject() })
    assertEquals(expected = oid4vpResponse.vpToken, actual = parsedResponse.vpToken)
    assertNotNull(parsedResponse.presentationSubmission)
    assertNotNull(parsedResponse.jwsParts)

    // 7) Verify presentation response (Verifier)
    val mdocHandoverRestored = OpenID4VP.generateMDocOID4VPHandover(presReq, Base64URL.from(parsedResponse.jwsParts.header["apu"]!!.jsonPrimitive.content).decodeToString())
    val parsedDeviceResponse = DeviceResponse.fromCBORBase64URL(parsedResponse.vpToken!!.jsonPrimitive.content)
    assertEquals(1, parsedDeviceResponse.documents.size)
    val parsedMdoc = parsedDeviceResponse.documents[0]
    val verified = parsedMdoc.verify(MDocVerificationParams(
      VerificationType.forPresentation,
      issuerKeyID = ISSUER_KEY_ID, deviceKeyID = DEVICE_KEY_ID,
      deviceAuthentication = DeviceAuthentication(ListElement(listOf(NullElement(), NullElement(), mdocHandoverRestored)),
        presReq.presentationDefinition?.inputDescriptors?.first()?.id!!, EncodedCBORElement(MapElement(mapOf())))
    ), SimpleCOSECryptoProvider(
      listOf(issuerProviderKeyInfo, COSECryptoProviderKeyInfo(DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, null))
    ))
    assertTrue(verified)
  }

  @Test
  fun testResponseTypeSerialization() {
    val testResponseType = setOf(ResponseType.VpToken, ResponseType.IdToken)
    val presReq = OpenID4VP.createPresentationRequest(
      PresentationDefinitionParameter.fromPresentationDefinitionScope("test"),
      responseTypes = testResponseType,
      clientId = "test",
      clientIdScheme = ClientIdScheme.PreRegistered,
      clientMetadataParameter = null,
      nonce = "test",
      redirectOrResponseUri = "http://test",
      state = "test"
      )
    val jsonReq = presReq.toJSON()
    assertTrue(jsonReq.keys.contains("response_type"))
    assertTrue(jsonReq["response_type"] is JsonPrimitive)
    assertEquals(ResponseType.getResponseTypeString(testResponseType), jsonReq["response_type"]!!.jsonPrimitive.content)

    val parsedPresReq = AuthorizationRequest.fromJSON(jsonReq)
    assertEquals(testResponseType, parsedPresReq.responseType)
  }
}
